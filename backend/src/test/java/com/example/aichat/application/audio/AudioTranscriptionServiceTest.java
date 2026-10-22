package com.example.aichat.application.audio;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AudioTranscriptionServiceTest {

    private AudioTranscriptionService service(AudioTranscriber transcriber, boolean enabled) {
        return new AudioTranscriptionService(transcriber, new AudioProperties(enabled,
                URI.create("http://localhost/v1"), null, "fake", Duration.ofSeconds(1), 1024));
    }

    @Test
    void rejectsDisabledEmptyOversizeAndUnsupportedUploadsBeforeCallingProvider() {
        AudioTranscriber transcriber = mock(AudioTranscriber.class);
        assertThatThrownBy(() -> service(transcriber, false).validateUpload(10, "audio/webm"))
                .isInstanceOfSatisfying(AudioTranscriptionException.class,
                        e -> assertThat(e.kind()).isEqualTo(AudioTranscriptionException.Kind.DISABLED));
        AudioTranscriptionService enabled = service(transcriber, true);
        assertThatThrownBy(() -> enabled.validateUpload(0, "audio/webm")).isInstanceOf(AudioTranscriptionException.class);
        assertThatThrownBy(() -> enabled.validateUpload(1025, "audio/webm"))
                .isInstanceOfSatisfying(AudioTranscriptionException.class,
                        e -> assertThat(e.kind()).isEqualTo(AudioTranscriptionException.Kind.TOO_LARGE));
        assertThatThrownBy(() -> enabled.validateUpload(10, "video/webm"))
                .isInstanceOf(AudioTranscriptionException.class);
        verifyNoInteractions(transcriber);
    }

    @Test
    void stripsCodecParametersAndReleasesConcurrencySlotAfterFailure() {
        AudioTranscriber transcriber = mock(AudioTranscriber.class);
        when(transcriber.transcribe(any(), eq("audio/webm"), eq("recording.webm")))
                .thenThrow(new AudioTranscriptionException(AudioTranscriptionException.Kind.TIMEOUT, "Timed out"))
                .thenReturn("Hello");
        AudioTranscriptionService enabled = service(transcriber, true);
        UUID user = UUID.randomUUID();
        assertThatThrownBy(() -> enabled.transcribe(user, new byte[]{1}, "audio/webm;codecs=opus"))
                .isInstanceOf(AudioTranscriptionException.class);
        assertThat(enabled.transcribe(user, new byte[]{1}, "audio/webm;codecs=opus")).isEqualTo("Hello");
    }

    @Test
    void rejectsOverlappingRequestsFromTheSameUser() {
        AudioTranscriber transcriber = mock(AudioTranscriber.class);
        AudioTranscriptionService enabled = service(transcriber, true);
        UUID user = UUID.randomUUID();
        when(transcriber.transcribe(any(), any(), any())).thenAnswer(invocation -> {
            assertThatThrownBy(() -> enabled.transcribe(user, new byte[]{1}, "audio/webm"))
                    .isInstanceOfSatisfying(AudioTranscriptionException.class,
                            e -> assertThat(e.kind()).isEqualTo(AudioTranscriptionException.Kind.BUSY));
            return "Hello";
        });
        assertThat(enabled.transcribe(user, new byte[]{1}, "audio/webm")).isEqualTo("Hello");
        verify(transcriber, times(1)).transcribe(any(), any(), any());
    }
}
