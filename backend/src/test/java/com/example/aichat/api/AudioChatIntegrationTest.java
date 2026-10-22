package com.example.aichat.api;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.aichat.support.AbstractIntegrationTest;
import com.example.aichat.support.SseEvent;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;

class AudioChatIntegrationTest extends AbstractIntegrationTest {

    @DynamicPropertySource
    static void audioProperties(DynamicPropertyRegistry registry) {
        registry.add("audio.transcription.enabled", () -> true);
        registry.add("audio.transcription.base-url", () -> WIREMOCK.baseUrl() + "/stt/v1");
        registry.add("audio.transcription.api-key", () -> "transcription-secret");
        registry.add("audio.transcription.model", () -> "fake-transcriber");
        registry.add("audio.transcription.max-upload-bytes", () -> 1024);
    }

    private Map<String, Object> settings(int tokens) {
        return Map.of("source", "SPEAKER", "maxOutputTokens", tokens, "captureSeconds", 20, "autoSend", false);
    }

    @Test
    void persistsAudioTypeSettingsAndKnowledgeAndHonorsTokensOnSendAndRegenerate() {
        String token = register("audio-chat-owner");
        List<SseEvent> events = streamEvents("/api/chat/stream", Map.of("message", "Transcribed question",
                "provider", "qwen", "type", "AUDIO", "audio", settings(77),
                "knowledge", Map.of("enabled", true, "collectionIds", List.of())), token);
        String id = SseEvent.first(events, "start").get("conversationId").asText();
        JsonNode conversation = getJson("/api/conversations/" + id, token).get("conversation");
        assertThat(conversation.get("type").asText()).isEqualTo("AUDIO");
        assertThat(conversation.at("/audio/source").asText()).isEqualTo("SPEAKER");
        assertThat(conversation.at("/knowledge/enabled").asBoolean()).isTrue();
        assertThat(SseEvent.names(events)).contains("citations", "done");
        WIREMOCK.verify(postRequestedFor(urlEqualTo("/qwen/v1/chat/completions"))
                .withRequestBody(matchingJsonPath("$.max_tokens", equalTo("77")))
                .withRequestBody(matchingJsonPath("$.stream", equalTo("true"))));

        assertThat(call(HttpMethod.PUT, "/api/conversations/" + id + "/audio", settings(99), token)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        streamEvents("/api/chat/regenerate", Map.of("conversationId", id, "provider", "llama"), token);
        WIREMOCK.verify(postRequestedFor(urlEqualTo("/llama/v1/chat/completions"))
                .withRequestBody(matchingJsonPath("$.max_tokens", equalTo("99")))
                .withRequestBody(matchingJsonPath("$.stream", equalTo("true"))));
        call(HttpMethod.PATCH, "/api/conversations/" + id, Map.of("title", "Meeting"), token);
        assertThat(getJson("/api/conversations/" + id, token).at("/conversation/audio/maxOutputTokens").asInt())
                .isEqualTo(99);
        assertThat(getJson("/api/conversations", token).get(0).get("type").asText()).isEqualTo("AUDIO");

        String other = register("audio-chat-other");
        assertThat(call(HttpMethod.PUT, "/api/conversations/" + id + "/audio", settings(42), other)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(HttpMethod.PUT, "/api/conversations/" + id + "/audio", settings(0), token)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void existingTextChatsKeepTheirTypeAndRejectAudioSettings() {
        String token = register("audio-text-owner");
        String id = SseEvent.first(streamEvents("/api/chat/stream", Map.of("message", "Hello", "provider", "qwen"),
                token), "start").get("conversationId").asText();
        assertThat(getJson("/api/conversations/" + id, token).at("/conversation/type").asText()).isEqualTo("TEXT");
        assertThat(call(HttpMethod.PUT, "/api/conversations/" + id + "/audio", settings(10), token)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(HttpMethod.POST, "/api/chat", Map.of("conversationId", id, "message", "Hello",
                "provider", "qwen", "type", "AUDIO"), token).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void authenticatesTranscriptionAndNeverExposesConfigurationSecrets() {
        WIREMOCK.stubFor(post("/stt/v1/audio/transcriptions").willReturn(okJson("{\"text\":\"Meeting prompt\"}")));
        String token = register("audio-transcribe-user");
        JsonNode config = getJson("/api/audio/config", token);
        assertThat(config.get("enabled").asBoolean()).isTrue();
        assertThat(config.toString()).doesNotContain("secret", "baseUrl", "apiKey");
        assertThat(call(HttpMethod.GET, "/api/audio/config", null, null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(upload(token, new byte[]{1, 2, 3}, "audio/webm").getBody()).contains("Meeting prompt");
        assertThat(upload(null, new byte[]{1}, "audio/webm").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(upload(token, new byte[1025], "audio/webm").getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(upload(token, new byte[]{1}, "video/webm").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(upload(token, new byte[0], "audio/webm").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        WIREMOCK.verify(1, postRequestedFor(urlEqualTo("/stt/v1/audio/transcriptions")));
    }

    private org.springframework.http.ResponseEntity<String> upload(String token, byte[] data, String contentType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        if (token != null) headers.setBearerAuth(token);
        HttpHeaders part = new HttpHeaders();
        part.setContentType(MediaType.parseMediaType(contentType));
        ByteArrayResource file = new ByteArrayResource(data) {
            @Override public String getFilename() { return "recording.webm"; }
        };
        LinkedMultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new HttpEntity<>(file, part));
        return rest.postForEntity("/api/audio/transcriptions", new HttpEntity<>(body, headers), String.class);
    }
}
