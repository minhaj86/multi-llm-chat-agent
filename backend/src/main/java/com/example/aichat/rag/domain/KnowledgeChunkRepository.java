package com.example.aichat.rag.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for chunks (text and citation metadata; vectors live in the {@link VectorStore}).
 */
public interface KnowledgeChunkRepository {

    /** Replaces all chunks of a source (delete + insert) inside the caller's transaction. */
    void replaceForSource(UUID sourceId, List<KnowledgeChunk> chunks);

    void deleteBySourceId(UUID sourceId);

    PageResult<KnowledgeChunk> findBySourceId(UUID sourceId, int page, int size);

    Optional<KnowledgeChunk> findById(UUID chunkId);

    List<KnowledgeChunk> findByIds(Collection<UUID> chunkIds);

    long countBySourceId(UUID sourceId);

    void updateCollection(UUID sourceId, UUID collectionId);
}
