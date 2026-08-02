package com.example.aichat.application.auth;

import com.example.aichat.domain.user.User;
import java.time.Instant;

/**
 * Outbound port issuing signed access tokens.
 */
public interface TokenIssuer {

    IssuedToken issue(User user);

    record IssuedToken(String value, Instant expiresAt) {
    }
}
