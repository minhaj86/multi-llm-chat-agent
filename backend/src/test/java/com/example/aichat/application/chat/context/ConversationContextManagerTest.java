package com.example.aichat.application.chat.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.aichat.application.chat.ChatProperties;
import com.example.aichat.application.chat.knowledge.KnowledgeContext;
import com.example.aichat.domain.conversation.Conversation;
import com.example.aichat.domain.conversation.Message;
import com.example.aichat.domain.conversation.TitleSource;
import com.example.aichat.domain.llm.LlmMessage;
import com.example.aichat.domain.llm.LlmRole;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConversationContextManagerTest {

    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");
    private static final UUID CONVERSATION = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();

    private final TokenEstimator estimator = new HeuristicTokenEstimator(4.0);

    private ConversationContextManager manager(ContextStrategy strategy, int maxTokens, int lastN) {
        return new ConversationContextManager(new ChatProperties.Context(strategy, maxTokens, lastN, 6000, 2, 4.0),
                estimator);
    }

    private static Conversation conversation(String summary, Instant summarizedUntil) {
        return new Conversation(CONVERSATION, USER, "t", TitleSource.DEFAULT, null, null, null, summary,
                summarizedUntil, T0, T0, null);
    }

    private static Message user(int index, String text) {
        return Message.user(CONVERSATION, text, T0.plusSeconds(index));
    }

    private static Message assistant(int index, String text) {
        Instant at = T0.plusSeconds(index);
        return Message.assistantPlaceholder(CONVERSATION, "qwen", "qwen3", at).complete(text, null, null, at);
    }

    /** Alternating history of {@code exchanges} user/assistant pairs, each message ~100 chars (25 tokens). */
    private static List<Message> history(int exchanges) {
        List<Message> messages = new ArrayList<>();
        for (int i = 0; i < exchanges; i++) {
            messages.add(user(i * 2, "question " + i + " " + "q".repeat(88)));
            messages.add(assistant(i * 2 + 1, "answer " + i + " " + "a".repeat(90)));
        }
        return messages;
    }

    @Test
    void fullContextIncludesAllEligibleMessagesAndKeepsSystemPromptSeparate() {
        PreparedContext context = manager(ContextStrategy.FULL_CONTEXT, 300, 20)
                .prepare(new ContextInput("You are helpful.", conversation(null, null), history(10), "new question"));

        List<LlmMessage> messages = context.messages();
        assertThat(messages).hasSize(1 + 20 + 1);
        assertThat(messages.getFirst()).isEqualTo(LlmMessage.system("You are helpful."));
        assertThat(messages.stream().filter(m -> m.role() == LlmRole.SYSTEM)).hasSize(1);
        assertThat(messages.getLast()).isEqualTo(LlmMessage.user("new question"));
        assertThat(context.omittedHistoryMessages()).isZero();
    }

    @Test
    void failedAndStreamingAnswersAreNeverSentAsContext() {
        Message failed = Message.assistantPlaceholder(CONVERSATION, "llama", "m", T0.plusSeconds(2))
                .fail("partial", null, "PROVIDER_UNAVAILABLE", "down", T0.plusSeconds(2));
        Message streaming = Message.assistantPlaceholder(CONVERSATION, "llama", "m", T0.plusSeconds(4));
        List<Message> history = List.of(user(1, "first"), failed, user(3, "second"), streaming);

        PreparedContext context = manager(ContextStrategy.FULL_CONTEXT, 1000, 20)
                .prepare(new ContextInput(null, conversation(null, null), history, "third"));

        assertThat(context.messages()).extracting(LlmMessage::content).noneMatch(c -> c.contains("partial"));
        // The unanswered user messages are merged so that roles strictly alternate.
        assertThat(context.messages()).containsExactly(LlmMessage.user("first\n\nsecond\n\nthird"));
    }

    @Test
    void lastNMessagesKeepsOnlyTheMostRecentMessages() {
        PreparedContext context = manager(ContextStrategy.LAST_N_MESSAGES, 10_000, 4)
                .prepare(new ContextInput(null, conversation(null, null), history(10), "now"));

        assertThat(context.includedHistoryMessages()).isEqualTo(4);
        assertThat(context.messages()).hasSize(5);
        assertThat(context.messages().getFirst().content()).startsWith("question 8");
        assertThat(context.omittedHistoryMessages()).isEqualTo(16);
    }

    @Test
    void tokenWindowDropsOldestMessagesBeyondBudgetAndNeverStartsWithAnAssistant() {
        PreparedContext context = manager(ContextStrategy.TOKEN_WINDOW, 300, 20)
                .prepare(new ContextInput("sys", conversation(null, null), history(10), "now?"));

        assertThat(context.estimatedTokens()).isLessThanOrEqualTo(300);
        assertThat(context.includedHistoryMessages()).isBetween(2, 10);
        assertThat(context.omittedHistoryMessages()).isPositive();
        assertThat(context.messages().get(1).role()).isEqualTo(LlmRole.USER);
        assertThat(context.messages().get(context.messages().size() - 2).content()).startsWith("answer 9");
    }

    @Test
    void summaryPlusRecentUsesTheSummaryAndOnlyMessagesNewerThanIt() {
        List<Message> history = history(10);
        Instant summarizedUntil = history.get(9).createdAt();
        PreparedContext context = manager(ContextStrategy.SUMMARY_PLUS_RECENT, 10_000, 20)
                .prepare(new ContextInput("You are helpful.", conversation("User likes Java.", summarizedUntil),
                        history, "next"));

        assertThat(context.summaryIncluded()).isTrue();
        LlmMessage system = context.messages().getFirst();
        assertThat(system.role()).isEqualTo(LlmRole.SYSTEM);
        assertThat(system.content()).startsWith("You are helpful.")
                .contains(ConversationContextManager.SUMMARY_HEADER)
                .contains("User likes Java.");
        assertThat(context.includedHistoryMessages()).isEqualTo(10);
        assertThat(context.messages().get(1).content()).startsWith("question 5");
    }

    @Test
    void summaryPlusRecentWithoutSummaryBehavesLikeTokenWindow() {
        PreparedContext context = manager(ContextStrategy.SUMMARY_PLUS_RECENT, 300, 20)
                .prepare(new ContextInput(null, conversation(null, null), history(10), "x"));

        assertThat(context.summaryIncluded()).isFalse();
        assertThat(context.estimatedTokens()).isLessThanOrEqualTo(300);
        assertThat(context.omittedHistoryMessages()).isPositive();
    }

    @Test
    void knowledgeAddsRulesToTheSystemMessageAndExcerptsToTheCurrentTurnOnly() {
        KnowledgeContext knowledge = new KnowledgeContext(true, "Knowledge rules.", "<knowledge>\n[1] fact\n</knowledge>",
                List.of(), 20, 1, null);
        PreparedContext context = manager(ContextStrategy.TOKEN_WINDOW, 8000, 20).prepare(new ContextInput("Be brief.",
                conversation(null, null), history(2), "What is the fact?", knowledge));

        assertThat(context.messages().getFirst().role()).isEqualTo(LlmRole.SYSTEM);
        assertThat(context.messages().getFirst().content()).isEqualTo("Be brief.\n\nKnowledge rules.");
        assertThat(context.messages().getLast().content())
                .isEqualTo("<knowledge>\n[1] fact\n</knowledge>\n\nQuestion:\nWhat is the fact?");
        assertThat(context.messages().subList(1, context.messages().size() - 1))
                .noneMatch(m -> m.content().contains("<knowledge>"));
    }

    @Test
    void knowledgeTokensReduceTheHistoryBudget() {
        String excerpts = "<knowledge>\n" + "fact ".repeat(400) + "\n</knowledge>";
        KnowledgeContext knowledge = new KnowledgeContext(true, "Rules.", excerpts, List.of(), 500, 1, null);
        ContextInput plain = new ContextInput(null, conversation(null, null), history(20), "Question?");
        ContextInput augmented = new ContextInput(null, conversation(null, null), history(20), "Question?", knowledge);

        ConversationContextManager manager = manager(ContextStrategy.TOKEN_WINDOW, 1000, 20);
        assertThat(manager.prepare(augmented).includedHistoryMessages())
                .isLessThan(manager.prepare(plain).includedHistoryMessages());
    }

    @Test
    void normalizeRolesMergesConsecutiveMessagesAndDropsLeadingAssistant() {
        List<LlmMessage> normalized = ConversationContextManager.normalizeRoles(List.of(
                LlmMessage.assistant("orphan"),
                LlmMessage.user("a"),
                LlmMessage.user("b"),
                LlmMessage.assistant("c"),
                LlmMessage.assistant("d"),
                LlmMessage.user("e")));

        assertThat(normalized).containsExactly(
                LlmMessage.user("a\n\nb"),
                LlmMessage.assistant("c\n\nd"),
                LlmMessage.user("e"));
    }
}
