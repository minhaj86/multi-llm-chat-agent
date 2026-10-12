package com.example.aichat.rag.application;

import java.util.List;

/**
 * Finds the knowledge relevant to a question. Implementations combine query embedding, vector (and optionally
 * keyword) search, filtering, optional reranking and the similarity threshold; they never call a chat LLM.
 * Extension points for query rewriting, multi-query retrieval or HyDE belong in front of this service.
 */
public interface RetrievalService {

    List<RetrievedChunk> retrieve(String query, RetrievalOptions options);

    /** Same as {@link #retrieve} with diagnostics (timings, candidates, effective settings). */
    RetrievalResult search(String query, RetrievalOptions options);
}
