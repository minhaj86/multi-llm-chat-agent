package com.example.aichat.application.provider;

import com.example.aichat.domain.llm.GenerationOptions;
import com.example.aichat.domain.llm.LlmMessage;
import com.example.aichat.domain.llm.LlmProvider;
import com.example.aichat.domain.llm.LlmRequest;
import com.example.aichat.domain.llm.ProviderDescriptor;
import com.example.aichat.domain.llm.ReasoningMode;
import java.util.List;

/**
 * A provider and concrete model selected for a request, together with the effective settings.
 */
public record ResolvedProvider(LlmProvider provider, String model, EffectiveProviderSettings settings) {

    public String providerId() {
        return provider.providerId();
    }

    public ProviderDescriptor descriptor() {
        return provider.descriptor();
    }

    public String displayName() {
        return provider.descriptor().displayName();
    }

    public boolean isLocal() {
        return provider.descriptor().isLocal();
    }

    public LlmRequest request(List<LlmMessage> messages, String correlationId) {
        return request(messages, correlationId, ReasoningMode.DEFAULT, null);
    }

    /**
     * Builds a provider-neutral request using the effective temperature, token limit and timeout.
     *
     * @param maxTokensOverride optional output token limit replacing the provider setting
     */
    public LlmRequest request(List<LlmMessage> messages, String correlationId, ReasoningMode reasoning,
                              Integer maxTokensOverride) {
        GenerationOptions options = new GenerationOptions(
                settings.temperature(),
                maxTokensOverride != null ? maxTokensOverride : settings.maxOutputTokens(),
                reasoning);
        return LlmRequest.builder(model)
                .messages(messages)
                .options(options)
                .timeout(settings.timeout())
                .metadata(LlmRequest.CORRELATION_ID, correlationId)
                .build();
    }
}
