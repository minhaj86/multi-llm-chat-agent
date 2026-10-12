package com.example.aichat.rag.domain;

import java.util.List;
import java.util.UUID;

/**
 * Optional keyword (full-text) search over chunk content, used by hybrid retrieval next to vector search.
 * The same filter semantics as {@link VectorStore#search} apply (enabled sources only).
 */
public interface KeywordSearcher {

    List<KeywordSearchResult> search(String query, VectorFilter filter, int limit);

    record KeywordSearchResult(UUID chunkId, UUID sourceId, double score) {
    }
}
