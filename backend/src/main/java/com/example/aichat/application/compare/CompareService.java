package com.example.aichat.application.compare;

import com.example.aichat.application.chat.BackgroundTasks;
import com.example.aichat.application.chat.ChatProperties;
import com.example.aichat.application.chat.StreamAccumulator;
import com.example.aichat.application.common.ValidationException;
import com.example.aichat.application.prompt.SystemPromptService;
import com.example.aichat.application.provider.LlmErrorMessages;
import com.example.aichat.application.provider.LlmProviderRegistry;
import com.example.aichat.application.provider.ProviderHealthMonitor;
import com.example.aichat.application.provider.ResolvedProvider;
import com.example.aichat.application.usage.ExecutionRecord;
import com.example.aichat.application.usage.UsageRecorder;
import com.example.aichat.common.Texts;
import com.example.aichat.domain.llm.LlmChunk;
import com.example.aichat.domain.llm.LlmMessage;
import com.example.aichat.domain.llm.LlmRequest;
import com.example.aichat.domain.llm.LlmUsage;
import com.example.aichat.domain.llm.exception.LlmException;
import com.example.aichat.domain.usage.ExecutionPurpose;
import com.example.aichat.domain.usage.ExecutionStatus;
import com.example.aichat.domain.usage.LlmExecution;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Compare mode: streams the same prompt to several providers/models in parallel. A failing target never affects
 * the others. Every call is recorded for the usage dashboard (purpose {@code COMPARE}).
 */
@Service
public class CompareService {

    private static final Logger log = LoggerFactory.getLogger(CompareService.class);

    private final LlmProviderRegistry registry;
    private final SystemPromptService systemPrompts;
    private final UsageRecorder usageRecorder;
    private final ProviderHealthMonitor healthMonitor;
    private final BackgroundTasks tasks;
    private final ChatProperties properties;

    public CompareService(LlmProviderRegistry registry, SystemPromptService systemPrompts,
                          UsageRecorder usageRecorder, ProviderHealthMonitor healthMonitor, BackgroundTasks tasks,
                          ChatProperties properties) {
        this.registry = registry;
        this.systemPrompts = systemPrompts;
        this.usageRecorder = usageRecorder;
        this.healthMonitor = healthMonitor;
        this.tasks = tasks;
        this.properties = properties;
    }

    public Flux<CompareEvent> compare(CompareCommand command) {
        String prompt = validatePrompt(command.prompt());
        List<ResolvedProvider> targets = resolveTargets(command.targets());
        if (command.systemPromptId() != null) {
            systemPrompts.requireSelectable(command.systemPromptId());
        }
        List<LlmMessage> messages = new ArrayList<>(2);
        systemPrompts.contentFor(command.systemPromptId()).ifPresent(s -> messages.add(LlmMessage.system(s)));
        messages.add(LlmMessage.user(prompt));
        UUID runId = UUID.randomUUID();

        List<CompareEvent.TargetInfo> infos = new ArrayList<>(targets.size());
        List<Flux<CompareEvent>> streams = new ArrayList<>(targets.size());
        for (int i = 0; i < targets.size(); i++) {
            ResolvedProvider target = targets.get(i);
            String targetId = "t" + i;
            infos.add(new CompareEvent.TargetInfo(targetId, target.providerId(), target.displayName(), target.model(),
                    target.isLocal()));
            streams.add(targetStream(targetId, target, target.request(messages, command.correlationId()),
                    command.userId()));
        }
        log.info("Compare run {} started with {} target(s)", runId, targets.size());
        return Flux.concat(
                Mono.just(new CompareEvent.Started(runId, List.copyOf(infos))),
                Flux.merge(streams),
                Mono.just(new CompareEvent.Finished(runId)));
    }

