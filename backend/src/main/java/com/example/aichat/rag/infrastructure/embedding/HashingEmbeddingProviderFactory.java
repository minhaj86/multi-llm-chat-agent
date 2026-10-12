package com.example.aichat.rag.infrastructure.embedding;

import com.example.aichat.rag.application.EmbeddingProviderFactory;
import com.example.aichat.rag.application.RagProperties;
import com.example.aichat.rag.domain.EmbeddingProvider;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * {@code rag.embedding.provider=hashing}: offline keyword-level embeddings (see {@link HashingEmbeddingProvider}).
 * {@code rag.embedding.dimensions} sets the vector size (default 1024); the model id records it.
 */
@Component
public class HashingEmbeddingProviderFactory implements EmbeddingProviderFactory {

    @Override
    public Set<String> types() {
        return Set.of("hashing");
    }

    @Override
    public EmbeddingProvider create(RagProperties.Embedding configuration) {
        int dimensions = configuration.dimensions() > 0 ? configuration.dimensions()
                : HashingEmbeddingProvider.DEFAULT_DIMENSIONS;
        return new HashingEmbeddingProvider(dimensions);
    }
}
