package com.example.aichat.application.audio;

/** Outbound speech-to-text port. Implementations must not log or retain audio or retry billable requests. */
public interface AudioTranscriber {

    String transcribe(byte[] audio, String contentType, String filename);
}
