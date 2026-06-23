package com.example.aichat.infrastructure.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.aichat.application.provider.ProviderSettingsService;
import com.example.aichat.domain.llm.LlmChunk;
import com.example.aichat.domain.llm.LlmModel;
import com.example.aichat.domain.llm.LlmProvider;
import com.example.aichat.domain.llm.LlmRequest;
import com.example.aichat.domain.llm.LlmResponse;
import com.example.aichat.domain.llm.LlmUsage;
import com.example.aichat.domain.llm.ProviderDefaults;
import com.example.aichat.domain.llm.ProviderDescriptor;
import com.example.aichat.domain.llm.ProviderHealth;
import com.example.aichat.domain.llm.ProviderKind;
import com.example.aichat.domain.llm.exception.LlmErrorCode;
import com.example.aichat.domain.llm.exception.LlmException;
import com.example.aichat.domain.llm.exception.LlmProviderUnavailableException;
import com.example.aichat.domain.llm.exception.LlmResponseException;
import com.example.aichat.domain.llm.exception.LlmTimeoutException;
import com.example.aichat.support.InMemoryProviderSettingsRepository;
import com.example.aichat.support.LlmTestSupport;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class ResilientLlmProviderTest {

    private static ProviderProperties properties() {
        ProviderProperties properties = new ProviderProperties();
        properties.getRetry().setInitialBackoff(Duration.ofMillis(10));
        properties.getCircuitBreaker().setMinimumNumberOfCalls(2);
        properties.getCircuitBreaker().setSlidingWindowSize(2);
        properties.getCircuitBreaker().setWaitDurationInOpenState(Duration.ofMinutes(1));
        return properties;
    }

    private static ResilientLlmProvider resilient(String id, LlmProvider delegate) {
        ProviderProperties properties = properties();
        LlmProviderConfiguration configuration = new LlmProviderConfiguration();
        return (ResilientLlmProvider) ((ObservedLlmProvider) configuration.llmProviderRegistry(
                        singleProvider(id, properties),
                        List.of(new FixedFactory(delegate)),
                        new ProviderSettingsService(new InMemoryProviderSettingsRepository(), Clock.systemUTC(),
                                Duration.ZERO),
                        CircuitBreakerRegistry.ofDefaults(), RetryRegistry.ofDefaults(), new SimpleMeterRegistry())
                .require(id)).delegate();
    }

    private static LlmProperties singleProvider(String id, ProviderProperties properties) {
        properties.setType("fake");
        LlmProperties llm = new LlmProperties();
        llm.getProviders().put(id, properties);
        return llm;
    }

    private static LlmRequest request(Duration timeout) {
        return LlmRequest.builder("m").user("hi").timeout(timeout).build();
    }

    @Test
    void retriesAConnectionFailureBeforeAnyOutput() {
        AtomicInteger attempts = new AtomicInteger();
        LlmProvider provider = resilient("p1", fake(request -> Flux.defer(() -> attempts.incrementAndGet() == 1
                ? Flux.error(new LlmProviderUnavailableException("p1", "refused", true, null))
                : Flux.just(LlmChunk.text("ok"), new LlmChunk.Completed("m", LlmUsage.UNKNOWN, null, null)))));

        List<LlmChunk> chunks = provider.stream(request(Duration.ofSeconds(2))).collectList().block();

        assertThat(attempts).hasValue(2);
        assertThat(LlmTestSupport.text(chunks)).isEqualTo("ok");
    }

    @Test
    void neverRetriesOnceTokensWereStreamed() {
        AtomicInteger attempts = new AtomicInteger();
        LlmProvider provider = resilient("p2", fake(request -> Flux.defer(() -> {
            attempts.incrementAndGet();
            return Flux.concat(Flux.just(LlmChunk.text("partial")),
                    Flux.error(new LlmProviderUnavailableException("p2", "connection reset", true, null)));
        })));

        assertThatThrownBy(() -> provider.stream(request(Duration.ofSeconds(2))).blockLast())
                .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.isRetryable()).isFalse());
        assertThat(attempts).hasValue(1);
    }

    @Test
    void timeoutsAreReportedAndNotRetried() {
        AtomicInteger attempts = new AtomicInteger();
        LlmProvider provider = resilient("p3", fake(request -> Flux.defer(() -> {
            attempts.incrementAndGet();
            return Flux.never();
        })));

        assertThatThrownBy(() -> provider.stream(request(Duration.ofMillis(150))).blockLast(Duration.ofSeconds(5)))
                .isInstanceOf(LlmTimeoutException.class);
        assertThat(attempts).hasValue(1);
    }

    @Test
    void chatTimeoutCoversTheWholeResponse() {
        LlmProvider provider = resilient("p4", fakeChat(request -> Mono.never()));

        assertThatThrownBy(() -> provider.chat(request(Duration.ofMillis(150))).block(Duration.ofSeconds(5)))
                .isInstanceOf(LlmTimeoutException.class);
    }

    @Test
    void circuitOpensAfterRepeatedProviderFailuresAndFailsFast() {
        AtomicInteger attempts = new AtomicInteger();
        LlmProvider provider = resilient("p5", fake(request -> Flux.defer(() -> {
            attempts.incrementAndGet();
            return Flux.error(new LlmResponseException("p5", LlmErrorCode.UPSTREAM_ERROR, "500"));
        })));

        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> provider.stream(request(Duration.ofSeconds(1))).blockLast())
                    .isInstanceOf(LlmResponseException.class);
        }
        assertThatThrownBy(() -> provider.stream(request(Duration.ofSeconds(1))).blockLast())
                .isInstanceOf(LlmProviderUnavailableException.class)
                .hasMessageContaining("temporarily unavailable");
        assertThat(attempts).hasValue(2);
    }

    @Test
    void clientErrorsDoNotOpenTheCircuit() {
        AtomicInteger attempts = new AtomicInteger();
        LlmProvider provider = resilient("p6", fake(request -> Flux.defer(() -> {
            attempts.incrementAndGet();
            return Flux.error(new LlmResponseException("p6", LlmErrorCode.INVALID_REQUEST, "bad"));
        })));

        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> provider.stream(request(Duration.ofSeconds(1))).blockLast())
                    .isInstanceOf(LlmResponseException.class);
        }
        assertThat(attempts).hasValue(4);
    }

    private static LlmProvider fake(Function<LlmRequest, Flux<LlmChunk>> stream) {
        return new FakeProvider(stream, request -> Mono.empty());
    }

    private static LlmProvider fakeChat(Function<LlmRequest, Mono<LlmResponse>> chat) {
        return new FakeProvider(request -> Flux.empty(), chat);
    }

    private record FakeProvider(Function<LlmRequest, Flux<LlmChunk>> streamFn,
                                Function<LlmRequest, Mono<LlmResponse>> chatFn) implements LlmProvider {
        @Override
        public ProviderDescriptor descriptor() {
            return new ProviderDescriptor("fake", "Fake", "fake", ProviderKind.LOCAL, Set.of(), "http://fake", null,
                    new ProviderDefaults(true, "m", Duration.ofSeconds(5), null, null));
        }

        @Override
        public Mono<LlmResponse> chat(LlmRequest request) {
            return chatFn.apply(request);
        }

        @Override
        public Flux<LlmChunk> stream(LlmRequest request) {
            return streamFn.apply(request);
        }

        @Override
        public List<LlmModel> availableModels() {
            return List.of();
        }

        @Override
        public ProviderHealth health() {
            return ProviderHealth.unknown("fake");
        }
    }

    /** Factory returning a fixed adapter, mirroring how real adapters are plugged in. */
    private record FixedFactory(LlmProvider delegate) implements LlmProviderFactory {
        @Override
        public String type() {
            return "fake";
        }

        @Override
        public LlmProvider create(String providerId, ProviderProperties properties) {
            return new LlmProvider() {
                @Override
                public ProviderDescriptor descriptor() {
                    return new ProviderDescriptor(providerId, providerId, "fake", ProviderKind.LOCAL, Set.of(),
                            "http://fake", null, new ProviderDefaults(true, "m", properties.getTimeout(), null, null));
                }

                @Override
                public Mono<LlmResponse> chat(LlmRequest request) {
                    return delegate.chat(request);
                }

                @Override
                public Flux<LlmChunk> stream(LlmRequest request) {
                    return delegate.stream(request);
                }

                @Override
                public List<LlmModel> availableModels() {
                    return List.of();
                }

                @Override
                public ProviderHealth health() {
                    return ProviderHealth.unknown(providerId);
                }
            };
        }
    }
}
