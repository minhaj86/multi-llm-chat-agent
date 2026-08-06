package com.example.aichat.application.conversation;

import com.example.aichat.application.common.ResourceNotFoundException;
import com.example.aichat.application.common.ValidationException;
import com.example.aichat.application.prompt.SystemPromptService;
import com.example.aichat.common.Texts;
import com.example.aichat.common.Times;
import com.example.aichat.domain.conversation.Conversation;
import com.example.aichat.domain.conversation.ConversationRepository;
import com.example.aichat.domain.conversation.Message;
import com.example.aichat.domain.conversation.MessageRepository;
import com.example.aichat.domain.usage.LlmExecution;
import com.example.aichat.domain.usage.LlmExecutionRepository;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Conversation management. Every operation is scoped to the owning user; other users' conversations are reported
 * as not found.
 */
@Service
public class ConversationService {

    static final int MAX_CONVERSATIONS = 500;

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final LlmExecutionRepository executions;
    private final SystemPromptService systemPrompts;
    private final Clock clock;

    public ConversationService(ConversationRepository conversations, MessageRepository messages,
                               LlmExecutionRepository executions, SystemPromptService systemPrompts, Clock clock) {
        this.conversations = conversations;
        this.messages = messages;
        this.executions = executions;
        this.systemPrompts = systemPrompts;
        this.clock = clock;
    }

    public List<Conversation> list(UUID userId) {
        return conversations.findByUserId(userId, MAX_CONVERSATIONS);
    }

    public Conversation get(UUID userId, UUID conversationId) {
        return conversations.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", conversationId));
    }

    public ConversationDetails details(UUID userId, UUID conversationId) {
        Conversation conversation = get(userId, conversationId);
        List<Message> history = messages.findByConversationId(conversationId);
        Map<UUID, LlmExecution> byMessage = executions.findLatestByMessageIds(
                history.stream().filter(Message::isAssistant).map(Message::id).toList());
        return new ConversationDetails(conversation, history.stream()
                .map(m -> new ConversationDetails.MessageView(m, byMessage.get(m.id())))
                .toList());
    }

    @Transactional
    public Conversation create(UUID userId, String title, UUID systemPromptId) {
        if (systemPromptId != null) {
            systemPrompts.requireSelectable(systemPromptId);
        }
        Conversation conversation = Conversation.start(userId, Texts.hasText(title) ? title : "New chat",
                systemPromptId, null, null, Times.now(clock));
        if (Texts.hasText(title)) {
            conversation = conversation.rename(title, conversation.createdAt());
        }
        return conversations.save(conversation);
    }

    @Transactional
    public Conversation rename(UUID userId, UUID conversationId, String title) {
        if (Texts.isBlank(title)) {
            throw new ValidationException("INVALID_TITLE", "Title must not be blank");
        }
        if (title.strip().length() > Conversation.MAX_TITLE_LENGTH) {
            throw new ValidationException("INVALID_TITLE", "Title must not exceed "
                    + Conversation.MAX_TITLE_LENGTH + " characters");
        }
        return conversations.save(get(userId, conversationId).rename(title, Times.now(clock)));
    }

    @Transactional
    public Conversation changeSystemPrompt(UUID userId, UUID conversationId, UUID systemPromptId) {
        if (systemPromptId != null) {
            systemPrompts.requireSelectable(systemPromptId);
        }
        return conversations.save(get(userId, conversationId).withSystemPrompt(systemPromptId, Times.now(clock)));
    }

    @Transactional
    public void delete(UUID userId, UUID conversationId) {
        get(userId, conversationId);
        conversations.deleteById(conversationId);
    }
}
