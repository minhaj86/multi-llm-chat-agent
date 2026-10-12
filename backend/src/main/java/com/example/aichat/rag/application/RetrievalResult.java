package com.example.aichat.rag.application;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Retrieval output plus diagnostics for the admin test console and the retrieval log.
 *
 * @param candidates matches before threshold, reranking and top-k
 */
public record RetrievalResult(String query,
                              List<RetrievedChunk> chunks,
                              int candidates,
                              Set<UUID> collectionIds,
                              RetrievalMode mode,
                              int topK,
                              double similarityThreshold,
                              String embeddingProvider,
                              String embeddingModel,
                              String reranker,
                              long embeddingMs,
                              long searchMs,
                              long totalMs) {

    public RetrievalResult {
        chunks = List.copyOf(chunks);
        collectionIds = Set.copyOf(collectionIds);
    }
}
