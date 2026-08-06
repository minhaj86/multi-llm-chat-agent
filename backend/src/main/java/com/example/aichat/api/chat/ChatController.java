package com.example.aichat.api.chat;

import com.example.aichat.api.SseSupport;
import com.example.aichat.api.chat.ChatDtos.CancelResponse;
import com.example.aichat.api.chat.ChatDtos.ChatRequest;
import com.example.aichat.api.chat.ChatDtos.ChatResponse;
import com.example.aichat.api.chat.ChatDtos.RegenerateRequest;
import com.example.aichat.api.chat.ChatDtos.TextPayload;
import com.example.aichat.api.error.LlmHttpStatus;
import com.example.aichat.api.error.Problems;
import com.example.aichat.application.auth.CurrentUser;
import com.example.aichat.application.chat.ChatCommand;
import com.example.aichat.application.chat.ChatEvent;
import com.example.aichat.application.chat.ChatProperties;
import com.example.aichat.application.chat.ChatResult;
import com.example.aichat.application.chat.ChatService;
import com.example.aichat.application.chat.RegenerateCommand;
import com.example.aichat.common.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * Chat endpoints. {@code POST /api/chat/stream} streams the answer as Server-Sent Events:
 *
 * <pre>
 * event: start      {"conversationId", "userMessageId", "assistantMessageId", "provider", "model", ...}
 * event: reasoning  {"text": "..."}          (only for models exposing their reasoning)
 * event: delta      {"text": "..."}          (answer tokens)
 * event: done       {"status": "COMPLETE|CANCELLED", "inputTokens", "outputTokens", "latencyMs", ...}
 * event: error      {"code", "message", "provider", "alternatives": [{"id", "name"}], ...}
 * event: title      {"conversationId", "title"}   (generated title of a new conversation)
 * </pre>
 *
 * Closing the connection (e.g. {@code AbortController.abort()}) stops the generation upstream.
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final ChatProperties properties;

    public ChatController(ChatService chatService, ChatProperties properties) {
        this.chatService = chatService;
        this.properties = properties;
    }

    @PostMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<Flux<ServerSentEvent<Object>>> stream(@AuthenticationPrincipal CurrentUser user,
                                                                @Valid @RequestBody ChatRequest request,
                                                                HttpServletRequest httpRequest) {
        Flux<ChatEvent> events = chatService.stream(command(user, request, httpRequest));
        return SseSupport.response(events.map(ChatController::toSse), properties.heartbeatInterval());
    }

    @PostMapping(path = "/regenerate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<Flux<ServerSentEvent<Object>>> regenerate(@AuthenticationPrincipal CurrentUser user,
                                                                    @Valid @RequestBody RegenerateRequest request,
                                                                    HttpServletRequest httpRequest) {
        Flux<ChatEvent> events = chatService.regenerate(new RegenerateCommand(user.id(), request.conversationId(),
                request.provider(), request.model(), CorrelationIdFilter.current(httpRequest)));
        return SseSupport.response(events.map(ChatController::toSse), properties.heartbeatInterval());
    }

    /** Explicit stop (in addition to closing the stream); the partial answer is kept. */
    @PostMapping("/generations/{assistantMessageId}/cancel")
    public ResponseEntity<CancelResponse> cancel(@AuthenticationPrincipal CurrentUser user,
                                                 @PathVariable("assistantMessageId") UUID assistantMessageId) {
        boolean cancelled = chatService.cancel(user.id(), assistantMessageId);
        return cancelled
                ? ResponseEntity.status(HttpStatus.ACCEPTED).body(new CancelResponse(true))
                : ResponseEntity.status(HttpStatus.NOT_FOUND).body(new CancelResponse(false));
    }

    /** Non-streaming variant for API clients: waits for the complete answer. */
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> chat(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody ChatRequest request,
                                  HttpServletRequest httpRequest) {
        ChatResult result = chatService.chat(command(user, request, httpRequest));
        if (result.failed() != null) {
            ChatEvent.Failed failed = result.failed();
            HttpStatus status = LlmHttpStatus.forCode(failed.code());
            ProblemDetail problem = Problems.of(status, failed.code(), failed.message());
            problem.setProperty("provider", failed.provider());
            problem.setProperty("conversationId", failed.conversationId());
            problem.setProperty("assistantMessageId", failed.assistantMessageId());
            problem.setProperty("retryable", failed.retryable());
            problem.setProperty("alternatives", failed.alternatives());
            return ResponseEntity.status(status).body(problem);
        }
        return ResponseEntity.ok(ChatResponse.of(result));
    }

    private static ChatCommand command(CurrentUser user, ChatRequest request, HttpServletRequest httpRequest) {
        return new ChatCommand(user.id(), request.conversationId(), request.message(), request.provider(),
                request.model(), request.systemPromptId(), CorrelationIdFilter.current(httpRequest));
    }

    static ServerSentEvent<Object> toSse(ChatEvent event) {
        return switch (event) {
            case ChatEvent.Started started -> SseSupport.event("start", started);
            case ChatEvent.Delta delta -> SseSupport.event("delta", new TextPayload(delta.text()));
            case ChatEvent.Reasoning reasoning -> SseSupport.event("reasoning", new TextPayload(reasoning.text()));
            case ChatEvent.Completed completed -> SseSupport.event("done", completed);
            case ChatEvent.Failed failed -> SseSupport.event("error", failed);
            case ChatEvent.TitleUpdated title -> SseSupport.event("title", title);
        };
    }
}
