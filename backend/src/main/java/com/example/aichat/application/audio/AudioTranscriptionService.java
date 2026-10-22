package com.example.aichat.application.audio;

import static com.example.aichat.application.audio.AudioTranscriptionException.Kind.*;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/** Validates bounded uploads and allows only one transcription per authenticated user at a time. */
@Service
public class AudioTranscriptionService {

    private static final Map<String, String> FORMATS = Map.of(
            "audio/webm", "webm", "audio/mp4", "m4a", "audio/ogg", "ogg",
            "audio/wav", "wav", "audio/x-wav", "wav", "audio/mpeg", "mp3");

    private final AudioTranscriber transcriber;
    private final AudioProperties properties;
    private final Set<UUID> active = ConcurrentHashMap.newKeySet();

    public AudioTranscriptionService(AudioTranscriber transcriber, AudioProperties properties) {
        this.transcriber = transcriber;
        this.properties = properties;
    }

    public AudioProperties configuration() {
        return properties;
    }

    public void validateUpload(long size, String contentType) {
        if (!properties.enabled()) {
            throw new AudioTranscriptionException(DISABLED,
                    "Audio transcription is not configured. Ask an administrator to enable it.");
        }
        if (size <= 0) {
            throw new AudioTranscriptionException(INVALID_AUDIO, "The recording is empty. Record some audio first.");
        }
        if (size > properties.maxUploadBytes()) {
            throw new AudioTranscriptionException(TOO_LARGE, "The recording is too large. Use a shorter capture.");
        }
        if (!FORMATS.containsKey(normalize(contentType))) {
            throw new AudioTranscriptionException(INVALID_AUDIO, "Unsupported recording format.");
        }
    }

    public String transcribe(UUID userId, byte[] audio, String contentType) {
        validateUpload(audio.length, contentType);
        if (!active.add(userId)) {
            throw new AudioTranscriptionException(BUSY, "A recording is already being transcribed. Please wait.");
        }
        try {
            String format = normalize(contentType);
            return transcriber.transcribe(audio, format, "recording." + FORMATS.get(format));
        } finally {
            active.remove(userId);
        }
    }

    private static String normalize(String contentType) {
        return contentType == null ? "" : contentType.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
    }
}
