package com.example.aichat.infrastructure.llm.compatible;

import com.example.aichat.domain.llm.LlmProvider;
import com.example.aichat.infrastructure.llm.LlmAdapterSupport;
import com.example.aichat.infrastructure.llm.LlmProviderFactory;
import com.example.aichat.infrastructure.llm.ProviderProperties;
import org.springframework.stereotype.Component;

/**
 * Generic OpenAI-compatible provider ({@code type: openai-compatible} without a known family). Lets any compatible
 * server (Mistral via vLLM, DeepSeek, LM Studio, LocalAI, ...) be added purely through configuration.
 */
@Component
public class OpenAiCompatibleProviderFactory implements LlmProviderFactory {

    private final LlmAdapterSupport support;

    public OpenAiCompatibleProviderFactory(LlmAdapterSupport support) {
        this.support = support;
    }

    @Override
    public String type() {
        return OpenAiCompatibleLlmProvider.TYPE;
    }

    @Override
    public LlmProvider create(String providerId, ProviderProperties properties) {
        return new OpenAiCompatibleLlmProvider(providerId, properties, ModelFamilyProfile.GENERIC, support);
    }
}
