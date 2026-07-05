package com.example.aichat.infrastructure.persistence.adapter;

import com.example.aichat.domain.conversation.Message;
import com.example.aichat.domain.conversation.MessageRepository;
import com.example.aichat.domain.conversation.MessageStatus;
import com.example.aichat.domain.llm.exception.LlmErrorCode;
import com.example.aichat.infrastructure.persistence.entity.MessageEntity;
import com.example.aichat.infrastructure.persistence.jpa.MessageJpaRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class JpaMessageRepositoryAdapter implements MessageRepository {

    private final MessageJpaRepository jpa;

    public JpaMessageRepositoryAdapter(MessageJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public Message save(Message message) {
        return jpa.save(MessageEntity.from(message)).toDomain();
    }

    @Override
    public Optional<Message> findById(UUID id) {
        return jpa.findById(id).map(MessageEntity::toDomain);
    }

    @Override
    public List<Message> findByConversationId(UUID conversationId) {
        return jpa.findByConversation(conversationId).stream().map(MessageEntity::toDomain).toList();
    }

    @Override
    @Transactional
    public void deleteAllById(Collection<UUID> ids) {
        if (!ids.isEmpty()) {
            jpa.deleteAllByIdInBatch(ids);
        }
    }

    @Override
    public long countByConversationId(UUID conversationId) {
        return jpa.countByConversation(conversationId);
    }

    @Override
    @Transactional
    public int failStaleStreaming(Instant olderThan, Instant now) {
        return jpa.failStale(MessageStatus.STREAMING, MessageStatus.ERROR, LlmErrorCode.STREAM_INTERRUPTED.name(),
                "The response was interrupted before it completed.", olderThan, now);
    }
}
