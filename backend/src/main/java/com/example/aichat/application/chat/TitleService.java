package com.example.aichat.application.chat;

import com.example.aichat.application.provider.LlmErrorMessages;
import com.example.aichat.application.provider.LlmProviderRegistry;
import com.example.aichat.application.provider.ResolvedProvider;
import com.example.aichat.application.usage.ExecutionRecord;
import com.example.aichat.application.usage.UsageRecorder;
import com.example.aichat.common.Texts;
import com.example.aichat.common.Times;
import com.example.aichat.domain.conversation.ConversationRepository;
import com.example.aichat.domain.llm.LlmMessage;
import com.example.aichat.domain.llm.LlmRequest;
import com.example.aichat.domain.llm.LlmResponse;
import com.example.aichat.domain.llm.LlmUsage;
import com.example.aichat.domain.llm.ReasoningMode;
import com.example.aichat.domain.usage.ExecutionPurpose;
import com.example.aichat.domain.usage.ExecutionStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Conversation titles: a provisional title from the first message is set immediately; afterwards a model is
 * asked (asynchronously, once the first answer is complete) for a short descriptive title. User renames always win.
 */
@Service
public class TitleService {

    private static final Logger log = LoggerFactory.getLogger(TitleService.class);

    static final int PROVISIONAL_MAX_LENGTH = 60;
    static final int MAX_TITLE_LENGTH = 80;
    private static final Pattern THINK_BLOCK = Pattern.compile("(?s)<think>.*?(</think>|$)");
    private static final Pattern TITLE_PREFIX = Pattern.compile("(?i)^(conversation\\s+)?title\\s*[:：-]\\s*");
    private static final Pattern EDGE_DECORATION = Pattern.compile("^[#*_`\"'“”‘’«»\\s]+|[#*_`\"'“”‘’«»\\s]+$");
    private static final Pattern TRAILING_PUNCTUATION = Pattern.compile("[.。!！?？:;,]+$");
    private static final String SYSTEM_PROMPT = """
            You write concise titles for chat conversations.
            Reply with the title only: 2 to 6 words, no quotes, no emojis, no trailing punctuation.
            Use the language of the user's message.""";

    private final ConversationRepository conversations;
    private final LlmProviderRegistry registry;
    private final UsageRecorder usageRecorder;
    private final BackgroundTasks tasks;
    private final ChatProperties properties;

    public TitleService(ConversationRepository conversations, LlmProviderRegistry registry,
                        UsageRecorder usageRecorder, BackgroundTasks tasks, ChatProperties properties) {
        this.conversations = conversations;
        this.registry = registry;
        this.usageRecorder = usageRecorder;
        this.tasks = tasks;
        this.properties = properties;
    }

    public static String provisionalTitle(String firstMessage) {
        String singleLine = Texts.singleLine(firstMessage);
        return singleLine.isEmpty() ? "New chat" : Texts.truncate(singleLine, PROVISIONAL_MAX_LENGTH);
    }

    /** Starts title generation in the background; completes with the new title if one was stored. */
    public CompletableFuture<Optional<String>> generateAsync(UUID conversationId, UUID userId, String firstMessage,
                                                             ResolvedProvider conversationTarget, String correlationId) {
        if (properties.title().mode() == ChatProperties.TitleMode.HEURISTIC) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return tasks.submit(() -> generate(conversationId, userId, firstMessage, conversationTarget, correlationId));
    }

    public Optional<String> generate(UUID conversationId, UUID userId, String firstMessage,
                                     ResolvedProvider conversationTarget, String correlationId) {
        ResolvedProvider target;
        try {
            target = Texts.hasText(properties.title().provider())
                    ? registry.resolve(properties.title().provider(), properties.title().model())
                    : conversationTarget;
        } catch (RuntimeException e) {
            log.debug("Title provider not usable: {}", e.toString());
            return Optional.empty();
        }
        LlmRequest base = target.request(
                List.of(LlmMessage.system(SYSTEM_PROMPT),
                        LlmMessage.user("Conversation start:\n" + Texts.truncate(firstMessage, 2000))),
                correlationId, ReasoningMode.DISABLED, properties.title().maxTokens());
        LlmRequest request = base.withOptions(base.options().withTemperature(0.3));
        long start = System.nanoTime();
        try {
            LlmResponse response = target.provider().chat(request).block(properties.title().timeout());
            record(target, conversationId, userId, response == null ? LlmUsage.UNKNOWN : response.usage(), start,
                    ExecutionStatus.SUCCESS, null);
            String title = sanitize(response == null ? null : response.content());
            if (title == null) {
                return Optional.empty();
            }
            boolean updated = conversations.updateGeneratedTitle(conversationId, title);
            return updated ? Optional.of(title) : Optional.empty();
        } catch (RuntimeException e) {
            record(target, conversationId, userId, LlmUsage.UNKNOWN, start, ExecutionStatus.ERROR,
                    LlmErrorMessages.code(e));
            log.debug("Title generation failed for conversation {}: {}", conversationId, e.toString());
            return Optional.empty();
        }
    }

    /** Extracts a clean single-line title from a model answer, or {@code null} if nothing usable remains. */
    static String sanitize(String raw) {
        if (raw == null) {
            return null;
        }
        String withoutThinking = THINK_BLOCK.matcher(raw).replaceAll("");
        String line = withoutThinking.lines().map(String::strip).filter(l -> !l.isEmpty()).findFirst().orElse("");
        line = TITLE_PREFIX.matcher(line).replaceFirst("");
        line = EDGE_DECORATION.matcher(line).replaceAll("");
        line = TRAILING_PUNCTUATION.matcher(line).replaceAll("");
        line = Texts.singleLine(line);
        return line.isEmpty() ? null : Texts.truncate(line, MAX_TITLE_LENGTH);
    }

    private void record(ResolvedProvider target, UUID conversationId, UUID userId, LlmUsage usage, long startNanos,
                        ExecutionStatus status, String errorCode) {
        usageRecorder.record(new ExecutionRecord(target.descriptor(), target.model(), ExecutionPurpose.TITLE, userId,
                conversationId, null, usage, Times.elapsedMillis(startNanos), null, status, errorCode));
    }
}
