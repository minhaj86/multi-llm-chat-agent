package com.example.aichat.rag.application;

import java.util.List;

/**
 * Reorders retrieval candidates by relevance to the query. Future implementations: cross-encoder models, Cohere
 * Rerank, a local reranker, or LLM-based reranking. Selected with {@code rag.reranking.provider} when
 * {@code rag.reranking.enabled=true}.
 */
public interface RerankingService {

    /** Name used in {@code rag.reranking.provider}. */
    String name();

    /** The same chunks, best first; implementations may set {@link RetrievedChunk#rerankScore()}. */
    List<RetrievedChunk> rerank(String query, List<RetrievedChunk> chunks);
}
