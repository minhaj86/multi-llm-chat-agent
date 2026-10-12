package com.example.aichat.rag.domain;

import java.util.UUID;

/**
 * A vector match; {@code similarity} is the cosine similarity (1 = identical direction).
 */
public record VectorSearchResult(UUID chunkId, UUID sourceId, double similarity) {
}
