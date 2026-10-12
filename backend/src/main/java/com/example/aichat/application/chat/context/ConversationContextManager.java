package com.example.aichat.application.chat.context;

import com.example.aichat.application.chat.ChatProperties;
import com.example.aichat.application.chat.knowledge.KnowledgeContext;
import com.example.aichat.common.Texts;
import com.example.aichat.domain.conversation.Message;
import com.example.aichat.domain.conversation.MessageRole;
import com.example.aichat.domain.llm.LlmMessage;
import com.example.aichat.domain.llm.LlmRole;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Builds the provider-neutral context for a request according to the configured {@link ContextStrategy}.
 *
 * <ul>
 *   <li>The system prompt (and rolling summary) is kept separate from the history and merged into a single
 *       leading system message, which every chat template supports.</li>
 *   <li>Failed and in-flight answers are excluded; history is trimmed from the oldest message to fit the token
 *       budget while the newest messages are always retained.</li>
 *   <li>Roles are normalised to strictly alternate (consecutive messages of the same role are merged and the
 *       history never starts with an assistant message) because some chat templates reject anything else.</li>
 *   <li>Knowledge-base context (RAG), when present, adds trusted usage rules to the system message and places
 *       the delimited, untrusted excerpts before the question in the current user turn; its tokens reduce the
 *       history budget. Stored messages are never modified.</li>
 * </ul>
 */
@Component
public class ConversationContextManager {

    static final String SUMMARY_HEADER = "Summary of the earlier part of this conversation:";

    private final ChatProperties.Context properties;
    private final TokenEstimator tokenEstimator;

    @Autowired
    public ConversationContextManager(ChatProperties chatProperties, TokenEstimator tokenEstimator) {
        this(chatProperties.context(), tokenEstimator);
    }

    public ConversationContextManager(ChatProperties.Context properties, TokenEstimator tokenEstimator) {
        this.properties = properties;
        this.tokenEstimator = tokenEstimator;
    }

    public ContextStrategy strategy() {
        return properties.strategy();
    }

    public PreparedContext prepare(ContextInput input) {
        return prepare(input, properties.strategy());
    }

    public PreparedContext prepare(ContextInput input, ContextStrategy strategy) {
        List<Message> eligible = input.history().stream().filter(Message::isContextEligible).toList();
        KnowledgeContext knowledge = input.knowledge();
        LlmMessage current = LlmMessage.user(knowledge.augment(input.userMessage()));

        boolean useSummary = strategy == ContextStrategy.SUMMARY_PLUS_RECENT
                && input.conversation() != null
                && input.conversation().hasSummary();
        List<Message> candidates = useSummary
                ? eligible.stream().filter(m -> m.createdAt().isAfter(input.conversation().summarizedUntil())).toList()
                : eligible;

        LlmMessage system = systemMessage(input.systemPrompt(), knowledge.hasKnowledge() ? knowledge.instructions() : null,
                useSummary ? input.conversation().summary() : null);
        int fixedTokens = tokenEstimator.estimate(current) + (system == null ? 0 : tokenEstimator.estimate(system));
        int historyBudget = Math.max(0, properties.maxContextTokens() - fixedTokens);

        List<Message> selected = switch (strategy) {
            case FULL_CONTEXT -> candidates;
            case LAST_N_MESSAGES -> fitToBudget(lastN(candidates, properties.lastNMessages()), historyBudget);
            case TOKEN_WINDOW, SUMMARY_PLUS_RECENT -> fitToBudget(candidates, historyBudget);
        };

        List<LlmMessage> conversation = new ArrayList<>(selected.size() + 1);
        selected.forEach(m -> conversation.add(toLlm(m)));
        conversation.add(current);
        List<LlmMessage> normalized = normalizeRoles(conversation);

        List<LlmMessage> result = new ArrayList<>(normalized.size() + 1);
        if (system != null) {
            result.add(system);
        }
        result.addAll(normalized);
        int tokens = result.stream().mapToInt(tokenEstimator::estimate).sum();
        int omitted = eligible.size() - selected.size();
        return new PreparedContext(result, strategy, tokens, selected.size(), omitted, useSummary);
    }

    /** Estimated tokens of messages that are not covered by the conversation summary yet. */
    public int estimateTokens(List<Message> messages) {
        return messages.stream().mapToInt(m -> tokenEstimator.estimate(m.content()) + TokenEstimator.MESSAGE_OVERHEAD)
                .sum();
    }

    private LlmMessage systemMessage(String systemPrompt, String knowledgeInstructions, String summary) {
        StringBuilder text = new StringBuilder();
        if (Texts.hasText(systemPrompt)) {
            text.append(systemPrompt.strip());
        }
        if (Texts.hasText(knowledgeInstructions)) {
            if (!text.isEmpty()) {
                text.append("\n\n");
            }
            text.append(knowledgeInstructions.strip());
        }
        if (Texts.hasText(summary)) {
            if (!text.isEmpty()) {
                text.append("\n\n");
            }
            text.append(SUMMARY_HEADER).append('\n').append(summary.strip());
        }
        return text.isEmpty() ? null : LlmMessage.system(text.toString());
    }

    private static List<Message> lastN(List<Message> messages, int n) {
        return messages.size() <= n ? messages : messages.subList(messages.size() - n, messages.size());
    }

    /** Keeps the newest messages whose estimated size fits into {@code budget}. */
    private List<Message> fitToBudget(List<Message> messages, int budget) {
        Deque<Message> kept = new ArrayDeque<>();
        int used = 0;
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message message = messages.get(i);
            int cost = tokenEstimator.estimate(message.content()) + TokenEstimator.MESSAGE_OVERHEAD;
            if (used + cost > budget) {
                break;
            }
            used += cost;
            kept.addFirst(message);
        }
        while (!kept.isEmpty() && kept.peekFirst().role() == MessageRole.ASSISTANT) {
            kept.removeFirst();
        }
        return List.copyOf(kept);
    }

    private static LlmMessage toLlm(Message message) {
        return message.isUser() ? LlmMessage.user(message.content()) : LlmMessage.assistant(message.content());
    }

    /** Merges consecutive messages of the same role and drops leading assistant messages. */
    static List<LlmMessage> normalizeRoles(List<LlmMessage> messages) {
        List<LlmMessage> result = new ArrayList<>(messages.size());
        for (LlmMessage message : messages) {
            if (result.isEmpty() && message.role() == LlmRole.ASSISTANT) {
                continue;
            }
            if (!result.isEmpty() && result.getLast().role() == message.role()) {
                LlmMessage previous = result.removeLast();
                result.add(new LlmMessage(message.role(), previous.content() + "\n\n" + message.content()));
            } else {
                result.add(message);
            }
        }
        return result;
    }
}
