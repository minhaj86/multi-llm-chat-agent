package com.example.aichat.domain.conversation;

/** Saved audio-chat preferences. Device identifiers stay in the browser; null tokens use provider defaults. */
public record AudioSettings(Source source, Integer maxOutputTokens, int captureSeconds, boolean autoSend) {

    public enum Source { MICROPHONE, SPEAKER }

    public static final AudioSettings DEFAULTS = new AudioSettings(Source.MICROPHONE, null, 30, false);

    public AudioSettings {
        source = source == null ? Source.MICROPHONE : source;
        if (maxOutputTokens != null && (maxOutputTokens < 1 || maxOutputTokens > 128_000)) {
            throw new IllegalArgumentException("Maximum output tokens must be between 1 and 128000");
        }
        if (captureSeconds < 5 || captureSeconds > 120) {
            throw new IllegalArgumentException("Capture duration must be between 5 and 120 seconds");
        }
    }
}
