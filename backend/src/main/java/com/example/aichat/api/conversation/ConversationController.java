package com.example.aichat.api.conversation;

import com.example.aichat.api.conversation.ConversationDtos.ConversationDetailResponse;
import com.example.aichat.api.conversation.ConversationDtos.ConversationResponse;
import com.example.aichat.api.conversation.ConversationDtos.CreateConversationRequest;
import com.example.aichat.api.conversation.ConversationDtos.MessageResponse;
import com.example.aichat.api.conversation.ConversationDtos.RenameConversationRequest;
import com.example.aichat.api.conversation.ConversationDtos.SystemPromptSelection;
import com.example.aichat.application.auth.CurrentUser;
import com.example.aichat.application.conversation.ConversationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Conversations of the authenticated user. Other users' conversations are indistinguishable from missing ones.
 */
@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversations;

    public ConversationController(ConversationService conversations) {
        this.conversations = conversations;
    }

    @GetMapping
    public List<ConversationResponse> list(@AuthenticationPrincipal CurrentUser user) {
        return conversations.list(user.id()).stream().map(ConversationResponse::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationResponse create(@AuthenticationPrincipal CurrentUser user,
                                       @Valid @RequestBody(required = false) CreateConversationRequest request) {
        CreateConversationRequest body = request == null ? new CreateConversationRequest(null, null) : request;
        return ConversationResponse.of(conversations.create(user.id(), body.title(), body.systemPromptId()));
    }

    @GetMapping("/{id}")
    public ConversationDetailResponse get(@AuthenticationPrincipal CurrentUser user, @PathVariable("id") UUID id) {
        return ConversationDetailResponse.of(conversations.details(user.id(), id));
    }

    @GetMapping("/{id}/messages")
    public List<MessageResponse> messages(@AuthenticationPrincipal CurrentUser user, @PathVariable("id") UUID id) {
        return conversations.details(user.id(), id).messages().stream().map(MessageResponse::of).toList();
    }

    @PatchMapping("/{id}")
    public ConversationResponse rename(@AuthenticationPrincipal CurrentUser user, @PathVariable("id") UUID id,
                                       @Valid @RequestBody RenameConversationRequest request) {
        return ConversationResponse.of(conversations.rename(user.id(), id, request.title()));
    }

    @PutMapping("/{id}/system-prompt")
    public ConversationResponse changeSystemPrompt(@AuthenticationPrincipal CurrentUser user,
                                                   @PathVariable("id") UUID id,
                                                   @RequestBody SystemPromptSelection request) {
        return ConversationResponse.of(conversations.changeSystemPrompt(user.id(), id, request.systemPromptId()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal CurrentUser user, @PathVariable("id") UUID id) {
        conversations.delete(user.id(), id);
    }
}
