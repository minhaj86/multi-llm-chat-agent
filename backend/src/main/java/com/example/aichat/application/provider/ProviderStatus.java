package com.example.aichat.application.provider;

import com.example.aichat.domain.llm.HealthStatus;
import com.example.aichat.domain.llm.LlmCapability;
import com.example.aichat.domain.llm.ProviderKind;
import java.time.Instant;
import java.util.Set;

/**
 * Provider as presented to clients: identity, effective settings and last known health.
 *
 * @param available {@code true} when the provider is enabled and reachable
 */
public record ProviderStatus(String id,
                             String name,
                             String type,
                             ProviderKind kind,
                             boolean enabled,
                             boolean available,
                             HealthStatus status,
                             String statusMessage,
                             String defaultModel,
                             Set<LlmCapability> capabilities,
                             Instant lastCheckedAt,
                             Long latencyMs) {
}
