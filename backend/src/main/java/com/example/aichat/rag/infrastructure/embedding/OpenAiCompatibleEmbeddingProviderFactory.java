package com.example.aichat.rag.infrastructure.embedding;

import com.example.aichat.rag.application.EmbeddingProviderFactory;
import com.example.aichat.rag.application.RagProperties;
import com.example.aichat.rag.domain.EmbeddingProvider;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * OpenAI embeddings and every server exposing the same API. {@code ollama}, {@code vllm}, {@code lm-studio},
 * {@code localai} and {@code llama-cpp} are accepted as aliases of {@code openai-compatible}.
 */
@Component
public class OpenAiCompatibleEmbeddingProviderFactory implements EmbeddingProviderFactory {

    private final WebClient.Builder builder;

    public OpenAiCompatibleEmbeddingProviderFactory(WebClient.Builder builder) {
        this.builder = builder;
    }

    @Override
    public Set<String> types() {
        return Set.of("openai", "openai-compatible", "ollama", "vllm", "lm-studio", "localai", "llama-cpp");
    }

    @Override
    public EmbeddingProvider create(RagProperties.Embedding configuration) {
        String type = configuration.provider().strip().toLowerCase(Locale.ROOT);
        return new OpenAiCompatibleEmbeddingProvider(type, configuration, builder);
    }
}
