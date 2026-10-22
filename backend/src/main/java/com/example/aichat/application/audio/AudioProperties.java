package com.example.aichat.application.audio;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Transcription is configured independently of the chat model. Secrets never appear in diagnostics or the API. */
@Validated
@ConfigurationProperties("audio.transcription")
public record AudioProperties(boolean enabled, @NotNull URI baseUrl, String apiKey,
                              @NotBlank String model, @NotNull Duration timeout,
                              @Min(1024) @Max(25_000_000) int maxUploadBytes) {

    public AudioProperties {
        if (timeout != null && (timeout.isNegative() || timeout.isZero())) {
            throw new IllegalArgumentException("Audio transcription timeout must be positive");
        }
        if (baseUrl != null && (!java.util.Set.of("http", "https").contains(baseUrl.getScheme())
                || baseUrl.getHost() == null || baseUrl.getUserInfo() != null)) {
            throw new IllegalArgumentException("Audio transcription requires an HTTP(S) base URL without credentials");
        }
    }

    @Override
    public String toString() {
        return "AudioProperties{enabled=" + enabled + ", maxUploadBytes=" + maxUploadBytes + "}";
    }
}
