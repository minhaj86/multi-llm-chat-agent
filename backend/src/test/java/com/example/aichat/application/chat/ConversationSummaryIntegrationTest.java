package com.example.aichat.application.chat;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.aichat.support.AbstractIntegrationTest;
import com.example.aichat.support.SseEvent;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * SUMMARY_PLUS_RECENT end to end: once the unsummarised history exceeds the trigger, older messages are folded into
 * a rolling summary in the background and later requests carry the summary instead of the full history.
 */
@TestPropertySource(properties = {
        "chat.context.strategy=SUMMARY_PLUS_RECENT",
        "chat.context.summary-trigger-tokens=20",
        "chat.context.summary-keep-recent-messages=2"
})
class ConversationSummaryIntegrationTest extends AbstractIntegrationTest {

    @Test
    void olderMessagesAreSummarisedAndTheSummaryIsSentInsteadOfTheFullHistory() {
        WIREMOCK.stubFor(post(urlEqualTo("/qwen/v1/chat/completions")).atPriority(1)
                .withRequestBody(containing("running summary"))
                .willReturn(okJson("{\"choices\":[{\"message\":{\"content\":\"- The user is learning about JVM tuning.\"},"
                        + "\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":80,\"completion_tokens\":12}}")));
        String token = register("summary-user");
        String conversationId = SseEvent.first(streamEvents("/api/chat/stream",
                Map.of("message", "Tell me about JVM tuning in detail please", "provider", "qwen"), token), "start")
                .get("conversationId").asText();
        streamEvents("/api/chat/stream", Map.of("conversationId", conversationId,
                "message", "What about garbage collectors and heap sizing?", "provider", "qwen"), token);

        await().atMost(Duration.ofSeconds(10)).until(() -> jdbc.queryForObject(
                "select summary from conversations where id = ?", String.class, UUID.fromString(conversationId)) != null);

        streamEvents("/api/chat/stream", Map.of("conversationId", conversationId, "message", "And ZGC?",
                "provider", "qwen"), token);

        WIREMOCK.verify(postRequestedFor(urlEqualTo("/qwen/v1/chat/completions"))
                .withRequestBody(containing("running summary"))
                .withRequestBody(matchingJsonPath("$.reasoning_effort", equalTo("none"))));
        WIREMOCK.verify(postRequestedFor(urlEqualTo("/qwen/v1/chat/completions"))
                .withRequestBody(matchingJsonPath("$.stream", equalTo("true")))
                .withRequestBody(containing("And ZGC?"))
                .withRequestBody(containing("Summary of the earlier part of this conversation"))
                .withRequestBody(containing("The user is learning about JVM tuning."))
                .withRequestBody(matchingJsonPath("$.messages[0].role", equalTo("system"))));
        Integer summaries = jdbc.queryForObject("select count(*) from llm_executions where conversation_id = ? and "
                + "purpose = 'SUMMARY'", Integer.class, UUID.fromString(conversationId));
        assertThat(summaries).isPositive();
    }
}
