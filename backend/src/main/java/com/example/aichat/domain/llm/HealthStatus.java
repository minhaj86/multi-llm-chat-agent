package com.example.aichat.domain.llm;

/**
 * Health of a provider.
 */
public enum HealthStatus {
    /** Reachable and the default model is available. */
    UP,
    /** Reachable but not fully functional (e.g. the default model is missing). Still selectable. */
    DEGRADED,
    /** Not reachable, misconfigured or rejecting credentials. */
    DOWN,
    /** Not checked yet. */
    UNKNOWN;

    public boolean isAvailable() {
        return this == UP || this == DEGRADED;
    }
}
