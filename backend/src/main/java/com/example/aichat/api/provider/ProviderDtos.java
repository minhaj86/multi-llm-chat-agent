package com.example.aichat.api.provider;

import com.example.aichat.application.provider.ModelInfo;
import com.example.aichat.application.provider.ProviderStatus;
import com.example.aichat.domain.llm.HealthStatus;
import com.example.aichat.domain.llm.LlmCapability;
import com.example.aichat.domain.llm.ProviderHealth;
import com.example.aichat.domain.llm.ProviderKind;
import java.time.Instant;
import java.util.Set;

/**
 * Provider API bodies. Never contain credentials.
 */
public final class ProviderDtos {

    private ProviderDtos() {
    }

    public record ProviderResponse(String id,
                                   String name,
                                   String type,
                                   ProviderKind kind,
                                   boolean enabled,
                                   boolean available,
                                   HealthStatus status,
                                   String statusMessage,
                                   String defaultModel,
                                   boolean defaultProvider,
                                   Set<LlmCapability> capabilities,
                                   Instant lastCheckedAt,
                                   Long latencyMs) {

        public static ProviderResponse of(ProviderStatus status, String defaultProviderId) {
            return new ProviderResponse(status.id(), status.name(), status.type(), status.kind(), status.enabled(),
                    status.available(), status.status(), status.statusMessage(), status.defaultModel(),
                    status.id().equals(defaultProviderId), status.capabilities(), status.lastCheckedAt(),
                    status.latencyMs());
        }
    }

    public record ModelResponse(String id, String name, String ownedBy, Integer contextWindow, boolean isDefault) {

        public static ModelResponse of(ModelInfo model) {
            return new ModelResponse(model.id(), model.name(), model.ownedBy(), model.contextWindow(),
                    model.isDefault());
        }
    }

    public record HealthResponse(String provider,
                                 HealthStatus status,
                                 boolean available,
                                 String message,
                                 Long latencyMs,
                                 Instant checkedAt) {

        public static HealthResponse of(ProviderHealth health) {
            return new HealthResponse(health.providerId(), health.status(), health.isAvailable(), health.message(),
                    health.latencyMs(), health.checkedAt());
        }
    }
}
