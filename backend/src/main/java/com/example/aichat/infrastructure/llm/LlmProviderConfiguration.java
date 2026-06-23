package com.example.aichat.infrastructure.llm;

import com.example.aichat.application.provider.LlmProviderRegistry;
import com.example.aichat.application.provider.ProviderSettingsService;
import com.example.aichat.common.Texts;
import com.example.aichat.domain.llm.LlmProvider;
import com.example.aichat.domain.llm.exception.LlmException;
import com.example.aichat.domain.llm.exception.LlmRateLimitException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Instantiates the configured providers through their factories (strategy lookup by {@code type}/{@code family},
 * no switch statements) and decorates each with resilience and metrics before handing them to the
 * application-level {@link LlmProviderRegistry}.
 */
@Configuration(proxyBeanMethods = false)
public class LlmProviderConfiguration {

    private static final Logger log = LoggerFactory.getLogger(LlmProviderConfiguration.class);
    private static final Pattern PROVIDER_ID = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");

    @Bean
    public LlmProviderRegistry llmProviderRegistry(LlmProperties properties,
                                                   List<LlmProviderFactory> factories,
                                                   ProviderSettingsService settingsService,
                                                   CircuitBreakerRegistry circuitBreakerRegistry,
                                                   RetryRegistry retryRegistry,
                                                   MeterRegistry meterRegistry) {
        Map<FactoryKey, LlmProviderFactory> index = new LinkedHashMap<>();
        for (LlmProviderFactory factory : factories) {
            FactoryKey key = FactoryKey.of(factory.type(), factory.family());
            LlmProviderFactory previous = index.putIfAbsent(key, factory);
            if (previous != null) {
                throw new IllegalStateException("Two LLM provider factories registered for " + key);
            }
        }
        List<LlmProvider> providers = new ArrayList<>();
        properties.getProviders().forEach((id, config) -> {
            if (!PROVIDER_ID.matcher(id).matches()) {
                throw new IllegalStateException("Invalid provider id '" + id + "' (use lowercase letters, digits, "
                        + "'-' or '_')");
            }
            if (Texts.isBlank(config.getType())) {
                throw new IllegalStateException("llm.providers." + id + ".type is required");
            }
            LlmProviderFactory factory = index.get(FactoryKey.of(config.getType(), config.getFamily()));
            if (factory == null) {
                factory = index.get(FactoryKey.of(config.getType(), null));
            }
            if (factory == null) {
                throw new IllegalStateException("No LLM provider factory for type '" + config.getType()
                        + "' (provider '" + id + "'). Available: " + index.keySet());
            }
            LlmProvider adapter = factory.create(id, config);
            LlmProvider resilient = new ResilientLlmProvider(adapter,
                    circuitBreaker(circuitBreakerRegistry, id, config),
                    retry(retryRegistry, id, config),
                    config.getTimeout());
            providers.add(new ObservedLlmProvider(resilient, meterRegistry));
            log.info("Registered LLM provider '{}' ({}, type={}, family={}, endpoint={}, defaultModel={}, enabled={})",
                    id, adapter.descriptor().displayName(), config.getType(), config.getFamily(),
                    adapter.descriptor().endpoint(), config.getDefaultModel(), config.isEnabled());
        });
        return new LlmProviderRegistry(providers, properties.getDefaultProvider(), settingsService);
    }

    private static CircuitBreaker circuitBreaker(CircuitBreakerRegistry registry, String id,
                                                 ProviderProperties config) {
        ProviderProperties.CircuitBreaker settings = config.getCircuitBreaker();
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(settings.getFailureRateThreshold())
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(settings.getSlidingWindowSize())
                .minimumNumberOfCalls(settings.getMinimumNumberOfCalls())
                .waitDurationInOpenState(settings.getWaitDurationInOpenState())
                .permittedNumberOfCallsInHalfOpenState(settings.getPermittedCallsInHalfOpenState())
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .recordException(error -> error instanceof LlmException llm && llm.indicatesProviderFailure())
                .build();
        return registry.circuitBreaker("llm-" + id, circuitBreakerConfig);
    }

    private static Retry retry(RetryRegistry registry, String id, ProviderProperties config) {
        ProviderProperties.Retry settings = config.getRetry();
        long initialMillis = Math.max(0, settings.getInitialBackoff().toMillis());
        double multiplier = Math.max(1.0, settings.getBackoffMultiplier());
        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(Math.max(1, settings.getMaxAttempts()))
                .retryOnException(error -> error instanceof LlmException llm && llm.isRetryable())
                .intervalBiFunction((attempt, outcome) -> {
                    if (outcome.isLeft() && outcome.getLeft() instanceof LlmRateLimitException rateLimit
                            && rateLimit.retryAfter().isPresent()) {
                        return Math.max(initialMillis, rateLimit.retryAfter().get().toMillis());
                    }
                    return (long) (initialMillis * Math.pow(multiplier, Math.max(0, attempt - 1)));
                })
                .build();
        return registry.retry("llm-" + id, retryConfig);
    }

    private record FactoryKey(String type, String family) {
        static FactoryKey of(String type, String family) {
            return new FactoryKey(type == null ? null : type.strip().toLowerCase(Locale.ROOT),
                    Texts.isBlank(family) ? null : family.strip().toLowerCase(Locale.ROOT));
        }

        @Override
        public String toString() {
            return family == null ? type : type + "/" + family;
        }
    }
}
