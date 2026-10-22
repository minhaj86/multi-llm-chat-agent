package com.example.aichat.api.audio;

import com.example.aichat.application.audio.AudioProperties;
import com.example.aichat.application.audio.AudioTranscriptionService;
import com.example.aichat.application.auth.CurrentUser;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Authenticated transcription gateway. Audio is transient; only a subsequently submitted transcript is saved. */
@RestController
@RequestMapping("/api/audio")
public class AudioController {

    private final AudioTranscriptionService audio;

    public AudioController(AudioTranscriptionService audio) {
        this.audio = audio;
    }

    @GetMapping("/config")
    public Configuration configuration() {
        AudioProperties properties = audio.configuration();
        return new Configuration(properties.enabled(), properties.maxUploadBytes());
    }

    @PostMapping(value = "/transcriptions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Transcript transcribe(@AuthenticationPrincipal CurrentUser user,
                                 @RequestParam("file") MultipartFile file) throws IOException {
        audio.validateUpload(file.getSize(), file.getContentType());
        return new Transcript(audio.transcribe(user.id(), file.getBytes(), file.getContentType()));
    }

    public record Configuration(boolean enabled, int maxUploadBytes) {
    }

    public record Transcript(String text) {
        @Override
        public String toString() {
            return "Transcript{length=" + (text == null ? 0 : text.length()) + "}";
        }
    }
}
