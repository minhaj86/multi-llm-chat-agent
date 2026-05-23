package com.example.aichat.domain.llm;

import java.time.Instant;

/**
 * Result of a (cheap) provider health probe.
 */
public record ProviderHealth(String providerId,
                             HealthStatus status,
                             String message,
                             Long latencyMs,
                             Instant checkedAt) {

    public static ProviderHealth up(String providerId, long latencyMs, Instant checkedAt) {
        return new ProviderHealth(providerId, HealthStatus.UP, null, latencyMs, checkedAt);
    }

    public static ProviderHealth degraded(String providerId, String message, long latencyMs, Instant checkedAt) {
        return new ProviderHealth(providerId, HealthStatus.DEGRADED, message, latencyMs, checkedAt);
    }

    public static ProviderHealth down(String providerId, String message, Long latencyMs, Instant checkedAt) {
        return new ProviderHealth(providerId, HealthStatus.DOWN, message, latencyMs, checkedAt);
    }

    public static ProviderHealth unknown(String providerId) {
        return new ProviderHealth(providerId, HealthStatus.UNKNOWN, "Not checked yet", null, null);
    }

    public boolean isAvailable() {
        return status.isAvailable();
    }
}
