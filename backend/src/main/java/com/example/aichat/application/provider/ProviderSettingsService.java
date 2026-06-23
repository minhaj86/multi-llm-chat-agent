package com.example.aichat.application.provider;

import com.example.aichat.application.common.ValidationException;
import com.example.aichat.common.Texts;
import com.example.aichat.common.Times;
import com.example.aichat.domain.llm.ProviderDefaults;
import com.example.aichat.domain.llm.ProviderDescriptor;
import com.example.aichat.domain.provider.ProviderSettingsOverride;
import com.example.aichat.domain.provider.ProviderSettingsRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Merges configured provider defaults with administrator overrides stored in the database. Overrides are cached
 * and periodically reloaded so that changes made on another instance become visible without a restart.
 */
@Service
public class ProviderSettingsService {

    private final ProviderSettingsRepository repository;
    private final Clock clock;
    private final Duration reloadInterval;

    private volatile Map<String, ProviderSettingsOverride> overrides;
    private volatile Instant loadedAt = Instant.EPOCH;

    public ProviderSettingsService(ProviderSettingsRepository repository,
                                   Clock clock,
                                   @Value("${llm.settings-reload-interval:PT15S}") Duration reloadInterval) {
        this.repository = repository;
        this.clock = clock;
        this.reloadInterval = reloadInterval;
    }

    public EffectiveProviderSettings effective(ProviderDescriptor descriptor) {
        ProviderDefaults defaults = descriptor.defaults();
        ProviderSettingsOverride override = overrides().get(descriptor.id());
        if (override == null) {
            return new EffectiveProviderSettings(descriptor.id(), defaults.enabled(), defaults.defaultModel(),
                    defaults.timeout(), defaults.temperature(), defaults.maxOutputTokens(), false, null, null);
        }
        return new EffectiveProviderSettings(
                descriptor.id(),
                override.enabled() != null ? override.enabled() : defaults.enabled(),
                Texts.hasText(override.defaultModel()) ? override.defaultModel() : defaults.defaultModel(),
                override.timeoutSeconds() != null ? Duration.ofSeconds(override.timeoutSeconds()) : defaults.timeout(),
                override.temperature() != null ? override.temperature() : defaults.temperature(),
                override.maxTokens() != null ? override.maxTokens() : defaults.maxOutputTokens(),
                true,
                override.updatedAt(),
                override.updatedBy());
    }

    public synchronized EffectiveProviderSettings update(ProviderDescriptor descriptor,
                                                         ProviderSettingsUpdate update,
                                                         String actor) {
        validate(update);
        ProviderSettingsOverride override = new ProviderSettingsOverride(
                descriptor.id(),
                update.enabled(),
                Texts.blankToNull(update.defaultModel()),
                update.timeoutSeconds(),
                update.temperature(),
                update.maxTokens(),
                Times.now(clock),
                actor);
        repository.save(override);
        reload();
        return effective(descriptor);
    }

    public synchronized EffectiveProviderSettings reset(ProviderDescriptor descriptor) {
        repository.deleteById(descriptor.id());
        reload();
        return effective(descriptor);
    }

    private Map<String, ProviderSettingsOverride> overrides() {
        Map<String, ProviderSettingsOverride> current = overrides;
        if (current == null || clock.instant().isAfter(loadedAt.plus(reloadInterval))) {
            synchronized (this) {
                if (overrides == null || clock.instant().isAfter(loadedAt.plus(reloadInterval))) {
                    reload();
                }
                current = overrides;
            }
        }
        return current;
    }

    private void reload() {
        overrides = new ConcurrentHashMap<>(repository.findAll().stream()
                .collect(Collectors.toMap(ProviderSettingsOverride::providerId, Function.identity())));
        loadedAt = clock.instant();
    }

    private static void validate(ProviderSettingsUpdate update) {
        if (update.timeoutSeconds() != null && (update.timeoutSeconds() < 1 || update.timeoutSeconds() > 3600)) {
            throw new ValidationException("INVALID_TIMEOUT", "Timeout must be between 1 and 3600 seconds");
        }
        if (update.temperature() != null && (update.temperature() < 0.0 || update.temperature() > 2.0)) {
            throw new ValidationException("INVALID_TEMPERATURE", "Temperature must be between 0.0 and 2.0");
        }
        if (update.maxTokens() != null && (update.maxTokens() < 1 || update.maxTokens() > 1_000_000)) {
            throw new ValidationException("INVALID_MAX_TOKENS", "Max tokens must be between 1 and 1000000");
        }
        if (update.defaultModel() != null && update.defaultModel().length() > 200) {
            throw new ValidationException("INVALID_MODEL", "Model name must not exceed 200 characters");
        }
    }
}
