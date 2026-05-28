package com.example.aichat.domain.conversation;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for messages.
 */
public interface MessageRepository {

    Message save(Message message);

    Optional<Message> findById(UUID id);

    /** All messages of a conversation in chronological order. */
    List<Message> findByConversationId(UUID conversationId);

    void deleteAllById(Collection<UUID> ids);

    long countByConversationId(UUID conversationId);

    /** Marks {@code STREAMING} messages not updated since {@code olderThan} as interrupted; returns the count. */
    int failStaleStreaming(Instant olderThan, Instant now);
}
