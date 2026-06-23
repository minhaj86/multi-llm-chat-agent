package com.example.aichat.infrastructure.llm;

import com.example.aichat.domain.llm.LlmChunk;
import com.example.aichat.domain.llm.LlmModel;
import com.example.aichat.domain.llm.LlmProvider;
import com.example.aichat.domain.llm.LlmRequest;
import com.example.aichat.domain.llm.LlmResponse;
import com.example.aichat.domain.llm.ProviderDescriptor;
import com.example.aichat.domain.llm.ProviderHealth;
import com.example.aichat.domain.llm.exception.LlmException;
import com.example.aichat.domain.llm.exception.LlmProviderUnavailableException;
import com.example.aichat.domain.llm.exception.LlmTimeoutException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.reactor.timelimiter.TimeLimiterOperator;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Decorator adding Resilience4j timeout, retry and circuit breaker to any provider.
 *
 * <ul>
 *   <li><b>Timeout</b>: overall for {@code chat}; for {@code stream} the limit applies to the wait for each chunk
 *       (time-to-first-token and idle gaps), so long answers are not cut off.</li>
 *   <li><b>Retry</b>: only failures flagged as retryable by the adapter (connection refused, 502/503, short
 *       rate-limit waits). Once a stream has emitted data, failures become non-retryable to avoid duplicated output
 *       and double billing. Timeouts are never retried.</li>
 *   <li><b>Circuit breaker</b>: opens on provider failures (unreachable, timeouts, 5xx) and then fails fast with
 *       {@link LlmProviderUnavailableException}; client errors such as an invalid request do not count.</li>
 * </ul>
 */
public final class ResilientLlmProvider implements LlmProvider {

    private final LlmProvider delegate;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final Duration defaultTimeout;

    public ResilientLlmProvider(LlmProvider delegate, CircuitBreaker circuitBreaker, Retry retry,
                                Duration defaultTimeout) {
        this.delegate = delegate;
        this.circuitBreaker = circuitBreaker;
        this.retry = retry;
        this.defaultTimeout = defaultTimeout;
    }

    @Override
    public ProviderDescriptor descriptor() {
        return delegate.descriptor();
    }

    @Override
    public Mono<LlmResponse> chat(LlmRequest request) {
        Duration timeout = request.timeoutValue().orElse(defaultTimeout);
        return Mono.defer(() -> delegate.chat(request))
                .transformDeferred(TimeLimiterOperator.of(timeLimiter(timeout)))
                .onErrorMap(TimeoutException.class, e -> timeoutException(e, timeout))
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .transformDeferred(RetryOperator.of(retry))
                .onErrorMap(CallNotPermittedException.class, this::circuitOpen);
    }

    @Override
    public Flux<LlmChunk> stream(LlmRequest request) {
        Duration timeout = request.timeoutValue().orElse(defaultTimeout);
        return Flux.defer(() -> {
                    AtomicBoolean emitted = new AtomicBoolean();
                    return delegate.stream(request)
                            .transformDeferred(TimeLimiterOperator.of(timeLimiter(timeout)))
                            .doOnNext(chunk -> emitted.set(true))
                            .onErrorMap(error -> {
                                Throwable mapped = error instanceof TimeoutException
                                        ? timeoutException(error, timeout)
                                        : error;
                                if (emitted.get() && mapped instanceof LlmException llm) {
                                    llm.nonRetryable();
                                }
                                return mapped;
                            });
                })
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .transformDeferred(RetryOperator.of(retry))
                .onErrorMap(CallNotPermittedException.class, this::circuitOpen);
    }

    @Override
    public List<LlmModel> availableModels() {
        return delegate.availableModels();
    }

    @Override
    public ProviderHealth health() {
        return delegate.health();
    }

    private TimeLimiter timeLimiter(Duration timeout) {
        return TimeLimiter.of("llm-" + providerId(), TimeLimiterConfig.custom()
                .timeoutDuration(timeout)
                .cancelRunningFuture(true)
                .build());
    }

    private LlmTimeoutException timeoutException(Throwable cause, Duration timeout) {
        return new LlmTimeoutException(providerId(), descriptor().displayName() + " did not respond within "
                + timeout.toSeconds() + "s", cause);
    }

    private LlmProviderUnavailableException circuitOpen(CallNotPermittedException cause) {
        return new LlmProviderUnavailableException(providerId(), descriptor().displayName()
                + " is temporarily unavailable after repeated failures", false, cause);
    }
}
