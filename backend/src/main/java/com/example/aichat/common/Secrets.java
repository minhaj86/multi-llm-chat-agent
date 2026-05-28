package com.example.aichat.common;

/**
 * Masks secrets for display and logging. Never returns more than the last four characters of a secret.
 */
public final class Secrets {

    private static final String MASK = "••••";

    private Secrets() {
    }

    public static String mask(String secret) {
        if (secret == null || secret.isBlank()) {
            return null;
        }
        String value = secret.strip();
        if (value.length() <= 12) {
            return MASK;
        }
        String prefix = value.startsWith("sk-") ? "sk-" : "";
        return prefix + MASK + value.substring(value.length() - 4);
    }
}
