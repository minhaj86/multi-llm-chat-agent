package com.example.aichat.domain.usage;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persistence port for LLM execution records and usage analytics.
 */
public interface LlmExecutionRepository {

    LlmExecution save(LlmExecution execution);

    /** Latest execution per message, used to show latency/tokens next to assistant messages. */
    Map<UUID, LlmExecution> findLatestByMessageIds(Collection<UUID> messageIds);

    UsageTotals totals(Instant from, Instant to);

    List<ProviderUsage> usageByProvider(Instant from, Instant to);

    List<DailyUsage> dailyUsage(Instant from, Instant to, String timeZone);

    record UsageTotals(long requests,
                       long errors,
                       long cancelled,
                       long inputTokens,
                       long outputTokens,
                       BigDecimal cloudCost,
                       double averageLatencyMs,
                       long localRequests,
                       long cloudRequests) {
    }

    record ProviderUsage(String provider,
                         String providerKind,
                         long requests,
                         long errors,
                         long inputTokens,
                         long outputTokens,
                         BigDecimal cost,
                         double averageLatencyMs) {
    }

    record DailyUsage(LocalDate day,
                      String provider,
                      long requests,
                      long errors,
                      long inputTokens,
                      long outputTokens,
                      BigDecimal cost) {
    }
}
