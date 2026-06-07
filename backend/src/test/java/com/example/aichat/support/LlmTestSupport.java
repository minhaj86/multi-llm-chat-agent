package com.example.aichat.support;

import com.example.aichat.domain.llm.LlmChunk;
import com.example.aichat.domain.llm.LlmMessage;
import com.example.aichat.domain.llm.LlmRequest;
import com.example.aichat.infrastructure.llm.LlmAdapterSupport;
import com.example.aichat.infrastructure.llm.LlmWebClientFactory;
import com.example.aichat.infrastructure.llm.ProviderProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Helpers for adapter tests that talk to a WireMock server instead of a real LLM.
 */
public final class LlmTestSupport {

    private LlmTestSupport() {
    }

    public static LlmAdapterSupport adapterSupport() {
        return new LlmAdapterSupport(new LlmWebClientFactory(WebClient.builder()), new ObjectMapper(),
                Clock.systemUTC());
    }

    public static ProviderProperties compatible(String baseUrl, String family, String engine, String model) {
        ProviderProperties properties = new ProviderProperties();
        properties.setType("openai-compatible");
        properties.setFamily(family);
        properties.setEngine(engine);
        properties.setBaseUrl(baseUrl);
        properties.setApiKey("local");
        properties.setDefaultModel(model);
        properties.setTimeout(Duration.ofSeconds(5));
        properties.setHealthTimeout(Duration.ofSeconds(2));
        return properties;
    }

    public static LlmRequest request(String model) {
        return LlmRequest.builder(model)
                .system("You are helpful.")
                .user("Hello?")
                .timeout(Duration.ofSeconds(5))
                .metadata(LlmRequest.CORRELATION_ID, "test-correlation-id")
                .build();
    }

    public static LlmRequest request(String model, List<LlmMessage> messages) {
        return LlmRequest.builder(model).messages(messages).timeout(Duration.ofSeconds(5)).build();
    }

    /** Builds a text/event-stream body from {@code data:} payloads. */
    public static String sse(String... data) {
        StringBuilder body = new StringBuilder();
        for (String payload : data) {
            body.append("data: ").append(payload).append("\n\n");
        }
        return body.toString();
    }

    /** Builds a text/event-stream body with named events (Responses API style). */
    public static String namedSse(String... eventAndData) {
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < eventAndData.length; i += 2) {
            body.append("event: ").append(eventAndData[i]).append('\n')
                    .append("data: ").append(eventAndData[i + 1]).append("\n\n");
        }
        return body.toString();
    }

    public static String text(List<LlmChunk> chunks) {
        StringBuilder out = new StringBuilder();
        for (LlmChunk chunk : chunks) {
            if (chunk instanceof LlmChunk.TextDelta delta) {
                out.append(delta.text());
            }
        }
        return out.toString();
    }

    public static String reasoning(List<LlmChunk> chunks) {
        StringBuilder out = new StringBuilder();
        for (LlmChunk chunk : chunks) {
            if (chunk instanceof LlmChunk.ReasoningDelta delta) {
                out.append(delta.text());
            }
        }
        return out.toString();
    }

    public static LlmChunk.Completed completion(List<LlmChunk> chunks) {
        LlmChunk last = chunks.getLast();
        if (last instanceof LlmChunk.Completed completed) {
            return completed;
        }
        throw new AssertionError("Stream did not end with a Completed chunk: " + last);
    }
}
