package com.example.aichat.infrastructure.persistence.entity;

import com.example.aichat.domain.conversation.Message;
import com.example.aichat.domain.conversation.MessageRole;
import com.example.aichat.domain.conversation.MessageStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "messages")
public class MessageEntity {

    @Id
    private UUID id;
    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MessageRole role;
    @Column(nullable = false, columnDefinition = "text")
    private String content;
    @Column(columnDefinition = "text")
    private String reasoning;
    @Column(length = 64)
    private String provider;
    @Column(length = 200)
    private String model;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MessageStatus status;
    @Column(name = "error_code", length = 64)
    private String errorCode;
    @Column(name = "error_message", length = 1000)
    private String errorMessage;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MessageEntity() {
    }

    public static MessageEntity from(Message message) {
        MessageEntity entity = new MessageEntity();
        entity.id = message.id();
        entity.conversationId = message.conversationId();
        entity.role = message.role();
        entity.content = message.content();
        entity.reasoning = message.reasoning();
        entity.provider = message.provider();
        entity.model = message.model();
        entity.status = message.status();
        entity.errorCode = message.errorCode();
        entity.errorMessage = message.errorMessage();
        entity.createdAt = message.createdAt();
        entity.updatedAt = message.updatedAt();
        return entity;
    }

    public Message toDomain() {
        return new Message(id, conversationId, role, content, reasoning, provider, model, status, errorCode,
                errorMessage, createdAt, updatedAt);
    }
}
