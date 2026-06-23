package com.example.aichat.application.provider;

import java.time.Duration;
import java.time.Instant;

/**
 * Provider settings after applying administrator overrides on top of the configured defaults.
 */
public record EffectiveProviderSettings(String providerId,
                                        boolean enabled,
                                        String defaultModel,
                                        Duration timeout,
                                        Double temperature,
                                        Integer maxOutputTokens,
                                        boolean overridden,
                                        Instant updatedAt,
                                        String updatedBy) {
}
