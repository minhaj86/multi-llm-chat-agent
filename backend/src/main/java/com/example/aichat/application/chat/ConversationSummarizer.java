package com.example.aichat.application.chat;

import com.example.aichat.application.chat.context.ContextStrategy;
import com.example.aichat.application.chat.context.ConversationContextManager;
import com.example.aichat.application.provider.LlmErrorMessages;
import com.example.aichat.application.provider.LlmProviderRegistry;
import com.example.aichat.application.provider.ResolvedProvider;
import com.example.aichat.application.usage.ExecutionRecord;
import com.example.aichat.application.usage.UsageRecorder;
import com.example.aichat.common.Texts;
import com.example.aichat.common.Times;
import com.example.aichat.domain.conversation.Conversation;
import com.example.aichat.domain.conversation.ConversationRepository;
import com.example.aichat.domain.conversation.Message;
import com.example.aichat.domain.conversation.MessageRepository;
import com.example.aichat.domain.llm.LlmMessage;
import com.example.aichat.domain.llm.LlmRequest;
import com.example.aichat.domain.llm.LlmResponse;
import com.example.aichat.domain.llm.LlmUsage;
import com.example.aichat.domain.llm.ReasoningMode;
import com.example.aichat.domain.usage.ExecutionPurpose;
import com.example.aichat.domain.usage.ExecutionStatus;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Maintains the rolling conversation summary used by {@link ContextStrategy#SUMMARY_PLUS_RECENT}.
 *
 * <p>Runs in the background after an answer completes, so summarisation never delays a response. When the
 * unsummarised history exceeds the trigger size, the oldest messages (all but the most recent few) are folded into
 * the summary. At most one summarisation runs per conversation at a time.
 */
@Service
public class ConversationSummarizer {

    private static final Logger log = LoggerFactory.getLogger(ConversationSummarizer.class);
    private static final Pattern THINK_BLOCK = Pattern.compile("(?s)<think>.*?(</think>|$)");
    private static final int MAX_CHARS_PER_MESSAGE = 4000;
    private static final String SYSTEM_PROMPT = """
            You maintain a running summary of a conversation between a user and an AI assistant.
            Merge the previous summary and the new messages into one updated summary.
            Keep facts, decisions, requirements, user preferences, code identifiers and open questions.
            Write compact bullet points in the language of the conversation. Do not add commentary.""";

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final ConversationContextManager contextManager;
    private final LlmProviderRegistry registry;
    private final UsageRecorder usageRecorder;
    private final BackgroundTasks tasks;
    private final ChatProperties properties;
    private final Set<UUID> inProgress = ConcurrentHashMap.newKeySet();

    public ConversationSummarizer(ConversationRepository conversations, MessageRepository messages,
                                  ConversationContextManager contextManager, LlmProviderRegistry registry,
                                  UsageRecorder usageRecorder, BackgroundTasks tasks, ChatProperties properties) {
        this.conversations = conversations;
        this.messages = messages;
        this.contextManager = contextManager;
        this.registry = registry;
        this.usageRecorder = usageRecorder;
        this.tasks = tasks;
        this.properties = properties;
    }

    public void summarizeIfNeededAsync(UUID conversationId, UUID userId, ResolvedProvider conversationTarget,
                                       String correlationId) {
        if (!isEnabled() || !inProgress.add(conversationId)) {
            return;
        }
        tasks.execute(() -> {
            try {
                summarizeIfNeeded(conversationId, userId, conversationTarget, correlationId);
            } catch (RuntimeException e) {
                log.warn("Summarisation of conversation {} failed: {}", conversationId, e.toString());
            } finally {
                inProgress.remove(conversationId);
            }
        });
    }

    /**
     * Updates the summary when needed.
     *
     * @return {@code true} if a new summary was stored
     */
    public boolean summarizeIfNeeded(UUID conversationId, UUID userId, ResolvedProvider conversationTarget,
                                     String correlationId) {
        if (!isEnabled()) {
            return false;
        }
        Conversation conversation = conversations.findById(conversationId).orElse(null);
        if (conversation == null) {
            return false;
        }
        List<Message> eligible = messages.findByConversationId(conversationId).stream()
                .filter(Message::isContextEligible)
                .toList();
        List<Message> unsummarized = conversation.summarizedUntil() == null
                ? eligible
                : eligible.stream().filter(m -> m.createdAt().isAfter(conversation.summarizedUntil())).toList();
        int keepRecent = properties.context().summaryKeepRecentMessages();
        if (unsummarized.size() <= keepRecent
                || contextManager.estimateTokens(unsummarized) <= properties.context().summaryTriggerTokens()) {
            return false;
        }
        int foldCount = Math.min(unsummarized.size() - keepRecent, properties.summary().maxMessagesPerRun());
        List<Message> toFold = unsummarized.subList(0, foldCount);

        ResolvedProvider target = Texts.hasText(properties.summary().provider())
                ? registry.resolve(properties.summary().provider(), properties.summary().model())
                : conversationTarget;
        LlmRequest base = target.request(
                List.of(LlmMessage.system(SYSTEM_PROMPT), LlmMessage.user(prompt(conversation.summary(), toFold))),
                correlationId, ReasoningMode.DISABLED, properties.summary().maxTokens());
        LlmRequest request = base.withOptions(base.options().withTemperature(0.2));
        long start = System.nanoTime();
        try {
            LlmResponse response = target.provider().chat(request).block(properties.summary().timeout());
            record(target, conversationId, userId, response == null ? LlmUsage.UNKNOWN : response.usage(), start,
                    ExecutionStatus.SUCCESS, null);
            String summary = response == null ? "" : THINK_BLOCK.matcher(response.content()).replaceAll("").strip();
            if (summary.isEmpty()) {
                return false;
            }
            conversations.updateSummary(conversationId, summary, toFold.getLast().createdAt());
            log.debug("Conversation {} summarised ({} messages folded)", conversationId, toFold.size());
            return true;
        } catch (RuntimeException e) {
            record(target, conversationId, userId, LlmUsage.UNKNOWN, start, ExecutionStatus.ERROR,
                    LlmErrorMessages.code(e));
            throw e;
        }
    }

    private boolean isEnabled() {
        return properties.summary().enabled()
                && contextManager.strategy() == ContextStrategy.SUMMARY_PLUS_RECENT;
    }

    private static String prompt(String previousSummary, List<Message> newMessages) {
        StringBuilder text = new StringBuilder();
        text.append("Previous summary:\n")
                .append(Texts.hasText(previousSummary) ? previousSummary.strip() : "(none)")
                .append("\n\nNew messages:\n");
        for (Message message : newMessages) {
            text.append(message.isUser() ? "User: " : "Assistant: ")
                    .append(Texts.truncate(message.content().strip(), MAX_CHARS_PER_MESSAGE))
                    .append("\n\n");
        }
        text.append("Write the updated summary now.");
        return text.toString();
    }

    private void record(ResolvedProvider target, UUID conversationId, UUID userId, LlmUsage usage, long startNanos,
                        ExecutionStatus status, String errorCode) {
        usageRecorder.record(new ExecutionRecord(target.descriptor(), target.model(), ExecutionPurpose.SUMMARY, userId,
                conversationId, null, usage, Times.elapsedMillis(startNanos), null, status, errorCode));
    }
}
