package com.example.aichat.application.usage;

import com.example.aichat.application.common.ValidationException;
import com.example.aichat.domain.usage.LlmExecutionRepository;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Service;

/**
 * Aggregated usage statistics for administrators.
 */
@Service
public class UsageDashboardService {

    private final LlmExecutionRepository repository;
    private final Clock clock;

    public UsageDashboardService(LlmExecutionRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public UsageDashboard dashboard(int days, String timeZone) {
        if (days < 1 || days > 366) {
            throw new ValidationException("INVALID_RANGE", "days must be between 1 and 366");
        }
        ZoneId zone = zone(timeZone);
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, zone);
        Instant startOfToday = today.atStartOfDay(zone).toInstant();
        Instant from = today.minusDays(days - 1L).atStartOfDay(zone).toInstant();
        return new UsageDashboard(
                from,
                now,
                zone.getId(),
                repository.totals(startOfToday, now),
                repository.totals(from, now),
                repository.usageByProvider(from, now),
                repository.dailyUsage(from, now, zone.getId()));
    }

    private static ZoneId zone(String timeZone) {
        if (timeZone == null || timeZone.isBlank()) {
            return ZoneId.of("UTC");
        }
        try {
            return ZoneId.of(timeZone);
        } catch (DateTimeException e) {
            throw new ValidationException("INVALID_TIME_ZONE", "Unknown time zone: " + timeZone);
        }
    }
}
