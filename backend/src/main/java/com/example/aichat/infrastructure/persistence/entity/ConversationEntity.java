package com.example.aichat.infrastructure.persistence.entity;

import com.example.aichat.domain.conversation.Conversation;
import com.example.aichat.domain.conversation.TitleSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "conversations")
public class ConversationEntity {

    @Id
    private UUID id;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(nullable = false, length = 200)
    private String title;
    @Enumerated(EnumType.STRING)
    @Column(name = "title_source", nullable = false, length = 16)
    private TitleSource titleSource;
    @Column(name = "system_prompt_id")
    private UUID systemPromptId;
    @Column(name = "last_provider", length = 64)
    private String lastProvider;
    @Column(name = "last_model", length = 200)
    private String lastModel;
    @Column(columnDefinition = "text")
    private String summary;
    @Column(name = "summarized_until")
    private Instant summarizedUntil;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ConversationEntity() {
    }

    public static ConversationEntity from(Conversation conversation) {
        ConversationEntity entity = new ConversationEntity();
        entity.id = conversation.id();
        entity.userId = conversation.userId();
        entity.title = conversation.title();
        entity.titleSource = conversation.titleSource();
        entity.systemPromptId = conversation.systemPromptId();
        entity.lastProvider = conversation.lastProvider();
        entity.lastModel = conversation.lastModel();
        entity.summary = conversation.summary();
        entity.summarizedUntil = conversation.summarizedUntil();
        entity.createdAt = conversation.createdAt();
        entity.updatedAt = conversation.updatedAt();
        return entity;
    }

    public Conversation toDomain() {
        return new Conversation(id, userId, title, titleSource, systemPromptId, lastProvider, lastModel, summary,
                summarizedUntil, createdAt, updatedAt);
    }
}
