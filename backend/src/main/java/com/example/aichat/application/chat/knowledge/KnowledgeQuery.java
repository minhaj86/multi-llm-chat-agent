package com.example.aichat.application.chat.knowledge;

import java.util.Set;
import java.util.UUID;

/**
 * A knowledge lookup for one chat answer.
 *
 * @param collectionIds collections to search; empty = every enabled collection
 */
public record KnowledgeQuery(UUID userId,
                             UUID conversationId,
                             UUID assistantMessageId,
                             String question,
                             Set<UUID> collectionIds,
                             String correlationId) {

    public KnowledgeQuery {
        collectionIds = collectionIds == null ? Set.of() : Set.copyOf(collectionIds);
    }
}
