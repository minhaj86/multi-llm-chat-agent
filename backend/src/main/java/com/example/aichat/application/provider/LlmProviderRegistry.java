package com.example.aichat.application.provider;

import com.example.aichat.application.common.ValidationException;
import com.example.aichat.common.Texts;
import com.example.aichat.domain.llm.LlmProvider;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registry of all configured LLM providers (strategy pattern). Providers are looked up by id - there is no
 * vendor-specific branching anywhere in the application layer. New providers are contributed by infrastructure
 * adapters/factories and become available here automatically.
 */
public class LlmProviderRegistry {

    private final Map<String, LlmProvider> providers;
    private final String defaultProviderId;
    private final ProviderSettingsService settingsService;

    public LlmProviderRegistry(Collection<? extends LlmProvider> providers,
                               String defaultProviderId,
                               ProviderSettingsService settingsService) {
        Map<String, LlmProvider> index = new LinkedHashMap<>();
        for (LlmProvider provider : providers) {
            LlmProvider previous = index.putIfAbsent(provider.providerId(), provider);
            if (previous != null) {
                throw new IllegalStateException("Duplicate LLM provider id: " + provider.providerId());
            }
        }
        if (index.isEmpty()) {
            throw new IllegalStateException("No LLM providers configured (llm.providers)");
        }
        String resolvedDefault = Texts.hasText(defaultProviderId) ? defaultProviderId : index.keySet().iterator().next();
        if (!index.containsKey(resolvedDefault)) {
            throw new IllegalStateException("llm.default-provider '" + resolvedDefault
                    + "' is not a configured provider. Configured: " + index.keySet());
        }
        this.providers = Collections.unmodifiableMap(index);
        this.defaultProviderId = resolvedDefault;
        this.settingsService = settingsService;
    }

    /** All providers in configuration order. */
    public List<LlmProvider> all() {
        return List.copyOf(providers.values());
    }

    public Optional<LlmProvider> find(String providerId) {
        return Optional.ofNullable(providerId).map(providers::get);
    }

    public LlmProvider require(String providerId) {
        return find(providerId).orElseThrow(() -> new UnknownProviderException(providerId));
    }

    public String defaultProviderId() {
        return defaultProviderId;
    }

    public EffectiveProviderSettings settings(String providerId) {
        return settingsService.effective(require(providerId).descriptor());
    }

    public boolean isEnabled(String providerId) {
        return settings(providerId).enabled();
    }

    /**
     * Resolves the provider and model for a request. Blank values fall back to the default provider and the
     * provider's (possibly overridden) default model.
     */
    public ResolvedProvider resolve(String providerId, String model) {
        String id = Texts.hasText(providerId) ? providerId.strip() : defaultProviderId;
        LlmProvider provider = require(id);
        EffectiveProviderSettings settings = settingsService.effective(provider.descriptor());
        if (!settings.enabled()) {
            throw new ProviderDisabledException(id, provider.descriptor().displayName());
        }
        String resolvedModel = Texts.hasText(model) ? model.strip() : settings.defaultModel();
        if (Texts.isBlank(resolvedModel)) {
            throw new ValidationException("MODEL_REQUIRED",
                    "No model selected and no default model configured for " + provider.descriptor().displayName());
        }
        return new ResolvedProvider(provider, resolvedModel, settings);
    }
}
