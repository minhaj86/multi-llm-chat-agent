package com.example.aichat.domain.llm;

import java.time.Duration;

/**
 * Statically configured defaults of a provider. Administrators can override the tunable values at runtime.
 */
public record ProviderDefaults(boolean enabled,
                               String defaultModel,
                               Duration timeout,
                               Double temperature,
                               Integer maxOutputTokens) {
}
