package com.example.aichat.infrastructure.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.springframework.stereotype.Component;

/**
 * Shared collaborators for adapter instances created by factories.
 */
@Component
public class LlmAdapterSupport {

    private final LlmWebClientFactory webClientFactory;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public LlmAdapterSupport(LlmWebClientFactory webClientFactory, ObjectMapper objectMapper, Clock clock) {
        this.webClientFactory = webClientFactory;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public LlmWebClientFactory webClientFactory() {
        return webClientFactory;
    }

    public ObjectMapper objectMapper() {
        return objectMapper;
    }

    public Clock clock() {
        return clock;
    }
}
