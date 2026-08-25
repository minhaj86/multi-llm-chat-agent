package com.example.aichat.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.aichat.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class AuthorizationIntegrationTest extends AbstractIntegrationTest {

    private String createConversation(String token) {
        ResponseEntity<String> response = call(HttpMethod.POST, "/api/conversations", Map.of("title", "Private"), token);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return read(response.getBody()).get("id").asText();
    }

    @Test
    void protectedEndpointsRequireAToken() {
        ResponseEntity<String> response = call(HttpMethod.GET, "/api/conversations", null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(read(response.getBody()).get("code").asText()).isEqualTo("UNAUTHENTICATED");
        assertThat(call(HttpMethod.GET, "/api/providers", null, "not-a-jwt").getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void usersCanOnlyAccessTheirOwnConversations() {
        String alice = register("alice-authz");
        String bob = register("bob-authz");
        String conversationId = createConversation(alice);

        assertThat(call(HttpMethod.GET, "/api/conversations/" + conversationId, null, bob).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(HttpMethod.PATCH, "/api/conversations/" + conversationId, Map.of("title", "x"), bob)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(HttpMethod.DELETE, "/api/conversations/" + conversationId, null, bob).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        Map<String, Object> intrusion = new HashMap<>();
        intrusion.put("conversationId", conversationId);
        intrusion.put("message", "let me in");
        intrusion.put("provider", "qwen");
        assertThat(callStream("/api/chat/stream", intrusion, bob).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(getJson("/api/conversations", bob)).isEmpty();
        assertThat(getJson("/api/conversations", alice)).hasSize(1);
        assertThat(getJson("/api/conversations/" + conversationId, alice).at("/conversation/title").asText())
                .isEqualTo("Private");
    }

    @Test
    void administrationRequiresTheAdminRole() {
        String user = register("plain-user");

        assertThat(call(HttpMethod.GET, "/api/admin/users", null, user).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(HttpMethod.GET, "/api/admin/providers", null, user).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(HttpMethod.GET, "/api/admin/users", null, adminToken()).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void disablingAnAccountRevokesItsTokensImmediately() {
        String token = register("to-disable");
        String admin = adminToken();
        String userId = getJson("/api/auth/me", token).get("id").asText();

        ResponseEntity<String> disabled = call(HttpMethod.POST, "/api/admin/users/" + userId + "/disable", null, admin);

        assertThat(read(disabled.getBody()).get("status").asText()).isEqualTo("DISABLED");
        ResponseEntity<String> rejected = call(HttpMethod.GET, "/api/conversations", null, token);
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(read(rejected.getBody()).get("code").asText()).isEqualTo("ACCOUNT_DISABLED");
        ResponseEntity<String> login = call(HttpMethod.POST, "/api/auth/login",
                Map.of("login", "to-disable", "password", "Secret-pass-123"), null);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        call(HttpMethod.POST, "/api/admin/users/" + userId + "/reset-status", null, admin);
        assertThat(login("to-disable", "Secret-pass-123")).isNotBlank();
    }

    @Test
    void repeatedFailedLoginsLockTheAccountUntilReset() {
        register("brute-force-target");
        for (int i = 0; i < 5; i++) {
            assertThat(call(HttpMethod.POST, "/api/auth/login",
                    Map.of("login", "brute-force-target", "password", "wrong-" + i), null).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        ResponseEntity<String> locked = call(HttpMethod.POST, "/api/auth/login",
                Map.of("login", "brute-force-target", "password", "Secret-pass-123"), null);

        assertThat(locked.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(read(locked.getBody()).get("code").asText()).isEqualTo("ACCOUNT_LOCKED");
        String admin = adminToken();
        String id = null;
        for (JsonNode user : getJson("/api/admin/users", admin)) {
            if (user.get("username").asText().equals("brute-force-target")) {
                id = user.get("id").asText();
            }
        }
        call(HttpMethod.POST, "/api/admin/users/" + id + "/reset-status", null, admin);
        assertThat(login("brute-force-target", "Secret-pass-123")).isNotBlank();
    }

    @Test
    void registrationIsValidatedAndPasswordsAreHashed() {
        ResponseEntity<String> weak = call(HttpMethod.POST, "/api/auth/register",
                Map.of("username", "weakling", "email", "weak@example.com", "password", "short"), null);
        assertThat(weak.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        register("unique-name");
        ResponseEntity<String> duplicate = call(HttpMethod.POST, "/api/auth/register",
                Map.of("username", "Unique-Name", "email", "other@example.com", "password", "Secret-pass-123"), null);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(read(duplicate.getBody()).get("code").asText()).isEqualTo("USERNAME_TAKEN");

        String hash = jdbc.queryForObject("select password_hash from users where username = 'unique-name'", String.class);
        assertThat(hash).startsWith("$2").doesNotContain("Secret-pass-123");
    }

    @Test
    void administratorsCannotLockThemselvesOut() {
        String admin = adminToken();
        String adminId = getJson("/api/auth/me", admin).get("id").asText();

        ResponseEntity<String> response = call(HttpMethod.DELETE, "/api/admin/users/" + adminId, null, admin);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(read(response.getBody()).get("code").asText()).isEqualTo("CANNOT_DELETE_SELF");
    }

    @Test
    void renameAndDeleteConversations() {
        String token = register("organizer");
        String conversationId = createConversation(token);

        ResponseEntity<String> renamed = call(HttpMethod.PATCH, "/api/conversations/" + conversationId,
                Map.of("title", "Renamed"), token);
        assertThat(read(renamed.getBody()).get("titleSource").asText()).isEqualTo("USER");

        assertThat(call(HttpMethod.DELETE, "/api/conversations/" + conversationId, null, token).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(call(HttpMethod.GET, "/api/conversations/" + conversationId, null, token).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
