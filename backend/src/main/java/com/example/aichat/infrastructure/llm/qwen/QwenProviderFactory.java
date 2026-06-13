package com.example.aichat.infrastructure.llm.qwen;

import com.example.aichat.domain.llm.LlmProvider;
import com.example.aichat.infrastructure.llm.LlmAdapterSupport;
import com.example.aichat.infrastructure.llm.LlmProviderFactory;
import com.example.aichat.infrastructure.llm.ProviderProperties;
import com.example.aichat.infrastructure.llm.compatible.OpenAiCompatibleLlmProvider;
import org.springframework.stereotype.Component;

/**
 * Creates {@link QwenLlmProvider} for {@code type: openai-compatible} + {@code family: qwen}.
 */
@Component
public class QwenProviderFactory implements LlmProviderFactory {

    private final LlmAdapterSupport support;

    public QwenProviderFactory(LlmAdapterSupport support) {
        this.support = support;
    }

    @Override
    public String type() {
        return OpenAiCompatibleLlmProvider.TYPE;
    }

    @Override
    public String family() {
        return QwenLlmProvider.FAMILY;
    }

    @Override
    public LlmProvider create(String providerId, ProviderProperties properties) {
        return new QwenLlmProvider(providerId, properties, support);
    }
}
