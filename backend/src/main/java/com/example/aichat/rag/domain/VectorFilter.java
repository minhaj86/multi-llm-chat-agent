package com.example.aichat.rag.domain;

import java.util.Set;
import java.util.UUID;

/**
 * Restricts a vector or keyword search. Empty sets mean "no restriction", except {@code collectionIds}: the
 * retrieval service always resolves the allowed (enabled) collections, so an empty set yields no results.
 */
public record VectorFilter(Set<UUID> collectionIds, Set<UUID> sourceIds, Set<String> tags) {

    public VectorFilter {
        collectionIds = collectionIds == null ? Set.of() : Set.copyOf(collectionIds);
        sourceIds = sourceIds == null ? Set.of() : Set.copyOf(sourceIds);
        tags = tags == null ? Set.of() : Set.copyOf(tags);
    }
}