    private Flux<CompareEvent> targetStream(String targetId, ResolvedProvider target, LlmRequest request,
                                            UUID userId) {
        return Flux.defer(() -> {
            StreamAccumulator state = new StreamAccumulator();
            return target.provider().stream(request)
                    .doOnNext(state::accept)
                    .<CompareEvent>handle((chunk, sink) -> {
                        if (chunk instanceof LlmChunk.TextDelta delta && !delta.text().isEmpty()) {
                            sink.next(new CompareEvent.Delta(targetId, delta.text()));
                        } else if (chunk instanceof LlmChunk.ReasoningDelta delta && !delta.text().isEmpty()) {
                            sink.next(new CompareEvent.Reasoning(targetId, delta.text()));
                        }
                    })
                    .concatWith(Mono.fromCallable(() -> completed(targetId, target, state, userId))
                            .subscribeOn(Schedulers.boundedElastic()))
                    .onErrorResume(error -> Mono.fromCallable(() -> failed(targetId, target, state, userId, error))
                            .subscribeOn(Schedulers.boundedElastic()))
                    .doOnCancel(() -> tasks.execute(() -> cancelled(target, state, userId)));
        });
    }

    private CompareEvent completed(String targetId, ResolvedProvider target, StreamAccumulator state, UUID userId) {
        if (!state.markFinished()) {
            return null;
        }
        LlmExecution execution = usageRecorder.record(record(target, state, userId, ExecutionStatus.SUCCESS, null));
        healthMonitor.reportSuccess(target.providerId());
        LlmUsage usage = state.usage();
        return new CompareEvent.Completed(targetId, state.finishReason(), execution.latencyMs(),
                execution.timeToFirstTokenMs(), usage.inputTokens(), usage.outputTokens(), execution.estimatedCost(),
                target.isLocal());
    }

    private CompareEvent failed(String targetId, ResolvedProvider target, StreamAccumulator state, UUID userId,
                                Throwable error) {
        if (!state.markFinished()) {
            return null;
        }
        String code = LlmErrorMessages.code(error);
        usageRecorder.record(record(target, state, userId, ExecutionStatus.ERROR, code));
        if (error instanceof LlmException llm) {
            healthMonitor.reportFailure(target.providerId(), llm);
        } else {
            log.error("Compare target {} failed unexpectedly", target.providerId(), error);
        }
        return new CompareEvent.Failed(targetId, code, LlmErrorMessages.describe(error, target.displayName()),
                LlmErrorMessages.retryable(error));
    }

    private void cancelled(ResolvedProvider target, StreamAccumulator state, UUID userId) {
        if (state.markFinished()) {
            usageRecorder.record(record(target, state, userId, ExecutionStatus.CANCELLED, null));
        }
    }

    private static ExecutionRecord record(ResolvedProvider target, StreamAccumulator state, UUID userId,
                                          ExecutionStatus status, String errorCode) {
        return new ExecutionRecord(target.descriptor(), target.model(), ExecutionPurpose.COMPARE, userId, null, null,
                state.usage(), state.latencyMs(), state.timeToFirstTokenMs(), status, errorCode);
    }

    private List<ResolvedProvider> resolveTargets(List<CompareCommand.Target> requested) {
        if (requested.isEmpty()) {
            throw new ValidationException("NO_TARGETS", "Select at least one provider to compare");
        }
        if (requested.size() > properties.compare().maxTargets()) {
            throw new ValidationException("TOO_MANY_TARGETS", "At most " + properties.compare().maxTargets()
                    + " providers can be compared at once");
        }
        Set<String> seen = new HashSet<>();
        List<ResolvedProvider> targets = new ArrayList<>(requested.size());
        for (CompareCommand.Target target : requested) {
            ResolvedProvider resolved = registry.resolve(target.provider(), target.model());
            if (!seen.add(resolved.providerId() + "\u0000" + resolved.model())) {
                throw new ValidationException("DUPLICATE_TARGET", "Each provider/model combination can only be "
                        + "compared once");
            }
            targets.add(resolved);
        }
        return targets;
    }

    private String validatePrompt(String prompt) {
        if (Texts.isBlank(prompt)) {
            throw new ValidationException("EMPTY_MESSAGE", "Prompt must not be empty");
        }
        if (prompt.length() > properties.maxMessageLength()) {
            throw new ValidationException("MESSAGE_TOO_LONG", "Prompt must not exceed "
                    + properties.maxMessageLength() + " characters");
        }
        return prompt.strip();
    }
}
