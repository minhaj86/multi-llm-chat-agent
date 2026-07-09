package com.example.aichat.application.usage;

import com.example.aichat.common.Times;
import com.example.aichat.domain.llm.LlmUsage;
import com.example.aichat.domain.usage.LlmExecution;
import com.example.aichat.domain.usage.LlmExecutionRepository;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Persists {@link LlmExecution} audit records including the estimated cost. Recording failures are logged but
 * never break the user-facing request.
 */
@Service
public class UsageRecorder {

    private static final Logger log = LoggerFactory.getLogger(UsageRecorder.class);

    private final LlmExecutionRepository repository;
    private final CostEstimator costEstimator;
    private final Clock clock;

    public UsageRecorder(LlmExecutionRepository repository, CostEstimator costEstimator, Clock clock) {
        this.repository = repository;
        this.costEstimator = costEstimator;
        this.clock = clock;
    }

    public LlmExecution record(ExecutionRecord record) {
        LlmUsage usage = record.usage() == null ? LlmUsage.UNKNOWN : record.usage();
        CostEstimate cost = estimate(record, usage);
        LlmExecution execution = new LlmExecution(
                UUID.randomUUID(),
                record.messageId(),
                record.conversationId(),
                record.userId(),
                record.provider().id(),
                record.model(),
                record.provider().kind(),
                record.purpose(),
                usage.inputTokens(),
                usage.outputTokens(),
                record.latencyMs(),
                record.timeToFirstTokenMs(),
                cost.amount(),
                record.status(),
                record.errorCode(),
                Times.now(clock));
        try {
            return repository.save(execution);
        } catch (RuntimeException e) {
            log.warn("Could not record LLM execution for provider {}: {}", record.provider().id(), e.toString());
            return execution;
        }
    }

    public CostEstimate estimate(ExecutionRecord record, LlmUsage usage) {
        try {
            return costEstimator.estimate(record.provider(), record.model(), usage);
        } catch (RuntimeException e) {
            log.debug("Cost estimation failed: {}", e.toString());
            return record.provider().isLocal() ? CostEstimate.localInference() : CostEstimate.unknown();
        }
    }
}
