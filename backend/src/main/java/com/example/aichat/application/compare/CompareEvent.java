package com.example.aichat.application.compare;

import com.example.aichat.domain.llm.FinishReason;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Events of a comparison run. Every target-specific event carries the {@code targetId} announced in
 * {@link Started}, so that the client can render the responses side by side while they stream in.
 */
public sealed interface CompareEvent {

    record Started(UUID runId, List<TargetInfo> targets) implements CompareEvent {
    }

    record Delta(String targetId, String text) implements CompareEvent {
    }

    record Reasoning(String targetId, String text) implements CompareEvent {
    }

    record Completed(String targetId,
                     FinishReason finishReason,
                     long latencyMs,
                     Long timeToFirstTokenMs,
                     Integer inputTokens,
                     Integer outputTokens,
                     BigDecimal estimatedCost,
                     boolean local) implements CompareEvent {
    }

    record Failed(String targetId, String code, String message, boolean retryable) implements CompareEvent {
    }

    record Finished(UUID runId) implements CompareEvent {
    }

    record TargetInfo(String targetId, String provider, String providerName, String model, boolean local) {
    }
}
