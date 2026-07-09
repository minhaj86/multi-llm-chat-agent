package com.example.aichat.application.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.aichat.domain.llm.LlmUsage;
import com.example.aichat.domain.llm.ProviderDefaults;
import com.example.aichat.domain.llm.ProviderDescriptor;
import com.example.aichat.domain.llm.ProviderKind;
import com.example.aichat.domain.usage.ExecutionPurpose;
import com.example.aichat.domain.usage.ExecutionStatus;
import com.example.aichat.domain.usage.LlmExecution;
import com.example.aichat.domain.usage.LlmExecutionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UsageRecorderTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-12T10:15:30.123456789Z"), ZoneOffset.UTC);

    @Mock
    private LlmExecutionRepository repository;
    @Mock
    private CostEstimator costEstimator;

    private static ExecutionRecord record(ProviderKind kind) {
        ProviderDescriptor provider = new ProviderDescriptor("openai", "OpenAI", "openai", kind, Set.of(), "http://x",
                null, new ProviderDefaults(true, "gpt-5-mini", null, null, null));
        return new ExecutionRecord(provider, "gpt-5-mini", ExecutionPurpose.CHAT, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), LlmUsage.of(1000, 500), 1234, 210L, ExecutionStatus.SUCCESS, null);
    }

    @Test
    void recordsTokensLatencyAndEstimatedCost() {
        when(costEstimator.estimate(any(), any(), any())).thenReturn(new CostEstimate(new BigDecimal("0.00125"), false));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        LlmExecution execution = new UsageRecorder(repository, costEstimator, CLOCK).record(record(ProviderKind.CLOUD));

        ArgumentCaptor<LlmExecution> saved = ArgumentCaptor.forClass(LlmExecution.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue()).isEqualTo(execution);
        assertThat(execution.inputTokens()).isEqualTo(1000);
        assertThat(execution.outputTokens()).isEqualTo(500);
        assertThat(execution.latencyMs()).isEqualTo(1234);
        assertThat(execution.timeToFirstTokenMs()).isEqualTo(210);
        assertThat(execution.estimatedCost()).isEqualByComparingTo("0.00125");
        assertThat(execution.createdAt()).isEqualTo(Instant.parse("2026-10-12T10:15:30.123456Z"));
    }

    @Test
    void usageTrackingFailuresNeverBreakTheUserFacingRequest() {
        when(costEstimator.estimate(any(), any(), any())).thenThrow(new IllegalStateException("pricing broken"));
        when(repository.save(any())).thenThrow(new IllegalStateException("database down"));

        LlmExecution execution = new UsageRecorder(repository, costEstimator, CLOCK).record(record(ProviderKind.LOCAL));

        assertThat(execution.status()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(execution.estimatedCost()).isEqualByComparingTo("0");
    }
}
