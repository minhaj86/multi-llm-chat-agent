package com.example.aichat.rag.domain;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * One vector entry. Filterable metadata (collection, enabled flag, tags) is stored with the vector, as vector
 * databases such as Qdrant or Pinecone store it in their payload; {@link VectorStore#updateSourceMetadata} keeps
 * it in sync.
 */
public record VectorRecord(UUID chunkId,
                           UUID sourceId,
                           UUID collectionId,
                           String embeddingModel,
                           float[] embedding,
                           boolean sourceEnabled,
                           List<String> tags) {

    public VectorRecord {
        Objects.requireNonNull(chunkId, "chunkId");
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(collectionId, "collectionId");
        Objects.requireNonNull(embeddingModel, "embeddingModel");
        Objects.requireNonNull(embedding, "embedding");
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
