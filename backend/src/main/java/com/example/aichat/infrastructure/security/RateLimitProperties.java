package com.example.aichat.infrastructure.security;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Token-bucket rate limits per client IP or authenticated user ({@code app.rate-limit.*}).
 */
@ConfigurationProperties("app.rate-limit")
public record RateLimitProperties(@DefaultValue("true") boolean enabled, List<Rule> rules) {

    public RateLimitProperties {
        rules = rules == null ? List.of() : List.copyOf(rules);
    }

    /**
     * @param paths        Ant-style path patterns (e.g. {@code /api/chat/**})
     * @param methods      HTTP methods the rule applies to (empty = all)
     * @param key          bucket key: {@code IP} or {@code USER} (falls back to IP when unauthenticated)
     * @param capacity     burst size
     * @param refillPeriod time to refill the full capacity
     */
    public record Rule(String name,
                       List<String> paths,
                       List<String> methods,
                       @DefaultValue("USER") KeyType key,
                       int capacity,
                       Duration refillPeriod) {

        public Rule {
            paths = paths == null ? List.of() : List.copyOf(paths);
            methods = methods == null ? List.of() : List.copyOf(methods);
            if (capacity < 1) {
                throw new IllegalArgumentException("app.rate-limit rule '" + name + "': capacity must be positive");
            }
            if (refillPeriod == null || refillPeriod.isZero() || refillPeriod.isNegative()) {
                throw new IllegalArgumentException("app.rate-limit rule '" + name + "': refill-period must be positive");
            }
        }
    }

    public enum KeyType {
        IP,
        USER
    }
}
