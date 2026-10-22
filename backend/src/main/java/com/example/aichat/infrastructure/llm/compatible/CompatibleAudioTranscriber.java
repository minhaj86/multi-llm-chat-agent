package com.example.aichat.infrastructure.llm.compatible;

import static com.example.aichat.application.audio.AudioTranscriptionException.Kind.*;

import com.example.aichat.application.audio.AudioProperties;
import com.example.aichat.application.audio.AudioTranscriber;
import com.example.aichat.application.audio.AudioTranscriptionException;
import java.util.concurrent.TimeoutException;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/** Adapter for the OpenAI-compatible multipart audio/transcriptions protocol, including local STT servers. */
@Component
public class CompatibleAudioTranscriber implements AudioTranscriber {

    private final AudioProperties properties;
    private final WebClient client;

    public CompatibleAudioTranscriber(AudioProperties properties, WebClient.Builder builder) {
        this.properties = properties;
        WebClient.Builder configured = builder.clone().baseUrl(properties.baseUrl().toString().replaceAll("/+$", ""));
        if (properties.apiKey() != null && !properties.apiKey().isBlank()) {
            configured.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey());
        }
        this.client = configured.build();
    }

    @Override
    public String transcribe(byte[] audio, String contentType, String filename) {
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("file", new ByteArrayResource(audio)).filename(filename)
                .contentType(MediaType.parseMediaType(contentType));
        body.part("model", properties.model());
        body.part("response_format", "json");
        // No retry: an interrupted request may already have been processed and billed.
        return client.post().uri("/audio/transcriptions")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(body.build()))
                .retrieve()
                .onStatus(status -> status.isError(), response -> response.releaseBody().thenReturn(
                        new AudioTranscriptionException(UPSTREAM_FAILURE,
                                "The transcription service rejected the recording. Check its configuration or try a shorter capture.")))
                .bodyToMono(Transcript.class)
                .flatMap(result -> result.text() == null ? Mono.error(new AudioTranscriptionException(UPSTREAM_FAILURE,
                        "The transcription service returned an invalid response.")) : Mono.just(result.text().strip()))
                .switchIfEmpty(Mono.error(new AudioTranscriptionException(UPSTREAM_FAILURE,
                        "The transcription service returned an empty response.")))
                .timeout(properties.timeout())
                .onErrorMap(error -> error instanceof AudioTranscriptionException ? error
                        : error instanceof TimeoutException
                        ? new AudioTranscriptionException(TIMEOUT, "Transcription timed out. Try a shorter recording.")
                        : new AudioTranscriptionException(UPSTREAM_FAILURE,
                                "The transcription service is unavailable or returned an invalid response."))
                .block();
    }

    private record Transcript(String text) {
    }
}
