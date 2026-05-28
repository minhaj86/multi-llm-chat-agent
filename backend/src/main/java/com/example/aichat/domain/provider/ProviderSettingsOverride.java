package com.example.aichat.domain.provider;

import java.time.Instant;

/**
 * Runtime overrides of a provider's configured defaults, editable by administrators without a restart.
 * {@code null} fields mean "use the configured default".
 */
public record ProviderSettingsOverride(String providerId,
                                       Boolean enabled,
                                       String defaultModel,
                                       Integer timeoutSeconds,
                                       Double temperature,
                                       Integer maxTokens,
                                       Instant updatedAt,
                                       String updatedBy) {
}
