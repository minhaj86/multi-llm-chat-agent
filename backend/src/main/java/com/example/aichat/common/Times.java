package com.example.aichat.common;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Time helpers. Timestamps are truncated to microseconds so that in-memory values equal their PostgreSQL
 * representation (important for ordering and summary boundaries).
 */
public final class Times {

    private Times() {
    }

    public static Instant now(Clock clock) {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    public static long elapsedMillis(long startNanos) {
        return Math.max(0, (System.nanoTime() - startNanos) / 1_000_000L);
    }
}
