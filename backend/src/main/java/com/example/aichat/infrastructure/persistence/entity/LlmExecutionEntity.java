package com.example.aichat.infrastructure.persistence.entity;

import com.example.aichat.domain.llm.ProviderKind;
import com.example.aichat.domain.usage.ExecutionPurpose;
import com.example.aichat.domain.usage.ExecutionStatus;
import com.example.aichat.domain.usage.LlmExecution;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "llm_executions")
public class LlmExecutionEntity {

    @Id
    private UUID id;
    @Column(name = "message_id")
    private UUID messageId;
    @Column(name = "conversation_id")
    private UUID conversationId;
    @Column(name = "user_id")
    private UUID userId;
    @Column(nullable = false, length = 64)
    private String provider;
    @Column(nullable = false, length = 200)
    private String model;
    @Enumerated(EnumType.STRING)
    @Column(name = "provider_kind", nullable = false, length = 16)
    private ProviderKind providerKind;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ExecutionPurpose purpose;
    @Column(name = "input_tokens")
    private Integer inputTokens;
    @Column(name = "output_tokens")
    private Integer outputTokens;
    @Column(name = "latency_ms", nullable = false)
    private long latencyMs;
    @Column(name = "time_to_first_token_ms")
    private Long timeToFirstTokenMs;
    @Column(name = "estimated_cost", precision = 14, scale = 6)
    private BigDecimal estimatedCost;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ExecutionStatus status;
    @Column(name = "error_code", length = 64)
    private String errorCode;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LlmExecutionEntity() {
    }

    public static LlmExecutionEntity from(LlmExecution execution) {
        LlmExecutionEntity entity = new LlmExecutionEntity();
        entity.id = execution.id();
        entity.messageId = execution.messageId();
        entity.conversationId = execution.conversationId();
        entity.userId = execution.userId();
        entity.provider = execution.provider();
        entity.model = execution.model();
        entity.providerKind = execution.providerKind();
        entity.purpose = execution.purpose();
        entity.inputTokens = execution.inputTokens();
        entity.outputTokens = execution.outputTokens();
        entity.latencyMs = execution.latencyMs();
        entity.timeToFirstTokenMs = execution.timeToFirstTokenMs();
        entity.estimatedCost = execution.estimatedCost();
        entity.status = execution.status();
        entity.errorCode = execution.errorCode();
        entity.createdAt = execution.createdAt();
        return entity;
    }

    public LlmExecution toDomain() {
        return new LlmExecution(id, messageId, conversationId, userId, provider, model, providerKind, purpose,
                inputTokens, outputTokens, latencyMs, timeToFirstTokenMs, estimatedCost, status, errorCode, createdAt);
    }

    public UUID getMessageId() {
        return messageId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
