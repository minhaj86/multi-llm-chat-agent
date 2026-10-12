package com.example.aichat.rag.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for knowledge collections.
 */
public interface KnowledgeCollectionRepository {

    List<KnowledgeCollection> findAll();

    Optional<KnowledgeCollection> findById(UUID id);

    Optional<KnowledgeCollection> findDefault();

    boolean existsByName(String name, UUID excludeId);

    KnowledgeCollection save(KnowledgeCollection collection);

    void delete(UUID id);

    /** Usage per collection id (collections without sources are absent). */
    Map<UUID, CollectionUsage> usage();

    record CollectionUsage(long sources, long enabledSources, long chunks, long bytes, Instant lastUpdated) {
    }
}
