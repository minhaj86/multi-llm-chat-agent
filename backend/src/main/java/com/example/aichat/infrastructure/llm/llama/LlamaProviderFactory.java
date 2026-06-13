package com.example.aichat.infrastructure.llm.llama;

import com.example.aichat.domain.llm.LlmProvider;
import com.example.aichat.infrastructure.llm.LlmAdapterSupport;
import com.example.aichat.infrastructure.llm.LlmProviderFactory;
import com.example.aichat.infrastructure.llm.ProviderProperties;
import com.example.aichat.infrastructure.llm.compatible.OpenAiCompatibleLlmProvider;
import org.springframework.stereotype.Component;

/**
 * Creates {@link LlamaLlmProvider} for {@code type: openai-compatible} + {@code family: llama}.
 */
@Component
public class LlamaProviderFactory implements LlmProviderFactory {

    private final LlmAdapterSupport support;

    public LlamaProviderFactory(LlmAdapterSupport support) {
        this.support = support;
    }

    @Override
    public String type() {
        return OpenAiCompatibleLlmProvider.TYPE;
    }

    @Override
    public String family() {
        return LlamaLlmProvider.FAMILY;
    }

    @Override
    public LlmProvider create(String providerId, ProviderProperties properties) {
        return new LlamaLlmProvider(providerId, properties, support);
    }
}
