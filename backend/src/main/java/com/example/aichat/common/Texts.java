package com.example.aichat.common;

/**
 * Small string helpers used across layers.
 */
public final class Texts {

    private Texts() {
    }

    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public static boolean hasText(String value) {
        return !isBlank(value);
    }

    public static String blankToNull(String value) {
        return isBlank(value) ? null : value.strip();
    }

    public static String firstNonBlank(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value;
            }
        }
        return null;
    }

    /** Collapses all whitespace (including newlines) into single spaces. */
    public static String singleLine(String value) {
        return value == null ? "" : value.strip().replaceAll("\\s+", " ");
    }

    /** Truncates to {@code maxLength} characters, appending an ellipsis when shortened. */
    public static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        if (maxLength <= 1) {
            return value.substring(0, Math.max(0, maxLength));
        }
        return value.substring(0, maxLength - 1).stripTrailing() + "…";
    }
}
