package com.example.aichat.application.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.aichat.domain.llm.HealthStatus;
import com.example.aichat.domain.llm.LlmModel;
import com.example.aichat.domain.llm.LlmProvider;
import com.example.aichat.domain.llm.ProviderDefaults;
import com.example.aichat.domain.llm.ProviderDescriptor;
import com.example.aichat.domain.llm.ProviderHealth;
import com.example.aichat.domain.llm.ProviderKind;
import com.example.aichat.domain.llm.exception.LlmErrorCode;
import com.example.aichat.domain.llm.exception.LlmProviderUnavailableException;
import com.example.aichat.domain.llm.exception.LlmResponseException;
import com.example.aichat.support.InMemoryProviderSettingsRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProviderHealthMonitorTest {

    @Mock
    private LlmProvider qwen;

    private ProviderSettingsService settings;
    private ProviderHealthMonitor monitor;

    @BeforeEach
    void setUp() {
        ProviderDescriptor descriptor = new ProviderDescriptor("qwen", "Qwen Local", "openai-compatible",
                ProviderKind.LOCAL, Set.of(), "http://qwen:8000/v1", null,
                new ProviderDefaults(true, "qwen3:8b", Duration.ofSeconds(30), null, null));
        lenient().when(qwen.descriptor()).thenReturn(descriptor);
        lenient().when(qwen.providerId()).thenReturn("qwen");
        settings = new ProviderSettingsService(new InMemoryProviderSettingsRepository(), Clock.systemUTC(),
                Duration.ZERO);
        LlmProviderRegistry registry = new LlmProviderRegistry(List.of(qwen), "qwen", settings);
        monitor = new ProviderHealthMonitor(registry, new ProviderModelCatalog(Clock.systemUTC(), Duration.ofMinutes(1)),
                Clock.systemUTC());
    }

    @Test
    void reportsUpWhenTheProbeSucceedsAndTheDefaultModelExists() {
        when(qwen.health()).thenReturn(ProviderHealth.up("qwen", 12, Instant.now()));
        when(qwen.availableModels()).thenReturn(List.of(LlmModel.of("qwen3:8b")));

        ProviderHealth health = monitor.check("qwen");

        assertThat(health.status()).isEqualTo(HealthStatus.UP);
        assertThat(monitor.current("qwen").isAvailable()).isTrue();
    }

    @Test
    void reportsDegradedWhenTheDefaultModelIsMissingOnTheServer() {
        when(qwen.health()).thenReturn(ProviderHealth.up("qwen", 12, Instant.now()));
        when(qwen.availableModels()).thenReturn(List.of(LlmModel.of("llama3.1:8b")));

        ProviderHealth health = monitor.check("qwen");

        assertThat(health.status()).isEqualTo(HealthStatus.DEGRADED);
        assertThat(health.message()).contains("qwen3:8b");
        assertThat(health.isAvailable()).isTrue();
    }

    @Test
    void probeFailuresMarkTheProviderDown() {
        when(qwen.health()).thenThrow(new IllegalStateException("connection refused"));

        assertThat(monitor.check("qwen").status()).isEqualTo(HealthStatus.DOWN);
        assertThat(monitor.current("qwen").message()).contains("connection refused");
    }

    @Test
    void disabledProvidersAreNotProbed() {
        settings.update(qwen.descriptor(), new ProviderSettingsUpdate(false, null, null, null, null), "admin");

        ProviderHealth health = monitor.check("qwen");

        assertThat(health.status()).isEqualTo(HealthStatus.UNKNOWN);
        verify(qwen, never()).health();
    }

    @Test
    void healthChecksNeverRunInference() {
        when(qwen.health()).thenReturn(ProviderHealth.up("qwen", 1, Instant.now()));
        when(qwen.availableModels()).thenReturn(List.of(LlmModel.of("qwen3:8b")));

        monitor.checkAll();

        verify(qwen, never()).chat(any());
        verify(qwen, never()).stream(any());
    }

    @Test
    void realTrafficUpdatesHealthPassively() {
        monitor.reportFailure("qwen", new LlmProviderUnavailableException("qwen", "Qwen Local is not reachable"));
        assertThat(monitor.current("qwen").status()).isEqualTo(HealthStatus.DOWN);

        monitor.reportFailure("qwen", new LlmResponseException("qwen", LlmErrorCode.INVALID_REQUEST, "bad prompt"));
        assertThat(monitor.current("qwen").status()).isEqualTo(HealthStatus.DOWN);

        monitor.reportSuccess("qwen");
        assertThat(monitor.current("qwen").status()).isEqualTo(HealthStatus.UP);
    }
}
