package com.example.aichat.rag.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Record of one retrieval (chat message or admin test) for debugging and retrieval-quality analysis. Chunks
 * included in the LLM context carry the citation number shown to the user.
 */
public record RagExecution(UUID id,
                           Purpose purpose,
                           UUID conversationId,
                           UUID messageId,
                           UUID userId,
                           String query,
                           Set<UUID> collectionIds,
                           String embeddingProvider,
                           String embeddingModel,
                           String retrievalMode,
                           int retrievedChunkCount,
                           int contextChunkCount,
                           int totalContextTokens,
                           Long embeddingDurationMs,
                           long retrievalDurationMs,
                           Status status,
                           String errorMessage,
                           Instant createdAt,
                           List<RagRetrievedChunk> chunks) {

    public static final int MAX_QUERY_LENGTH = 4_000;

    public RagExecution {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(purpose, "purpose");
        query = query == null ? "" : query.length() <= MAX_QUERY_LENGTH ? query : query.substring(0, MAX_QUERY_LENGTH);
        collectionIds = collectionIds == null ? Set.of() : Set.copyOf(collectionIds);
        chunks = chunks == null ? List.of() : List.copyOf(chunks);
    }

    public enum Purpose {
        CHAT,
        TEST
    }

    public enum Status {
        SUCCESS,
        EMPTY,
        ERROR
    }
}
