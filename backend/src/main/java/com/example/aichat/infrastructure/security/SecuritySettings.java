package com.example.aichat.infrastructure.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Security settings ({@code app.security.*}).
 *
 * @param bcryptStrength BCrypt work factor (10-14 is typical; tests use a lower value)
 */
@ConfigurationProperties("app.security")
public record SecuritySettings(@DefaultValue Jwt jwt, @DefaultValue("12") int bcryptStrength) {

    /**
     * @param secret         HMAC secret (at least 32 bytes). When empty a random key is generated at startup, which
     *                       invalidates tokens on restart - fine for development, not for production.
     * @param issuer         {@code iss} claim
     * @param accessTokenTtl lifetime of access tokens
     */
    public record Jwt(String secret,
                      @DefaultValue("ai-workspace") String issuer,
                      @DefaultValue("PT12H") Duration accessTokenTtl) {

        @Override
        public String toString() {
            return "Jwt{issuer=" + issuer + ", accessTokenTtl=" + accessTokenTtl + ", secret=****}";
        }
    }
}
