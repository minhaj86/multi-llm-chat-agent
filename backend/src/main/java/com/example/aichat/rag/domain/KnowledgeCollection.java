package com.example.aichat.rag.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Groups knowledge sources (e.g. "Engineering", "Company Policies"). A disabled collection is excluded from
 * retrieval without touching its sources. Exactly one collection is the default for new knowledge.
 */
public record KnowledgeCollection(UUID id,
                                  String name,
                                  String description,
                                  boolean enabled,
                                  boolean defaultCollection,
                                  Instant createdAt,
                                  Instant updatedAt) {

    public static final int MAX_NAME_LENGTH = 100;
    public static final int MAX_DESCRIPTION_LENGTH = 500;

    public KnowledgeCollection {
        Objects.requireNonNull(id, "id");
        name = Objects.requireNonNull(name, "name").strip();
        description = description == null || description.isBlank() ? null : description.strip();
    }

    public static KnowledgeCollection create(String name, String description, boolean enabled, Instant now) {
        return new KnowledgeCollection(UUID.randomUUID(), name, description, enabled, false, now, now);
    }

    public KnowledgeCollection update(String newName, String newDescription, boolean newEnabled, Instant now) {
        return new KnowledgeCollection(id, newName, newDescription, newEnabled, defaultCollection, createdAt, now);
    }
}
