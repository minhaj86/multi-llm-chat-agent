package com.example.aichat.support;

import com.example.aichat.domain.provider.ProviderSettingsOverride;
import com.example.aichat.domain.provider.ProviderSettingsRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory {@link ProviderSettingsRepository} for unit tests. */
public final class InMemoryProviderSettingsRepository implements ProviderSettingsRepository {

    private final Map<String, ProviderSettingsOverride> store = new ConcurrentHashMap<>();

    @Override
    public List<ProviderSettingsOverride> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public Optional<ProviderSettingsOverride> findById(String providerId) {
        return Optional.ofNullable(store.get(providerId));
    }

    @Override
    public ProviderSettingsOverride save(ProviderSettingsOverride override) {
        store.put(override.providerId(), override);
        return override;
    }

    @Override
    public void deleteById(String providerId) {
        store.remove(providerId);
    }
}
