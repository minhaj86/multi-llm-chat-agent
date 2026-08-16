package com.example.aichat.api.admin;

import com.example.aichat.api.auth.AuthDtos.UserProfile;
import com.example.aichat.application.provider.EffectiveProviderSettings;
import com.example.aichat.application.provider.ProviderAdministrationService.ProviderAdminView;
import com.example.aichat.application.usage.UsageDashboard;
import com.example.aichat.domain.llm.HealthStatus;
import com.example.aichat.domain.llm.LlmCapability;
import com.example.aichat.domain.llm.ProviderDefaults;
import com.example.aichat.domain.llm.ProviderDescriptor;
import com.example.aichat.domain.llm.ProviderHealth;
import com.example.aichat.domain.llm.ProviderKind;
import com.example.aichat.domain.usage.LlmExecutionRepository.DailyUsage;
import com.example.aichat.domain.usage.LlmExecutionRepository.ProviderUsage;
import com.example.aichat.domain.usage.LlmExecutionRepository.UsageTotals;
import com.example.aichat.domain.user.User;
import com.example.aichat.domain.user.UserRole;
import com.example.aichat.domain.user.UserStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Administration API bodies. Provider credentials are only ever exposed as a masked hint.
 */
public final class AdminDtos {

    private AdminDtos() {
    }

    public record CreateUserRequest(@NotBlank @Size(min = 3, max = 32) String username,
                                    @NotBlank @Email @Size(max = 255) String email,
                                    @NotBlank @Size(min = 8, max = 128) String password,
                                    @Size(max = 128) String displayName,
                                    UserRole role) {

        @Override
        public String toString() {
            return "CreateUserRequest{username=" + username + ", email=" + email + ", role=" + role
                    + ", password=****}";
        }
    }

    public record ChangeRoleRequest(@NotNull UserRole role) {
    }

    public record AdminUserResponse(UUID id,
                                    String username,
                                    String email,
                                    String displayName,
                                    UserRole role,
                                    UserStatus status,
                                    int failedLoginAttempts,
                                    Instant lockedUntil,
                                    Instant lastLoginAt,
                                    Instant createdAt) {

        public static AdminUserResponse of(User user) {
            return new AdminUserResponse(user.id(), user.username(), user.email(), user.displayName(), user.role(),
                    user.status(), user.failedLoginAttempts(), user.lockedUntil(), user.lastLoginAt(),
                    user.createdAt());
        }

        public UserProfile profile() {
            return new UserProfile(id, username, email, displayName, role, status, createdAt, lastLoginAt);
        }
    }

    /**
     * Tunable provider settings; {@code null} values restore the configured default.
     */
    public record ProviderSettingsRequest(Boolean enabled,
                                          @Size(max = 200) String defaultModel,
                                          @Min(1) @Max(3600) Integer timeoutSeconds,
                                          @DecimalMin("0.0") @DecimalMax("2.0") Double temperature,
                                          @Min(1) @Max(1_000_000) Integer maxTokens) {
    }

    public record AdminProviderResponse(String id,
                                        String name,
                                        String type,
                                        ProviderKind kind,
                                        boolean enabled,
                                        String baseUrl,
                                        boolean apiKeyConfigured,
                                        String apiKeyHint,
                                        String defaultModel,
                                        Integer timeoutSeconds,
                                        Double temperature,
                                        Integer maxTokens,
                                        Set<LlmCapability> capabilities,
                                        HealthView health,
                                        List<String> availableModels,
                                        ConfiguredDefaults configuredDefaults,
                                        boolean overridden,
                                        Instant updatedAt,
                                        String updatedBy) {

        public static AdminProviderResponse of(ProviderAdminView view) {
            ProviderDescriptor descriptor = view.descriptor();
            EffectiveProviderSettings settings = view.settings();
            ProviderDefaults defaults = descriptor.defaults();
            return new AdminProviderResponse(descriptor.id(), descriptor.displayName(), descriptor.type(),
                    descriptor.kind(), settings.enabled(), descriptor.endpoint(), descriptor.maskedApiKey() != null,
                    descriptor.maskedApiKey(), settings.defaultModel(), seconds(settings.timeout()),
                    settings.temperature(), settings.maxOutputTokens(), descriptor.capabilities(),
                    HealthView.of(view.health()), view.models(),
                    new ConfiguredDefaults(defaults.enabled(), defaults.defaultModel(), seconds(defaults.timeout()),
                            defaults.temperature(), defaults.maxOutputTokens()),
                    settings.overridden(), settings.updatedAt(), settings.updatedBy());
        }

        private static Integer seconds(Duration duration) {
            return duration == null ? null : (int) duration.toSeconds();
        }
    }

    public record ConfiguredDefaults(boolean enabled,
                                     String defaultModel,
                                     Integer timeoutSeconds,
                                     Double temperature,
                                     Integer maxTokens) {
    }

    public record HealthView(HealthStatus status, boolean available, String message, Long latencyMs,
                             Instant checkedAt) {

        static HealthView of(ProviderHealth health) {
            return new HealthView(health.status(), health.isAvailable(), health.message(), health.latencyMs(),
                    health.checkedAt());
        }
    }

    public record UsageResponse(Instant from,
                                Instant to,
                                String timeZone,
                                Totals today,
                                Totals period,
                                List<ProviderUsage> byProvider,
                                List<DailyUsage> daily) {

        public static UsageResponse of(UsageDashboard dashboard) {
            return new UsageResponse(dashboard.from(), dashboard.to(), dashboard.timeZone(),
                    Totals.of(dashboard.today()), Totals.of(dashboard.period()), dashboard.byProvider(),
                    dashboard.daily());
        }
    }

    public record Totals(long requests,
                         long errors,
                         long cancelled,
                         double errorRate,
                         long inputTokens,
                         long outputTokens,
                         BigDecimal cloudCost,
                         double averageLatencyMs,
                         long localRequests,
                         long cloudRequests) {

        static Totals of(UsageTotals totals) {
            double errorRate = totals.requests() == 0 ? 0.0 : (double) totals.errors() / totals.requests();
            return new Totals(totals.requests(), totals.errors(), totals.cancelled(), errorRate,
                    totals.inputTokens(), totals.outputTokens(), totals.cloudCost(), totals.averageLatencyMs(),
                    totals.localRequests(), totals.cloudRequests());
        }
    }
}
