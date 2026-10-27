package com.example.aichat.api;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.notContaining;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.aichat.support.AbstractIntegrationTest;
import com.example.aichat.support.LlmTestSupport;
import com.example.aichat.support.SseEvent;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ChatFlowIntegrationTest extends AbstractIntegrationTest {

    private static Map<String, Object> chat(String message, String provider, String conversationId) {
        Map<String, Object> body = new HashMap<>();
        body.put("message", message);
        body.put("provider", provider);
        if (conversationId != null) {
            body.put("conversationId", conversationId);
        }
        return body;
    }

    @Test
    void streamsTheAnswerAndPersistsTheExchangeWithProviderAndMetrics() {
        String token = register("stream-user");

        List<SseEvent> events = streamEvents("/api/chat/stream", chat("Hi there", "qwen", null), token);

        assertThat(SseEvent.names(events)).startsWith("start").contains("reasoning", "delta", "done").endsWith("title");
        assertThat(SseEvent.names(events).indexOf("done")).isGreaterThan(SseEvent.names(events).lastIndexOf("delta"));
        JsonNode start = SseEvent.first(events, "start");
        assertThat(start.get("provider").asText()).isEqualTo("qwen");
        assertThat(start.get("providerName").asText()).isEqualTo("Qwen Local");
        assertThat(start.get("newConversation").asBoolean()).isTrue();
        assertThat(SseEvent.text(events, "delta")).isEqualTo("Hello from Qwen");
        assertThat(SseEvent.text(events, "reasoning")).isEqualTo("Thinking");

        JsonNode done = SseEvent.first(events, "done");
        assertThat(done.get("status").asText()).isEqualTo("COMPLETE");
        assertThat(done.get("inputTokens").asInt()).isEqualTo(12);
        assertThat(done.get("outputTokens").asInt()).isEqualTo(5);
        assertThat(done.get("local").asBoolean()).isTrue();
        assertThat(done.get("estimatedCost").decimalValue()).isZero();
        assertThat(SseEvent.first(events, "title").get("title").asText()).isEqualTo("Generated Title");

        String conversationId = start.get("conversationId").asText();
        JsonNode detail = getJson("/api/conversations/" + conversationId, token);
        assertThat(detail.at("/conversation/title").asText()).isEqualTo("Generated Title");
        assertThat(detail.at("/conversation/lastProvider").asText()).isEqualTo("qwen");
        JsonNode messages = detail.get("messages");
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).get("role").asText()).isEqualTo("user");
        assertThat(messages.get(0).get("content").asText()).isEqualTo("Hi there");
        JsonNode answer = messages.get(1);
        assertThat(answer.get("role").asText()).isEqualTo("assistant");
        assertThat(answer.get("provider").asText()).isEqualTo("qwen");
        assertThat(answer.get("model").asText()).isEqualTo("qwen3:8b");
        assertThat(answer.get("status").asText()).isEqualTo("COMPLETE");
        assertThat(answer.get("content").asText()).isEqualTo("Hello from Qwen");
        assertThat(answer.get("reasoning").asText()).isEqualTo("Thinking");
        assertThat(answer.at("/metrics/outputTokens").asInt()).isEqualTo(5);

        Integer executions = jdbc.queryForObject("select count(*) from llm_executions where conversation_id = ? "
                + "and purpose = 'CHAT' and status = 'SUCCESS' and provider_kind = 'LOCAL'", Integer.class,
                UUID.fromString(conversationId));
        assertThat(executions).isEqualTo(1);
    }

    @Test
    void switchingProvidersMidConversationKeepsTheHistoryAndRecordsEachProvider() {
        String token = register("switch-user");
        String conversationId = SseEvent.first(streamEvents("/api/chat/stream", chat("First question", "qwen", null),
                token), "start").get("conversationId").asText();

        List<SseEvent> second = streamEvents("/api/chat/stream", chat("Follow-up", "llama", conversationId), token);

        assertThat(SseEvent.first(second, "done").get("provider").asText()).isEqualTo("llama");
        assertThat(SseEvent.names(second)).doesNotContain("title");
        JsonNode messages = getJson("/api/conversations/" + conversationId, token).get("messages");
        List<String> providers = new ArrayList<>();
        messages.forEach(m -> {
            if (m.get("role").asText().equals("assistant")) {
                providers.add(m.get("provider").asText());
            }
        });
        assertThat(providers).containsExactly("qwen", "llama");
        WIREMOCK.verify(postRequestedFor(urlEqualTo("/llama/v1/chat/completions"))
                .withRequestBody(matchingJsonPath("$.stream", equalTo("true")))
                .withRequestBody(containing("First question"))
                .withRequestBody(containing("Hello from Qwen"))
                .withRequestBody(containing("Follow-up")));
        assertThat(getJson("/api/conversations/" + conversationId, token).at("/conversation/lastProvider").asText())
                .isEqualTo("llama");
    }

    @Test
    void unavailableLocalProviderFailsGracefullyAndSuggestsAlternatives() {
        healthMonitor.checkAll();
        String token = register("offline-user");

        List<SseEvent> events = streamEvents("/api/chat/stream", chat("Anyone there?", "offline", null), token);

        JsonNode error = SseEvent.first(events, "error");
        assertThat(error.get("code").asText()).isEqualTo("PROVIDER_UNAVAILABLE");
        assertThat(error.get("message").asText()).isEqualTo("Offline Local is currently unavailable.");
        assertThat(error.get("retryable").asBoolean()).isTrue();
        List<String> alternatives = new ArrayList<>();
        error.get("alternatives").forEach(a -> alternatives.add(a.get("id").asText()));
        assertThat(alternatives).containsExactlyInAnyOrder("openai", "qwen", "llama");

        String conversationId = SseEvent.first(events, "start").get("conversationId").asText();
        JsonNode failed = getJson("/api/conversations/" + conversationId, token).get("messages").get(1);
        assertThat(failed.get("status").asText()).isEqualTo("ERROR");
        assertThat(failed.get("errorCode").asText()).isEqualTo("PROVIDER_UNAVAILABLE");
        assertThat(healthMonitor.current("offline").isAvailable()).isFalse();

        // The rest of the application keeps working: retry the same question with another provider.
        List<SseEvent> retry = streamEvents("/api/chat/regenerate",
                Map.of("conversationId", conversationId, "provider", "llama"), token);
        assertThat(SseEvent.first(retry, "done").get("status").asText()).isEqualTo("COMPLETE");
        JsonNode messages = getJson("/api/conversations/" + conversationId, token).get("messages");
        assertThat(messages).hasSize(2);
        assertThat(messages.get(1).get("provider").asText()).isEqualTo("llama");
        assertThat(messages.get(1).get("status").asText()).isEqualTo("COMPLETE");
    }

    @Test
    void regenerateReplacesTheLastAnswer() {
        String token = register("regen-user");
        String conversationId = SseEvent.first(streamEvents("/api/chat/stream", chat("Explain DI", "qwen", null),
                token), "start").get("conversationId").asText();
        String firstAnswerId = getJson("/api/conversations/" + conversationId, token).get("messages").get(1)
                .get("id").asText();

        List<SseEvent> events = streamEvents("/api/chat/regenerate",
                Map.of("conversationId", conversationId, "provider", "llama"), token);

        assertThat(SseEvent.first(events, "start").get("regenerated").asBoolean()).isTrue();
        JsonNode messages = getJson("/api/conversations/" + conversationId, token).get("messages");
        assertThat(messages).hasSize(2);
        assertThat(messages.get(1).get("id").asText()).isNotEqualTo(firstAnswerId);
        assertThat(messages.get(1).get("content").asText()).isEqualTo("Hello from Llama");
    }

    @Test
    void stoppingAGenerationKeepsThePartialAnswer() throws Exception {
        List<String> events = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            events.add("{\"choices\":[{\"delta\":{\"content\":\"token" + i + " \"}}]}");
        }
        events.add("[DONE]");
        WIREMOCK.stubFor(post(urlEqualTo("/llama/v1/chat/completions")).atPriority(1)
                .withRequestBody(matchingJsonPath("$.stream", equalTo("true")))
                .willReturn(aResponse().withHeader("Content-Type", "text/event-stream")
                        .withBody(LlmTestSupport.sse(events.toArray(String[]::new)))
                        .withChunkedDribbleDelay(40, 8000)));
        String token = register("stop-user");

        CompletableFuture<List<SseEvent>> stream = CompletableFuture.supplyAsync(() ->
                streamEvents("/api/chat/stream", chat("Write a long story", "llama", null), token));
        String assistantId = await().atMost(Duration.ofSeconds(10)).until(() -> jdbc.queryForList(
                "select m.id from messages m join conversations c on c.id = m.conversation_id join users u on "
                        + "u.id = c.user_id where u.username = 'stop-user' and m.role = 'ASSISTANT' and m.status = 'STREAMING'",
                UUID.class), ids -> !ids.isEmpty()).getFirst().toString();
        Thread.sleep(1500);

        ResponseEntity<String> cancel = call(HttpMethod.POST, "/api/chat/generations/" + assistantId + "/cancel", null,
                token);

        assertThat(cancel.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        List<SseEvent> result = stream.get(15, TimeUnit.SECONDS);
        assertThat(SseEvent.first(result, "done").get("status").asText()).isEqualTo("CANCELLED");
        Map<String, Object> stored = jdbc.queryForMap("select status, content from messages where id = ?",
                UUID.fromString(assistantId));
        assertThat(stored.get("status")).isEqualTo("CANCELLED");
        assertThat((String) stored.get("content")).startsWith("token0").doesNotContain("token39");
    }

    @Test
    void cancellingSomeoneElsesGenerationIsNotPossible() {
        String token = register("cancel-intruder");

        ResponseEntity<String> response = call(HttpMethod.POST, "/api/chat/generations/" + UUID.randomUUID() + "/cancel",
                null, token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void nonStreamingEndpointReturnsTheCompleteAnswer() {
        String token = register("blocking-user");

        ResponseEntity<String> response = call(HttpMethod.POST, "/api/chat", chat("Hello?", "llama", null), token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = read(response.getBody());
        assertThat(body.get("content").asText()).isEqualTo("Hello from Llama");
        assertThat(body.get("provider").asText()).isEqualTo("llama");
        assertThat(body.get("status").asText()).isEqualTo("COMPLETE");
    }

    @Test
    void openAiAnswersThroughTheResponsesApiWithCostEstimate() {
        WIREMOCK.stubFor(post(urlEqualTo("/openai/v1/responses"))
                .withRequestBody(matchingJsonPath("$.stream", equalTo("true")))
                .willReturn(aResponse().withHeader("Content-Type", "text/event-stream").withBody(LlmTestSupport.namedSse(
                        "response.output_text.delta", "{\"type\":\"response.output_text.delta\",\"delta\":\"Cloud answer\"}",
                        "response.completed", "{\"type\":\"response.completed\",\"response\":{\"id\":\"r1\",\"status\":\"completed\","
                                + "\"usage\":{\"input_tokens\":1000,\"output_tokens\":500}}}"))));
        WIREMOCK.stubFor(post(urlEqualTo("/openai/v1/responses"))
                .withRequestBody(matchingJsonPath("$.stream", equalTo("false")))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(
                        "{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"Cloud Title\"}]}]}")));
        String token = register("cloud-user");

        List<SseEvent> events = streamEvents("/api/chat/stream", chat("Hi", "openai", null), token);

        assertThat(SseEvent.text(events, "delta")).isEqualTo("Cloud answer");
        JsonNode done = SseEvent.first(events, "done");
        assertThat(done.get("local").asBoolean()).isFalse();
        // gpt-5-mini: 1000 * 0.25 / 1M + 500 * 2.00 / 1M = 0.00125
        assertThat(done.get("estimatedCost").decimalValue()).isEqualByComparingTo("0.00125");
        WIREMOCK.verify(postRequestedFor(urlEqualTo("/openai/v1/responses"))
                .withHeader("Authorization", equalTo("Bearer sk-test-integration-0123456789")));
    }

    @Test
    void gpt6ChatAlsoGeneratesAndPersistsATitle() {
        WIREMOCK.stubFor(post(urlEqualTo("/openai/v1/responses"))
                .withRequestBody(matchingJsonPath("$.stream", equalTo("true")))
                .willReturn(aResponse().withHeader("Content-Type", "text/event-stream").withBody(LlmTestSupport.namedSse(
                        "response.output_text.delta", "{\"type\":\"response.output_text.delta\",\"delta\":\"Cloud answer\"}",
                        "response.completed", "{\"type\":\"response.completed\",\"response\":{\"status\":\"completed\"}}"))));
        WIREMOCK.stubFor(post(urlEqualTo("/openai/v1/responses"))
                .withRequestBody(matchingJsonPath("$.stream", equalTo("false")))
                .willReturn(okJson("""
                        {"status":"completed","output":[{"type":"message",
                          "content":[{"type":"output_text","text":"Cloud Title"}]}]}""")));
        // GPT-6 defaults to reasoning, which rejects the temperature requested by title generation.
        WIREMOCK.stubFor(post(urlEqualTo("/openai/v1/responses")).atPriority(1)
                .withRequestBody(matchingJsonPath("$.temperature"))
                .willReturn(aResponse().withStatus(400).withHeader("Content-Type", "application/json").withBody("""
                        {"error":{"type":"invalid_request_error","param":"temperature",
                          "message":"Unsupported parameter: temperature"}}""")));
        String token = register("gpt6-title-user");
        Map<String, Object> request = chat("Hi", "openai", null);
        request.put("model", "gpt-6-luna");

        List<SseEvent> events = streamEvents("/api/chat/stream", request, token);

        assertThat(SseEvent.text(events, "delta")).isEqualTo("Cloud answer");
        assertThat(SseEvent.names(events)).contains("done", "title").doesNotContain("error");
        String conversationId = SseEvent.first(events, "start").get("conversationId").asText();
        assertThat(getJson("/api/conversations/" + conversationId, token).at("/conversation/title").asText())
                .isEqualTo("Cloud Title");
        WIREMOCK.verify(postRequestedFor(urlEqualTo("/openai/v1/responses"))
                .withRequestBody(matchingJsonPath("$.stream", equalTo("false")))
                .withRequestBody(matchingJsonPath("$.model", equalTo("gpt-6-luna")))
                .withRequestBody(matchingJsonPath("$.reasoning.effort", equalTo("low")))
                .withRequestBody(notContaining("temperature")));
    }

    @Test
    void invalidRequestsAreRejectedBeforeStreaming() {
        String token = register("validation-user");

        ResponseEntity<String> blank = callStream("/api/chat/stream", chat(" ", "qwen", null), token);
        ResponseEntity<String> unknown = callStream("/api/chat/stream", chat("hi", "nope", null), token);

        assertThat(blank.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(read(unknown.getBody()).get("code").asText()).isEqualTo("UNKNOWN_PROVIDER");
    }
}
