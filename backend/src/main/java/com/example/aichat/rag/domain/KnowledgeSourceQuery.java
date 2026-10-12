package com.example.aichat.rag.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Filters, sorting and paging for the knowledge base overview. {@code null} fields do not filter.
 */
public record KnowledgeSourceQuery(String text,
                                   UUID collectionId,
                                   KnowledgeSourceType type,
                                   IngestionStatus status,
                                   Boolean enabled,
                                   String tag,
                                   Instant createdFrom,
                                   Instant createdTo,
                                   Sort sort,
                                   boolean ascending,
                                   int page,
                                   int size) {

    public static final int MAX_PAGE_SIZE = 200;

    public KnowledgeSourceQuery {
        sort = sort == null ? Sort.CREATED_AT : sort;
        page = Math.max(0, page);
        size = Math.clamp(size, 1, MAX_PAGE_SIZE);
    }

    public enum Sort {
        TITLE,
        CREATED_AT,
        UPDATED_AT,
        CHUNK_COUNT,
        FILE_SIZE
    }
}
