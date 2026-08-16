package com.example.aichat.application.usage;

import com.example.aichat.domain.usage.LlmExecutionRepository.DailyUsage;
import com.example.aichat.domain.usage.LlmExecutionRepository.ProviderUsage;
import com.example.aichat.domain.usage.LlmExecutionRepository.UsageTotals;
import java.time.Instant;
import java.util.List;

/**
 * Administrator usage dashboard.
 */
public record UsageDashboard(Instant from,
                             Instant to,
                             String timeZone,
                             UsageTotals today,
                             UsageTotals period,
                             List<ProviderUsage> byProvider,
                             List<DailyUsage> daily) {
}
