package com.example.aichat.application.audio;

/** Safe, provider-neutral failures; upstream bodies and credentials must not be included. */
public class AudioTranscriptionException extends RuntimeException {

    public enum Kind { DISABLED, INVALID_AUDIO, TOO_LARGE, BUSY, UPSTREAM_FAILURE, TIMEOUT }

    private final Kind kind;

    public AudioTranscriptionException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
