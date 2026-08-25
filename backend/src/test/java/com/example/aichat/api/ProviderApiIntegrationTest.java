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

class ProviderApiIntegrationTest extends AbstractIntegrationTest {

    private static Map<String, JsonNode> byId(JsonNode providers) {
        Map<String, JsonNode> result = new HashMap<>();
        providers.forEach(p -> result.put(p.get("id").asText(), p));
        return result;
    }

    @Test
    void listsConfiguredProvidersWithLiveAvailability() {
        healthMonitor.checkAll();
        String token = register("provider-viewer");

        Map<String, JsonNode> providers = byId(getJson("/api/providers", token));

        assertThat(providers.keySet()).containsExactlyInAnyOrder("openai", "qwen", "llama", "offline");
        assertThat(providers.get("qwen").get("name").asText()).isEqualTo("Qwen Local");
        assertThat(providers.get("qwen").get("available").asBoolean()).isTrue();
        assertThat(providers.get("qwen").get("defaultProvider").asBoolean()).isTrue();
        assertThat(providers.get("qwen").get("kind").asText()).isEqualTo("LOCAL");
        assertThat(providers.get("openai").get("available").asBoolean()).isTrue();
        assertThat(providers.get("openai").get("kind").asText()).isEqualTo("CLOUD");
        assertThat(providers.get("offline").get("available").asBoolean()).isFalse();
        assertThat(providers.get("offline").get("status").asText()).isEqualTo("DOWN");
    }

    @Test
    void modelsAndHealthComeFromTheProvider() {
        String token = register("model-viewer");

        JsonNode models = getJson("/api/providers/qwen/models", token);
        JsonNode health = getJson("/api/providers/offline/health?refresh=true", token);

        assertThat(models).extracting(m -> m.get("id").asText()).containsExactly("qwen3:32b", "qwen3:8b");
        assertThat(models).filteredOn(m -> m.get("isDefault").asBoolean())
                .extracting(m -> m.get("id").asText()).containsExactly("qwen3:8b");
        assertThat(health.get("status").asText()).isEqualTo("DOWN");
        assertThat(health.get("available").asBoolean()).isFalse();
        assertThat(getJson("/api/providers/llama/health?refresh=true", token).get("status").asText()).isEqualTo("UP");
        assertThat(call(HttpMethod.GET, "/api/providers/unknown/models", null, token).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void administratorsCanDisableAndRestoreProvidersAtRuntime() {
        String admin = adminToken();
        String user = register("runtime-user");

        try {
            ResponseEntity<String> update = call(HttpMethod.PUT, "/api/admin/providers/llama/settings",
                    Map.of("enabled", false, "timeoutSeconds", 30), admin);
            assertThat(update.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(read(update.getBody()).get("overridden").asBoolean()).isTrue();

            assertThat(byId(getJson("/api/providers", user)).get("llama").get("enabled").asBoolean()).isFalse();
            ResponseEntity<String> rejected = callStream("/api/chat/stream",
                    Map.of("message", "hi", "provider", "llama"), user);
            assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(read(rejected.getBody()).get("code").asText()).isEqualTo("PROVIDER_DISABLED");
        } finally {
            call(HttpMethod.DELETE, "/api/admin/providers/llama/settings", null, admin);
        }
        assertThat(byId(getJson("/api/providers", user)).get("llama").get("enabled").asBoolean()).isTrue();
    }

    @Test
    void credentialsAreNeverExposed() {
        String admin = adminToken();

        String adminView = call(HttpMethod.GET, "/api/admin/providers", null, admin).getBody();
        String userView = call(HttpMethod.GET, "/api/providers", null, register("curious-user")).getBody();

        assertThat(adminView).doesNotContain("sk-test-integration-0123456789").contains("sk-••••6789");
        assertThat(userView).doesNotContain("sk-test").doesNotContain("apiKey");
    }

    @Test
    void prometheusExposesLlmMetrics() {
        String token = register("metrics-user");
        streamEvents("/api/chat/stream", Map.of("message", "metrics please", "provider", "llama"), token);

        String metrics = rest.getForObject("/actuator/prometheus", String.class);

        assertThat(metrics).contains("llm_requests_total{")
                .contains("llm_request_duration_seconds")
                .contains("llm_input_tokens")
                .contains("llm_output_tokens")
                .contains("provider=\"llama\"")
                .contains("status=\"success\"");
    }

    @Test
    void usageDashboardAggregatesExecutions() {
        String token = register("usage-user");
        streamEvents("/api/chat/stream", Map.of("message", "count me", "provider", "qwen"), token);

        JsonNode usage = getJson("/api/admin/usage?days=7&tz=Europe/Berlin", adminToken());

        assertThat(usage.at("/today/requests").asLong()).isPositive();
        assertThat(usage.at("/period/localRequests").asLong()).isPositive();
        assertThat(usage.get("byProvider")).anySatisfy(p -> assertThat(p.get("provider").asText()).isEqualTo("qwen"));
        assertThat(usage.get("daily")).isNotEmpty();
    }
}
