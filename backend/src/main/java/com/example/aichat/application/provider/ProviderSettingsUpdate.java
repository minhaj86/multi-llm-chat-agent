package com.example.aichat.application.provider;

/**
 * Administrator change of the tunable provider settings. {@code null} restores the configured default.
 */
public record ProviderSettingsUpdate(Boolean enabled,
                                     String defaultModel,
                                     Integer timeoutSeconds,
                                     Double temperature,
                                     Integer maxTokens) {
}
