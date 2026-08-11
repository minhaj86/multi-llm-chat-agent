package com.example.aichat.api.compare;

import com.example.aichat.api.SseSupport;
import com.example.aichat.application.auth.CurrentUser;
import com.example.aichat.application.chat.ChatProperties;
import com.example.aichat.application.compare.CompareCommand;
import com.example.aichat.application.compare.CompareEvent;
import com.example.aichat.application.compare.CompareService;
import com.example.aichat.common.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * Compare mode: the same prompt streamed from several providers side by side.
 *
 * <pre>
 * event: start      {"runId", "targets": [{"targetId": "t0", "provider", "providerName", "model", "local"}]}
 * event: delta      {"targetId", "text"}
 * event: reasoning  {"targetId", "text"}
 * event: done       {"targetId", "latencyMs", "inputTokens", "outputTokens", "estimatedCost", "local", ...}
 * event: error      {"targetId", "code", "message", "retryable"}
 * event: end        {"runId"}
 * </pre>
 */
@RestController
@RequestMapping("/api/compare")
public class CompareController {

    private final CompareService compareService;
    private final ChatProperties properties;

    public CompareController(CompareService compareService, ChatProperties properties) {
        this.compareService = compareService;
        this.properties = properties;
    }

    @PostMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<Flux<ServerSentEvent<Object>>> stream(@AuthenticationPrincipal CurrentUser user,
                                                                @Valid @RequestBody CompareRequest request,
                                                                HttpServletRequest httpRequest) {
        List<CompareCommand.Target> targets = request.targets().stream()
                .map(t -> new CompareCommand.Target(t.provider(), t.model()))
                .toList();
        Flux<CompareEvent> events = compareService.compare(new CompareCommand(user.id(), request.prompt(), targets,
                request.systemPromptId(), CorrelationIdFilter.current(httpRequest)));
        return SseSupport.response(events.map(CompareController::toSse), properties.heartbeatInterval());
    }

    static ServerSentEvent<Object> toSse(CompareEvent event) {
        return switch (event) {
            case CompareEvent.Started started -> SseSupport.event("start", started);
            case CompareEvent.Delta delta -> SseSupport.event("delta", delta);
            case CompareEvent.Reasoning reasoning -> SseSupport.event("reasoning", reasoning);
            case CompareEvent.Completed completed -> SseSupport.event("done", completed);
            case CompareEvent.Failed failed -> SseSupport.event("error", failed);
            case CompareEvent.Finished finished -> SseSupport.event("end", finished);
        };
    }

    public record CompareRequest(@NotBlank @Size(max = 200_000) String prompt,
                                 @NotEmpty @Size(max = 8) List<@Valid TargetRequest> targets,
                                 UUID systemPromptId) {
    }

    public record TargetRequest(@NotBlank @Size(max = 64) String provider, @Size(max = 200) String model) {
    }
}
