package com.example.aichat.rag.application;

import java.util.Set;
import java.util.UUID;

/**
 * Options of one retrieval; {@code null} values fall back to {@code rag.retrieval.*}.
 *
 * @param collectionIds collections to search (empty = every enabled collection)
 * @param tags          only sources having at least one of these tags
 * @param sourceIds     only these sources
 */
public record RetrievalOptions(Set<UUID> collectionIds,
                               Integer topK,
                               Double similarityThreshold,
                               Set<String> tags,
                               Set<UUID> sourceIds,
                               RetrievalMode mode) {

    public RetrievalOptions {
        collectionIds = collectionIds == null ? Set.of() : Set.copyOf(collectionIds);
        tags = tags == null ? Set.of() : Set.copyOf(tags);
        sourceIds = sourceIds == null ? Set.of() : Set.copyOf(sourceIds);
    }

    public static RetrievalOptions defaults() {
        return new RetrievalOptions(null, null, null, null, null, null);
    }

    public static RetrievalOptions forCollections(Set<UUID> collectionIds) {
        return new RetrievalOptions(collectionIds, null, null, null, null, null);
    }
}
