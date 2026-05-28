package com.example.aichat.domain.prompt;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A server-side system prompt profile (e.g. "Java Expert"). Users pick a profile per conversation; administrators
 * manage the catalogue.
 */
public record SystemPrompt(UUID id,
                           String name,
                           String description,
                           String content,
                           boolean defaultPrompt,
                           boolean enabled,
                           int sortOrder,
                           Instant createdAt,
                           Instant updatedAt) {

    public SystemPrompt {
        Objects.requireNonNull(id, "id");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
    }

    public static SystemPrompt create(String name, String description, String content, boolean defaultPrompt,
                                      boolean enabled, int sortOrder, Instant now) {
        return new SystemPrompt(UUID.randomUUID(), name.strip(), description, content.strip(), defaultPrompt, enabled,
                sortOrder, now, now);
    }

    public SystemPrompt update(String newName, String newDescription, String newContent, boolean newDefault,
                               boolean newEnabled, int newSortOrder, Instant now) {
        return new SystemPrompt(id, newName.strip(), newDescription, newContent.strip(), newDefault, newEnabled,
                newSortOrder, createdAt, now);
    }

    public SystemPrompt withDefault(boolean value, Instant now) {
        return new SystemPrompt(id, name, description, content, value, enabled, sortOrder, createdAt, now);
    }
}
