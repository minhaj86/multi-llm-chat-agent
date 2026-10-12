package com.example.aichat.rag.application;

import com.example.aichat.application.provider.LlmProviderRegistry;
import com.example.aichat.application.provider.ResolvedProvider;
import com.example.aichat.application.usage.ExecutionRecord;
import com.example.aichat.application.usage.UsageRecorder;
import com.example.aichat.common.Times;
import com.example.aichat.domain.llm.LlmMessage;
import com.example.aichat.domain.llm.LlmResponse;
import com.example.aichat.domain.llm.LlmUsage;
import com.example.aichat.domain.usage.ExecutionPurpose;
import com.example.aichat.domain.usage.ExecutionStatus;
import com.example.aichat.domain.usage.LlmExecution;
import com.example.aichat.rag.application.RagContextService.RagPreparation;
import com.example.aichat.rag.domain.RagExecution;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Admin test console: retrieval without any LLM call, and the full question -> retrieval -> context -> selected
 * LLM -> answer flow with every intermediate result exposed for troubleshooting.
 */
@Service
public class RagTestService {

    private static final Duration ANSWER_TIMEOUT = Duration.ofMinutes(5);

    private final RetrievalService retrieval;
    private final RagContextService contextService;
    private final RagExecutionRecorder recorder;
    private final LlmProviderRegistry registry;
    private final UsageRecorder usageRecorder;

    public RagTestService(RetrievalService retrieval, RagContextService contextService, RagExecutionRecorder recorder,
                          LlmProviderRegistry registry, UsageRecorder usageRecorder) {
        this.retrieval = retrieval;
        this.contextService = contextService;
        this.recorder = recorder;
        this.registry = registry;
        this.usageRecorder = usageRecorder;
    }

    public RagPreparation search(UUID userId, String question, RetrievalOptions options) {
        RetrievalResult result = retrieval.search(question, options);
        RagPreparation preparation = new RagPreparation(result, new RagContext(null, null, List.of(), List.of(), 0,
                false));
        recorder.success(RagExecution.Purpose.TEST, new RagExecutionRecorder.Scope(userId, null, null,
                options.collectionIds()), preparation);
        return preparation;
    }

    public TestAnswer answer(UUID userId, String question, RetrievalOptions options, String provider, String model,
                             String correlationId) {
        ResolvedProvider target = registry.resolve(provider, model);
        RagPreparation preparation = contextService.prepare(question, options);
        recorder.success(RagExecution.Purpose.TEST, new RagExecutionRecorder.Scope(userId, null, null,
                options.collectionIds()), preparation);
        RagContext context = preparation.context();
        List<LlmMessage> messages = new ArrayList<>();
        if (!context.isEmpty()) {
            messages.add(LlmMessage.system(context.instructions()));
        }
        String userMessage = context.userMessage(question);
        messages.add(LlmMessage.user(userMessage));

        long start = System.nanoTime();
        LlmResponse response;
        try {
            response = target.provider().chat(target.request(messages, correlationId)).block(ANSWER_TIMEOUT);
        } catch (RuntimeException e) {
            usageRecorder.record(new ExecutionRecord(target.descriptor(), target.model(), ExecutionPurpose.RAG_TEST,
                    userId, null, null, LlmUsage.UNKNOWN, Times.elapsedMillis(start), null, ExecutionStatus.ERROR,
                    e.getClass().getSimpleName()));
            throw e;
        }
        long latencyMs = Times.elapsedMillis(start);
        LlmUsage usage = response == null ? LlmUsage.UNKNOWN : response.usage();
        LlmExecution execution = usageRecorder.record(new ExecutionRecord(target.descriptor(), target.model(),
                ExecutionPurpose.RAG_TEST, userId, null, null, usage, latencyMs, null, ExecutionStatus.SUCCESS, null));
        return new TestAnswer(response == null ? "" : response.content(), response == null ? null : response.reasoning(),
                target.providerId(), target.displayName(), target.model(), target.isLocal(), latencyMs,
                usage.inputTokens(), usage.outputTokens(), execution.estimatedCost(), preparation,
                context.isEmpty() ? null : context.instructions(), userMessage);
    }

    /**
     * @param systemMessage the knowledge instructions sent as system message ({@code null} without knowledge)
     * @param userMessage   the exact user turn sent to the model (excerpts + question)
     */
    public record TestAnswer(String answer,
                             String reasoning,
                             String provider,
                             String providerName,
                             String model,
                             boolean local,
                             long latencyMs,
                             Integer inputTokens,
                             Integer outputTokens,
                             BigDecimal estimatedCost,
                             RagPreparation preparation,
                             String systemMessage,
                             String userMessage) {
    }
}
