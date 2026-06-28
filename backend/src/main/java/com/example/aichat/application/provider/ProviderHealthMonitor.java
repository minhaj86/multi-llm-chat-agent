package com.example.aichat.application.provider;

import com.example.aichat.common.Times;
import com.example.aichat.domain.llm.HealthStatus;
import com.example.aichat.domain.llm.LlmModel;
import com.example.aichat.domain.llm.LlmProvider;
import com.example.aichat.domain.llm.ProviderHealth;
import com.example.aichat.domain.llm.exception.LlmAuthenticationException;
import com.example.aichat.domain.llm.exception.LlmException;
import com.example.aichat.domain.llm.exception.LlmProviderUnavailableException;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically probes every enabled provider with its cheap health check (e.g. {@code GET /v1/models}) - never
 * with an inference request. Results are cached and also updated passively from real chat traffic so that an
 * outage is visible immediately.
 */
@Component
public class ProviderHealthMonitor {

    private static final Logger log = LoggerFactory.getLogger(ProviderHealthMonitor.class);

    private final LlmProviderRegistry registry;
    private final ProviderModelCatalog modelCatalog;
    private final Clock clock;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<String, ProviderHealth> current = new ConcurrentHashMap<>();
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    public ProviderHealthMonitor(LlmProviderRegistry registry, ProviderModelCatalog modelCatalog, Clock clock) {
        this.registry = registry;
        this.modelCatalog = modelCatalog;
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "${llm.health.initial-delay:PT2S}",
            fixedDelayString = "${llm.health.check-interval:PT30S}")
    public void checkAll() {
        CompletableFuture<?>[] checks = registry.all().stream()
                .map(provider -> CompletableFuture.runAsync(() -> check(provider.providerId()), executor))
                .toArray(CompletableFuture[]::new);
        try {
            CompletableFuture.allOf(checks).get(2, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("Provider health checks did not complete: {}", e.toString());
        }
    }

    /** Runs a health check now and returns the fresh result. */
    public ProviderHealth check(String providerId) {
        LlmProvider provider = registry.require(providerId);
        EffectiveProviderSettings settings = registry.settings(providerId);
        if (!settings.enabled()) {
            ProviderHealth disabled = new ProviderHealth(providerId, HealthStatus.UNKNOWN, "Provider is disabled",
                    null, Times.now(clock));
            current.put(providerId, disabled);
            return disabled;
        }
        if (!inFlight.add(providerId)) {
            return current(providerId);
        }
        try {
            ProviderHealth health = probe(provider);
            if (health.status() == HealthStatus.UP) {
                health = verifyDefaultModel(provider, settings.defaultModel(), health);
            }
            update(providerId, health);
            return health;
        } finally {
            inFlight.remove(providerId);
        }
    }

    public ProviderHealth current(String providerId) {
        return current.getOrDefault(providerId, ProviderHealth.unknown(providerId));
    }

    /** Passive health signal: a real request succeeded. */
    public void reportSuccess(String providerId) {
        ProviderHealth health = current.get(providerId);
        if (health == null || health.status() == HealthStatus.DOWN || health.status() == HealthStatus.UNKNOWN) {
            update(providerId, new ProviderHealth(providerId, HealthStatus.UP, null, null, Times.now(clock)));
        }
    }

    /** Passive health signal: a real request failed in a way that indicates an outage or misconfiguration. */
    public void reportFailure(String providerId, LlmException failure) {
        if (failure instanceof LlmProviderUnavailableException || failure instanceof LlmAuthenticationException) {
            update(providerId, ProviderHealth.down(providerId, failure.getMessage(), null, Times.now(clock)));
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    private ProviderHealth probe(LlmProvider provider) {
        try {
            return provider.health();
        } catch (RuntimeException e) {
            return ProviderHealth.down(provider.providerId(), e.getMessage(), null, Times.now(clock));
        }
    }

    private ProviderHealth verifyDefaultModel(LlmProvider provider, String defaultModel, ProviderHealth health) {
        if (defaultModel == null) {
            return health;
        }
        try {
            List<LlmModel> models = modelCatalog.models(provider);
            if (!models.isEmpty() && !ProviderModelCatalog.contains(models, defaultModel)) {
                return ProviderHealth.degraded(provider.providerId(),
                        "Default model '" + defaultModel + "' was not found on the server",
                        health.latencyMs() == null ? 0 : health.latencyMs(), health.checkedAt());
            }
        } catch (RuntimeException e) {
            log.debug("Could not verify models of provider {}: {}", provider.providerId(), e.toString());
        }
        return health;
    }

    private void update(String providerId, ProviderHealth health) {
        ProviderHealth previous = current.put(providerId, health);
        if (previous == null || previous.status() != health.status()) {
            if (health.isAvailable()) {
                log.info("LLM provider '{}' is {}", providerId, health.status());
            } else {
                log.warn("LLM provider '{}' is {}: {}", providerId, health.status(), health.message());
            }
        }
    }

}
