package com.example.aichat.application.auth;

/**
 * Outbound port for one-way password hashing.
 */
public interface PasswordHasher {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String hash);
}
