package com.example.aichat.infrastructure.persistence.adapter;

import com.example.aichat.domain.provider.ProviderSettingsOverride;
import com.example.aichat.domain.provider.ProviderSettingsRepository;
import com.example.aichat.infrastructure.persistence.entity.ProviderSettingsEntity;
import com.example.aichat.infrastructure.persistence.jpa.ProviderSettingsJpaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class JpaProviderSettingsRepositoryAdapter implements ProviderSettingsRepository {

    private final ProviderSettingsJpaRepository jpa;

    public JpaProviderSettingsRepositoryAdapter(ProviderSettingsJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<ProviderSettingsOverride> findAll() {
        return jpa.findAll().stream().map(ProviderSettingsEntity::toDomain).toList();
    }

    @Override
    public Optional<ProviderSettingsOverride> findById(String providerId) {
        return jpa.findById(providerId).map(ProviderSettingsEntity::toDomain);
    }

    @Override
    @Transactional
    public ProviderSettingsOverride save(ProviderSettingsOverride override) {
        return jpa.save(ProviderSettingsEntity.from(override)).toDomain();
    }

    @Override
    @Transactional
    public void deleteById(String providerId) {
        if (jpa.existsById(providerId)) {
            jpa.deleteById(providerId);
        }
    }
}
