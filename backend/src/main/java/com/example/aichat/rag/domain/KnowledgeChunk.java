package com.example.aichat.rag.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A retrievable piece of a knowledge source. Character offsets refer to the extracted text, so a chunk can always
 * be traced back to its exact position (and page/section) for citations. Embeddings are not part of the chunk:
 * they belong to the {@link VectorStore}.
 */
public record KnowledgeChunk(UUID id,
                             UUID sourceId,
                             UUID collectionId,
                             int chunkIndex,
                             String content,
                             int tokenCount,
                             int characterStart,
                             int characterEnd,
                             Integer pageNumber,
                             String sectionTitle,
                             Map<String, Object> metadata,
                             Instant createdAt) {

    public KnowledgeChunk {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(collectionId, "collectionId");
        content = Objects.requireNonNull(content, "content");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
