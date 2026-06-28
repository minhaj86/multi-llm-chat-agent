package com.example.aichat.application.provider;

import com.example.aichat.domain.llm.HealthStatus;
import com.example.aichat.domain.llm.LlmModel;
import com.example.aichat.domain.llm.LlmProvider;
import com.example.aichat.domain.llm.ProviderDescriptor;
import com.example.aichat.domain.llm.ProviderHealth;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Read model of providers for the UI: status, models and health. The frontend builds its provider/model selector
 * exclusively from this service, so no provider is hard-coded in the client.
 */
@Service
public class ProviderCatalogService {

    private static final Logger log = LoggerFactory.getLogger(ProviderCatalogService.class);

    private final LlmProviderRegistry registry;
    private final ProviderHealthMonitor healthMonitor;
    private final ProviderModelCatalog modelCatalog;

    public ProviderCatalogService(LlmProviderRegistry registry,
                                  ProviderHealthMonitor healthMonitor,
                                  ProviderModelCatalog modelCatalog) {
        this.registry = registry;
        this.healthMonitor = healthMonitor;
        this.modelCatalog = modelCatalog;
    }

    public List<ProviderStatus> providers() {
        return registry.all().stream().map(this::status).toList();
    }

    public ProviderStatus provider(String providerId) {
        return status(registry.require(providerId));
    }

    public String defaultProviderId() {
        return registry.defaultProviderId();
    }

    /**
     * Models of a provider; the effective default model is flagged and always included (first) even if the
     * server does not list it. Offline providers return cached models (or just the default model).
     */
    public List<ModelInfo> models(String providerId) {
        LlmProvider provider = registry.require(providerId);
        EffectiveProviderSettings settings = registry.settings(providerId);
        List<LlmModel> models = loadModels(provider);
        String defaultModel = settings.defaultModel();
        List<ModelInfo> result = new ArrayList<>(models.stream()
                .map(m -> new ModelInfo(m.id(), m.displayName(), m.ownedBy(), m.contextWindow(),
                        ProviderModelCatalog.matches(m.id(), defaultModel)))
                .toList());
        if (defaultModel != null && result.stream().noneMatch(ModelInfo::isDefault)) {
            result.addFirst(new ModelInfo(defaultModel, defaultModel, null, null, true));
        }
        return result;
    }

    public ProviderHealth health(String providerId, boolean refresh) {
        registry.require(providerId);
        return refresh ? healthMonitor.check(providerId) : healthMonitor.current(providerId);
    }

    /** Enabled and available providers other than {@code providerId}, suggested when a provider fails. */
    public List<ProviderRef> alternativesTo(String providerId) {
        return providers().stream()
                .filter(p -> p.available() && !p.id().equals(providerId))
                .map(p -> new ProviderRef(p.id(), p.name()))
                .toList();
    }

    public ProviderDescriptor descriptor(String providerId) {
        return registry.require(providerId).descriptor();
    }

    private List<LlmModel> loadModels(LlmProvider provider) {
        ProviderHealth health = healthMonitor.current(provider.providerId());
        if (health.status() == HealthStatus.DOWN) {
            return modelCatalog.cached(provider.providerId()).orElse(List.of());
        }
        try {
            return modelCatalog.models(provider);
        } catch (RuntimeException e) {
            log.debug("Model discovery failed for provider {}: {}", provider.providerId(), e.toString());
            return List.of();
        }
    }

    private ProviderStatus status(LlmProvider provider) {
        ProviderDescriptor descriptor = provider.descriptor();
        EffectiveProviderSettings settings = registry.settings(descriptor.id());
        ProviderHealth health = healthMonitor.current(descriptor.id());
        boolean available = settings.enabled() && health.isAvailable();
        String message = settings.enabled() ? health.message() : "Provider is disabled";
        return new ProviderStatus(descriptor.id(), descriptor.displayName(), descriptor.type(), descriptor.kind(),
                settings.enabled(), available, health.status(), message, settings.defaultModel(),
                descriptor.capabilities(), health.checkedAt(), health.latencyMs());
    }
}
