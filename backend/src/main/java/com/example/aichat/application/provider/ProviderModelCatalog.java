package com.example.aichat.application.provider;

import com.example.aichat.domain.llm.LlmModel;
import com.example.aichat.domain.llm.LlmProvider;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Caches the model lists of providers. Model discovery is a cheap {@code GET /models} call, but it is still not
 * repeated for every UI request. A stale list is served when a refresh fails.
 */
@Component
public class ProviderModelCatalog {

    private final Clock clock;
    private final Duration ttl;
    private final Map<String, CachedModels> cache = new ConcurrentHashMap<>();

    public ProviderModelCatalog(Clock clock, @Value("${llm.models-cache-ttl:PT2M}") Duration ttl) {
        this.clock = clock;
        this.ttl = ttl;
    }

    public List<LlmModel> models(LlmProvider provider) {
        return models(provider, false);
    }

    public List<LlmModel> models(LlmProvider provider, boolean forceRefresh) {
        CachedModels cached = cache.get(provider.providerId());
        Instant now = clock.instant();
        if (!forceRefresh && cached != null && cached.fetchedAt().plus(ttl).isAfter(now)) {
            return cached.models();
        }
        try {
            List<LlmModel> models = List.copyOf(provider.availableModels());
            cache.put(provider.providerId(), new CachedModels(models, now));
            return models;
        } catch (RuntimeException e) {
            if (cached != null) {
                return cached.models();
            }
            throw e;
        }
    }

    public Optional<List<LlmModel>> cached(String providerId) {
        return Optional.ofNullable(cache.get(providerId)).map(CachedModels::models);
    }

    public void evict(String providerId) {
        cache.remove(providerId);
    }

    /** Whether {@code model} is offered, tolerating implicit {@code :latest} tags used by some local servers. */
    public static boolean contains(List<LlmModel> models, String model) {
        if (model == null) {
            return false;
        }
        return models.stream().anyMatch(m -> matches(m.id(), model));
    }

    public static boolean matches(String candidate, String model) {
        if (candidate == null || model == null) {
            return false;
        }
        return candidate.equalsIgnoreCase(model)
                || candidate.equalsIgnoreCase(model + ":latest")
                || model.equalsIgnoreCase(candidate + ":latest");
    }

    private record CachedModels(List<LlmModel> models, Instant fetchedAt) {
    }
}
