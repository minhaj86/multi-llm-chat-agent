package com.example.aichat.rag.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 checksums used for duplicate detection.
 */
public final class Checksums {

    private Checksums() {
    }

    public static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /** Checksum of text that ignores line-ending and trailing-whitespace differences. */
    public static String sha256OfText(String text) {
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n').lines().map(String::stripTrailing)
                .reduce((a, b) -> a + "\n" + b).orElse("").strip();
        return sha256(normalized.getBytes(StandardCharsets.UTF_8));
    }
}
