package com.example.aichat.rag.domain;

import java.util.Objects;

/**
 * Nearest-neighbour query: vectors of {@code embeddingModel} only (different models are not comparable), enabled
 * sources only, at most {@code limit} results ordered by descending similarity.
 */
public record VectorSearchRequest(float[] embedding, String embeddingModel, int limit, VectorFilter filter) {

    public VectorSearchRequest {
        Objects.requireNonNull(embedding, "embedding");
        Objects.requireNonNull(embeddingModel, "embeddingModel");
        filter = filter == null ? new VectorFilter(null, null, null) : filter;
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
    }
}
