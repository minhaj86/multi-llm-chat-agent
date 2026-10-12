package com.example.aichat.rag.domain;

import java.util.List;
import java.util.UUID;

/**
 * Port for vector storage and similarity search. The rest of the application depends on this interface only;
 * pgvector is one implementation, and Qdrant, Weaviate, Pinecone, Milvus or OpenSearch can be added as others.
 * The store knows chunks and sources, never conversations.
 */
public interface VectorStore {

    /** Identifier shown in diagnostics, e.g. {@code pgvector}. */
    String id();

    /**
     * Prepares storage for vectors of the given model and size before they are written, e.g. creates an index
     * (pgvector) or a collection (Qdrant). Called outside of write transactions; must be idempotent.
     */
    default void prepare(String embeddingModel, int dimensions) {
    }

    /** Inserts or replaces the vectors of the given chunks. */
    void store(List<VectorRecord> records);

    List<VectorSearchResult> search(VectorSearchRequest request);

    void deleteBySourceId(UUID sourceId);

    void deleteByCollectionId(UUID collectionId);

    /** Propagates source metadata that is used for filtering (moved collection, enabled flag, tags). */
    void updateSourceMetadata(UUID sourceId, UUID collectionId, boolean enabled, List<String> tags);

    long countBySourceId(UUID sourceId);
}
