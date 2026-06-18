package com.example.aichat.infrastructure.llm.openai;

import com.example.aichat.domain.llm.LlmProvider;
import com.example.aichat.infrastructure.llm.LlmAdapterSupport;
import com.example.aichat.infrastructure.llm.LlmProviderFactory;
import com.example.aichat.infrastructure.llm.ProviderProperties;
import org.springframework.stereotype.Component;

/**
 * Creates {@link OpenAiLlmProvider} instances for {@code type: openai}.
 */
@Component
public class OpenAiProviderFactory implements LlmProviderFactory {

    private final LlmAdapterSupport support;

    public OpenAiProviderFactory(LlmAdapterSupport support) {
        this.support = support;
    }

    @Override
    public String type() {
        return OpenAiLlmProvider.TYPE;
    }

    @Override
    public LlmProvider create(String providerId, ProviderProperties properties) {
        return new OpenAiLlmProvider(providerId, properties, support);
    }
}
