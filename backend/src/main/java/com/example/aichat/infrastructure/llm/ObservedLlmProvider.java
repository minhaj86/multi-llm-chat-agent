package com.example.aichat.infrastructure.llm;

import com.example.aichat.domain.llm.LlmChunk;
import com.example.aichat.domain.llm.LlmModel;
import com.example.aichat.domain.llm.LlmProvider;
import com.example.aichat.domain.llm.LlmRequest;
import com.example.aichat.domain.llm.LlmResponse;
import com.example.aichat.domain.llm.LlmUsage;
import com.example.aichat.domain.llm.ProviderDescriptor;
import com.example.aichat.domain.llm.ProviderHealth;
import com.example.aichat.domain.llm.exception.LlmException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Decorator recording Micrometer metrics for every LLM call (exported to Prometheus as {@code llm_requests_total},
 * {@code llm_request_duration_seconds}, {@code llm_input_tokens}, {@code llm_output_tokens},
 * {@code llm_errors_total}) with {@code provider}, {@code model} and {@code status} labels.
 */
public final class ObservedLlmProvider implements LlmProvider {

    static final String REQUESTS = "llm.requests";
    static final String DURATION = "llm.request.duration";
    static final String INPUT_TOKENS = "llm.input.tokens";
    static final String OUTPUT_TOKENS = "llm.output.tokens";
    static final String ERRORS = "llm.errors";

    private final LlmProvider delegate;
    private final MeterRegistry registry;

    public ObservedLlmProvider(LlmProvider delegate, MeterRegistry registry) {
        this.delegate = delegate;
        this.registry = registry;
    }

    public LlmProvider delegate() {
        return delegate;
    }

    @Override
    public ProviderDescriptor descriptor() {
        return delegate.descriptor();
    }

    @Override
    public Mono<LlmResponse> chat(LlmRequest request) {
        return Mono.defer(() -> {
            long start = System.nanoTime();
            AtomicBoolean recorded = new AtomicBoolean();
            return delegate.chat(request)
                    .doOnSuccess(response -> record(recorded, request.model(), "success", start,
                            response == null ? null : response.usage(), null))
                    .doOnError(error -> record(recorded, request.model(), "error", start, null, error))
                    .doOnCancel(() -> record(recorded, request.model(), "cancelled", start, null, null));
        });
    }

    @Override
    public Flux<LlmChunk> stream(LlmRequest request) {
        return Flux.defer(() -> {
            long start = System.nanoTime();
            AtomicBoolean recorded = new AtomicBoolean();
            AtomicReference<LlmUsage> usage = new AtomicReference<>();
            return delegate.stream(request)
                    .doOnNext(chunk -> {
                        if (chunk instanceof LlmChunk.Completed completed) {
                            usage.set(completed.usage());
                        }
                    })
                    .doOnComplete(() -> record(recorded, request.model(), "success", start, usage.get(), null))
                    .doOnError(error -> record(recorded, request.model(), "error", start, usage.get(), error))
                    .doOnCancel(() -> record(recorded, request.model(), "cancelled", start, usage.get(), null));
        });
    }

    @Override
    public List<LlmModel> availableModels() {
        return delegate.availableModels();
    }

    @Override
    public ProviderHealth health() {
        return delegate.health();
    }

    private void record(AtomicBoolean recorded, String model, String status, long startNanos, LlmUsage usage,
                        Throwable error) {
        if (!recorded.compareAndSet(false, true)) {
            return;
        }
        String provider = providerId();
        Tags tags = Tags.of("provider", provider, "model", model, "status", status);
        Counter.builder(REQUESTS).description("LLM requests").tags(tags).register(registry).increment();
        Timer.builder(DURATION).description("LLM request duration (streams: until the last chunk)")
                .tags(tags).register(registry).record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
        if (usage != null && usage.inputTokens() != null) {
            DistributionSummary.builder(INPUT_TOKENS).description("Input (prompt) tokens per request")
                    .baseUnit("tokens").tags(tags).register(registry).record(usage.inputTokens());
        }
        if (usage != null && usage.outputTokens() != null) {
            DistributionSummary.builder(OUTPUT_TOKENS).description("Output (completion) tokens per request")
                    .baseUnit("tokens").tags(tags).register(registry).record(usage.outputTokens());
        }
        if (error != null) {
            String code = error instanceof LlmException llm ? llm.code().name() : "INTERNAL_ERROR";
            Counter.builder(ERRORS).description("Failed LLM requests")
                    .tags(Tags.of("provider", provider, "model", model, "status", "error", "code", code))
                    .register(registry).increment();
        }
    }
}
