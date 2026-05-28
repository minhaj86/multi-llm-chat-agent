package com.example.aichat.domain.prompt;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for system prompt profiles.
 */
public interface SystemPromptRepository {

    SystemPrompt save(SystemPrompt prompt);

    Optional<SystemPrompt> findById(UUID id);

    /** Ordered by sort order, then name. */
    List<SystemPrompt> findAll();

    Optional<SystemPrompt> findDefault();

    boolean existsByNameIgnoreCase(String name, UUID excludingId);

    void clearDefault();

    void deleteById(UUID id);
}
