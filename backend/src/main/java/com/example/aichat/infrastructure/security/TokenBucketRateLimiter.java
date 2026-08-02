package com.example.aichat.infrastructure.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.function.LongSupplier;

/**
 * In-memory token buckets keyed by an arbitrary string. Buckets of idle clients expire automatically.
 * (For multi-instance deployments, replace with a shared store such as Redis.)
 */
public class TokenBucketRateLimiter {

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofHours(1))
            .maximumSize(200_000)
            .build();
    private final LongSupplier nanoTime;

    public TokenBucketRateLimiter() {
        this(System::nanoTime);
    }

    TokenBucketRateLimiter(LongSupplier nanoTime) {
        this.nanoTime = nanoTime;
    }

    public Decision tryConsume(String key, int capacity, Duration refillPeriod) {
        Bucket bucket = buckets.get(key, k -> new Bucket(capacity, nanoTime.getAsLong()));
        return bucket.tryConsume(capacity, refillPeriod, nanoTime.getAsLong());
    }

    public record Decision(boolean allowed, long remaining, Duration retryAfter) {
    }

    private static final class Bucket {

        private double tokens;
        private long lastRefillNanos;

        Bucket(int capacity, long now) {
            this.tokens = capacity;
            this.lastRefillNanos = now;
        }

        synchronized Decision tryConsume(int capacity, Duration refillPeriod, long now) {
            double tokensPerNano = capacity / (double) refillPeriod.toNanos();
            tokens = Math.min(capacity, tokens + Math.max(0, now - lastRefillNanos) * tokensPerNano);
            lastRefillNanos = now;
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return new Decision(true, (long) Math.floor(tokens), Duration.ZERO);
            }
            long waitNanos = (long) Math.ceil((1.0 - tokens) / tokensPerNano);
            return new Decision(false, 0, Duration.ofNanos(waitNanos));
        }
    }
}
