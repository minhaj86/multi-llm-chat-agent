package com.example.aichat.domain.usage;

import com.example.aichat.domain.llm.ProviderKind;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Audit record of one LLM invocation (tokens, latency, cost, outcome).
 *
 * @param messageId     assistant message produced by the call ({@code null} for compare runs, titles, summaries)
 * @param estimatedCost estimated cost in USD; zero for local providers; {@code null} if unknown
 */
public record LlmExecution(UUID id,
                           UUID messageId,
                           UUID conversationId,
                           UUID userId,
                           String provider,
                           String model,
                           ProviderKind providerKind,
                           ExecutionPurpose purpose,
                           Integer inputTokens,
                           Integer outputTokens,
                           long latencyMs,
                           Long timeToFirstTokenMs,
                           BigDecimal estimatedCost,
                           ExecutionStatus status,
                           String errorCode,
                           Instant createdAt) {

    public LlmExecution {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(providerKind, "providerKind");
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
