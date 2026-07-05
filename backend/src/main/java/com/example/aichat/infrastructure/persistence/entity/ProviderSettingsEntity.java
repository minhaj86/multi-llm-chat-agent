package com.example.aichat.infrastructure.persistence.entity;

import com.example.aichat.domain.provider.ProviderSettingsOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "provider_settings")
public class ProviderSettingsEntity {

    @Id
    @Column(name = "provider_id", length = 64)
    private String providerId;
    private Boolean enabled;
    @Column(name = "default_model", length = 200)
    private String defaultModel;
    @Column(name = "timeout_seconds")
    private Integer timeoutSeconds;
    private Double temperature;
    @Column(name = "max_tokens")
    private Integer maxTokens;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    protected ProviderSettingsEntity() {
    }

    public static ProviderSettingsEntity from(ProviderSettingsOverride override) {
        ProviderSettingsEntity entity = new ProviderSettingsEntity();
        entity.providerId = override.providerId();
        entity.enabled = override.enabled();
        entity.defaultModel = override.defaultModel();
        entity.timeoutSeconds = override.timeoutSeconds();
        entity.temperature = override.temperature();
        entity.maxTokens = override.maxTokens();
        entity.updatedAt = override.updatedAt();
        entity.updatedBy = override.updatedBy();
        return entity;
    }

    public ProviderSettingsOverride toDomain() {
        return new ProviderSettingsOverride(providerId, enabled, defaultModel, timeoutSeconds, temperature, maxTokens,
                updatedAt, updatedBy);
    }
}
