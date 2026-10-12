package com.example.aichat.rag.application;

import com.example.aichat.rag.domain.EmbeddingProvider;
import java.util.Set;

/**
 * Creates the configured {@link EmbeddingProvider}. Add a factory bean to support a new embedding backend
 * (e.g. a sentence-transformers service or AWS Bedrock) and select it with {@code rag.embedding.provider}.
 */
public interface EmbeddingProviderFactory {

    /** Values of {@code rag.embedding.provider} handled by this factory (lower case). */
    Set<String> types();

    EmbeddingProvider create(RagProperties.Embedding configuration);
}
