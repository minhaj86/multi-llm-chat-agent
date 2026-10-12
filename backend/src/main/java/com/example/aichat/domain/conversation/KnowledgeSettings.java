package com.example.aichat.domain.conversation;

import java.util.Set;
import java.util.UUID;

/**
 * Per-conversation use of the knowledge base (RAG).
 *
 * @param collectionIds collections to search; empty = all enabled collections
 */
public record KnowledgeSettings(boolean enabled, Set<UUID> collectionIds) {

    public static final int MAX_COLLECTIONS = 50;
    public static final KnowledgeSettings DISABLED = new KnowledgeSettings(false, Set.of());

    public KnowledgeSettings {
        collectionIds = collectionIds == null ? Set.of() : Set.copyOf(collectionIds);
        if (collectionIds.size() > MAX_COLLECTIONS) {
            throw new IllegalArgumentException("At most " + MAX_COLLECTIONS + " knowledge collections");
        }
    }
}
