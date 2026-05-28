package com.example.aichat.domain.provider;

import java.util.List;
import java.util.Optional;

/**
 * Persistence port for provider settings overrides.
 */
public interface ProviderSettingsRepository {

    List<ProviderSettingsOverride> findAll();

    Optional<ProviderSettingsOverride> findById(String providerId);

    ProviderSettingsOverride save(ProviderSettingsOverride override);

    void deleteById(String providerId);
}
