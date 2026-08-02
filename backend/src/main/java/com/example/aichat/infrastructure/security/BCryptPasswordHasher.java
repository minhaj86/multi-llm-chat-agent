package com.example.aichat.infrastructure.security;

import com.example.aichat.application.auth.PasswordHasher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * BCrypt implementation of the {@link PasswordHasher} port.
 */
@Component
public class BCryptPasswordHasher implements PasswordHasher {

    private final PasswordEncoder encoder;

    public BCryptPasswordHasher(PasswordEncoder encoder) {
        this.encoder = encoder;
    }

    @Override
    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String hash) {
        if (rawPassword == null || hash == null) {
            return false;
        }
        try {
            return encoder.matches(rawPassword, hash);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
