package com.example.aichat.infrastructure.llm.compatible;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.*;

import com.example.aichat.application.audio.AudioProperties;
import com.example.aichat.application.audio.AudioTranscriptionException;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.reactive.function.client.WebClient;

class CompatibleAudioTranscriberTest {

    @RegisterExtension
    static WireMockExtension server = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    private CompatibleAudioTranscriber adapter(String key, Duration timeout) {
        return new CompatibleAudioTranscriber(new AudioProperties(true, URI.create(server.baseUrl() + "/v1/"),
                key, "test-transcriber", timeout, 100_000), WebClient.builder());
    }

    @Test
    void forwardsMultipartToConfiguredServiceWithBackendCredentials() {
        server.stubFor(post("/v1/audio/transcriptions").willReturn(okJson("{\"text\":\"  Explain this meeting  \"}")));
        assertThat(adapter("secret-test", Duration.ofSeconds(5))
                .transcribe(new byte[]{1, 2, 3}, "audio/webm", "recording.webm")).isEqualTo("Explain this meeting");
        server.verify(postRequestedFor(urlEqualTo("/v1/audio/transcriptions"))
                .withHeader("Authorization", equalTo("Bearer secret-test"))
                .withAnyRequestBodyPart(aMultipart().withName("model").withBody(equalTo("test-transcriber")))
                .withAnyRequestBodyPart(aMultipart().withName("file")
                        .withHeader("Content-Type", containing("audio/webm"))
                        .withBody(binaryEqualTo(new byte[]{1, 2, 3}))));
    }

    @Test
    void permitsLocalServiceWithoutAKeyAndEmptySpeech() {
        server.stubFor(post("/v1/audio/transcriptions").willReturn(okJson("{\"text\":\"\"}")));
        assertThat(adapter("", Duration.ofSeconds(5)).transcribe(new byte[]{1}, "audio/ogg", "recording.ogg"))
                .isEmpty();
        server.verify(postRequestedFor(urlEqualTo("/v1/audio/transcriptions"))
                .withHeader("Authorization", absent()));
    }

    @Test
    void neverEchoesUpstreamErrorsOrRetries() {
        server.stubFor(post("/v1/audio/transcriptions").willReturn(aResponse().withStatus(503)
                .withBody("secret-test confidential transcription")));
        assertThatThrownBy(() -> adapter("secret-test", Duration.ofSeconds(5))
                .transcribe(new byte[]{1}, "audio/webm", "recording.webm"))
                .isInstanceOf(AudioTranscriptionException.class).hasMessageNotContaining("secret-test")
                .hasMessageNotContaining("confidential");
        server.verify(1, postRequestedFor(urlEqualTo("/v1/audio/transcriptions")));
    }

    @Test
    void timesOutWithoutRetrying() {
        server.stubFor(post("/v1/audio/transcriptions").willReturn(okJson("{\"text\":\"late\"}").withFixedDelay(500)));
        assertThatThrownBy(() -> adapter(null, Duration.ofMillis(150))
                .transcribe(new byte[]{1}, "audio/mp4", "recording.m4a"))
                .isInstanceOfSatisfying(AudioTranscriptionException.class,
                        e -> assertThat(e.kind()).isEqualTo(AudioTranscriptionException.Kind.TIMEOUT));
        server.verify(1, postRequestedFor(urlEqualTo("/v1/audio/transcriptions")));
    }

    @Test
    void rejectsMalformedSuccessResponse() {
        server.stubFor(post("/v1/audio/transcriptions").willReturn(okJson("{\"unexpected\":true}")));
        assertThatThrownBy(() -> adapter(null, Duration.ofSeconds(5))
                .transcribe(new byte[]{1}, "audio/webm", "recording.webm"))
                .isInstanceOf(AudioTranscriptionException.class);
    }
}
