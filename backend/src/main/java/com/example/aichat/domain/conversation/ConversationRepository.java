package com.example.aichat.domain.conversation;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for conversations. Field-level update methods avoid lost updates between concurrent writers
 * (chat stream, title generation and background summarisation).
 */
public interface ConversationRepository {

    Conversation save(Conversation conversation);

    Optional<Conversation> findById(UUID id);

    Optional<Conversation> findByIdAndUserId(UUID id, UUID userId);

    /** Most recently updated first. */
    List<Conversation> findByUserId(UUID userId, int limit);

    void deleteById(UUID id);

    /** Records activity and the provider/model most recently used. */
    void touch(UUID id, String provider, String model, Instant now);

    /**
     * Sets a generated title unless the user renamed the conversation in the meantime. Does not change
     * {@code updatedAt}, so background updates never re-order the conversation list.
     */
    boolean updateGeneratedTitle(UUID id, String title);

    /** Stores the rolling summary; does not change {@code updatedAt}. */
    void updateSummary(UUID id, String summary, Instant summarizedUntil);
}
