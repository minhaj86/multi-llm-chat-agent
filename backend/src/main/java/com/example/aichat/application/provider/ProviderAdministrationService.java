package com.example.aichat.application.provider;

import com.example.aichat.domain.llm.LlmModel;
import com.example.aichat.domain.llm.LlmProvider;
import com.example.aichat.domain.llm.ProviderDescriptor;
import com.example.aichat.domain.llm.ProviderHealth;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Administrator view and runtime tuning of providers (enabled flag, default model, timeout, temperature, token
 * limit). Changes take effect immediately, without restarting anything.
 */
@Service
public class ProviderAdministrationService {

    private final LlmProviderRegistry registry;
    private final ProviderSettingsService settingsService;
    private final ProviderHealthMonitor healthMonitor;
    private final ProviderModelCatalog modelCatalog;

    public ProviderAdministrationService(LlmProviderRegistry registry, ProviderSettingsService settingsService,
                                         ProviderHealthMonitor healthMonitor, ProviderModelCatalog modelCatalog) {
        this.registry = registry;
        this.settingsService = settingsService;
        this.healthMonitor = healthMonitor;
        this.modelCatalog = modelCatalog;
    }

    public List<ProviderAdminView> list() {
        return registry.all().stream().map(this::view).toList();
    }

    public ProviderAdminView get(String providerId) {
        return view(registry.require(providerId));
    }

    public ProviderAdminView update(String providerId, ProviderSettingsUpdate update, String actor) {
        LlmProvider provider = registry.require(providerId);
        settingsService.update(provider.descriptor(), update, actor);
        healthMonitor.check(providerId);
        return view(provider);
    }

    public ProviderAdminView reset(String providerId) {
        LlmProvider provider = registry.require(providerId);
        settingsService.reset(provider.descriptor());
        healthMonitor.check(providerId);
        return view(provider);
    }

    public ProviderAdminView checkHealth(String providerId) {
        LlmProvider provider = registry.require(providerId);
        healthMonitor.check(providerId);
        try {
            modelCatalog.models(provider, true);
        } catch (RuntimeException ignored) {
            // health already reflects the failure
        }
        return view(provider);
    }

    private ProviderAdminView view(LlmProvider provider) {
        ProviderDescriptor descriptor = provider.descriptor();
        ProviderHealth health = healthMonitor.current(descriptor.id());
        List<String> models = modelCatalog.cached(descriptor.id())
                .map(list -> list.stream().map(LlmModel::id).toList())
                .orElse(List.of());
        return new ProviderAdminView(descriptor, settingsService.effective(descriptor), health, models);
    }

    public record ProviderAdminView(ProviderDescriptor descriptor,
                                    EffectiveProviderSettings settings,
                                    ProviderHealth health,
                                    List<String> models) {
    }
}
