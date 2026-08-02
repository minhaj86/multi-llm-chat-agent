package com.example.aichat.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class TokenBucketRateLimiterTest {

    @Test
    void allowsBurstThenRejectsUntilRefilled() {
        AtomicLong clock = new AtomicLong();
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(clock::get);

        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryConsume("user-1", 3, Duration.ofMinutes(1)).allowed()).isTrue();
        }
        TokenBucketRateLimiter.Decision rejected = limiter.tryConsume("user-1", 3, Duration.ofMinutes(1));
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfter()).isBetween(Duration.ofSeconds(19), Duration.ofSeconds(21));

        clock.addAndGet(Duration.ofSeconds(20).toNanos());
        assertThat(limiter.tryConsume("user-1", 3, Duration.ofMinutes(1)).allowed()).isTrue();
    }

    @Test
    void bucketsAreIsolatedPerKey() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(() -> 0L);

        assertThat(limiter.tryConsume("a", 1, Duration.ofMinutes(1)).allowed()).isTrue();
        assertThat(limiter.tryConsume("a", 1, Duration.ofMinutes(1)).allowed()).isFalse();
        assertThat(limiter.tryConsume("b", 1, Duration.ofMinutes(1)).allowed()).isTrue();
    }
}
