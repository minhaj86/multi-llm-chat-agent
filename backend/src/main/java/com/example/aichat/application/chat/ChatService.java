package com.example.aichat.application.chat;

import com.example.aichat.application.chat.context.ContextInput;
import com.example.aichat.application.chat.context.ConversationContextManager;
import com.example.aichat.application.chat.context.PreparedContext;
import com.example.aichat.application.chat.knowledge.KnowledgeAugmenter;
import com.example.aichat.application.chat.knowledge.KnowledgeContext;
import com.example.aichat.application.chat.knowledge.KnowledgeQuery;
import com.example.aichat.application.common.ConflictException;
import com.example.aichat.application.common.ResourceNotFoundException;
import com.example.aichat.application.common.ValidationException;
import com.example.aichat.application.prompt.SystemPromptService;
import com.example.aichat.application.provider.LlmErrorMessages;
import com.example.aichat.application.provider.LlmProviderRegistry;
import com.example.aichat.application.provider.ProviderCatalogService;
import com.example.aichat.application.provider.ProviderHealthMonitor;
import com.example.aichat.application.provider.ResolvedProvider;
import com.example.aichat.application.usage.ExecutionRecord;
import com.example.aichat.application.usage.UsageRecorder;
import com.example.aichat.common.CorrelationIdFilter;
import com.example.aichat.common.Texts;
import com.example.aichat.common.Times;
import com.example.aichat.domain.conversation.Conversation;
import com.example.aichat.domain.conversation.ConversationRepository;
import com.example.aichat.domain.conversation.KnowledgeSettings;
import com.example.aichat.domain.conversation.Message;
import com.example.aichat.domain.conversation.MessageRepository;
import com.example.aichat.domain.conversation.MessageStatus;
import com.example.aichat.domain.conversation.TitleSource;
import com.example.aichat.domain.llm.FinishReason;
import com.example.aichat.domain.llm.LlmChunk;
import com.example.aichat.domain.llm.LlmRequest;
import com.example.aichat.domain.llm.LlmUsage;
import com.example.aichat.domain.llm.exception.LlmException;
import com.example.aichat.domain.prompt.SystemPrompt;
import com.example.aichat.domain.usage.ExecutionPurpose;
import com.example.aichat.domain.usage.ExecutionStatus;
import com.example.aichat.domain.usage.LlmExecution;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Chat use case: persists the exchange, builds the provider-neutral context, streams the answer from whichever
 * provider was selected and records usage. Nothing in here depends on a particular vendor - providers are resolved
 * through {@link LlmProviderRegistry} and accessed through the {@code LlmProvider} port only. Knowledge-base
 * context (RAG) comes through the {@link KnowledgeAugmenter} port; retrieval itself lives in the RAG module.
 *
 * <p>Lifecycle of a generation: the user message and an assistant placeholder ({@code STREAMING}) are stored
 * before streaming starts; the placeholder is finalised exactly once as {@code COMPLETE}, {@code CANCELLED} (stop
 * button / client disconnect, partial text kept) or {@code ERROR}.
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final Duration BLOCKING_CHAT_TIMEOUT = Duration.ofMinutes(15);

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final LlmProviderRegistry registry;
    private final ConversationContextManager contextManager;
    private final SystemPromptService systemPrompts;
    private final UsageRecorder usageRecorder;
    private final ProviderHealthMonitor healthMonitor;
    private final ProviderCatalogService catalog;
    private final TitleService titleService;
    private final ConversationSummarizer summarizer;
    private final GenerationRegistry generations;
    private final BackgroundTasks tasks;
    private final ChatProperties properties;
    private final TransactionOperations transactions;
    private final ObjectProvider<KnowledgeAugmenter> knowledge;
    private final Clock clock;

    public ChatService(ConversationRepository conversations, MessageRepository messages,
                       LlmProviderRegistry registry, ConversationContextManager contextManager,
                       SystemPromptService systemPrompts, UsageRecorder usageRecorder,
                       ProviderHealthMonitor healthMonitor, ProviderCatalogService catalog,
                       TitleService titleService, ConversationSummarizer summarizer,
                       GenerationRegistry generations, BackgroundTasks tasks, ChatProperties properties,
                       TransactionOperations transactions, ObjectProvider<KnowledgeAugmenter> knowledge,
                       Clock clock) {
        this.conversations = conversations;
        this.messages = messages;
        this.registry = registry;
        this.contextManager = contextManager;
        this.systemPrompts = systemPrompts;
        this.usageRecorder = usageRecorder;
        this.healthMonitor = healthMonitor;
        this.catalog = catalog;
        this.titleService = titleService;
        this.summarizer = summarizer;
        this.generations = generations;
        this.tasks = tasks;
        this.properties = properties;
        this.transactions = transactions;
        this.knowledge = knowledge;
        this.clock = clock;
    }

    /**
     * Sends a message and streams the answer. Validation, provider resolution and persistence of the user message
     * happen eagerly, so invalid requests fail before the stream starts.
     */
    public Flux<ChatEvent> stream(ChatCommand command) {
        String text = validateMessage(command.message());
        ResolvedProvider target = registry.resolve(command.provider(), command.model());
        if (command.systemPromptId() != null) {
            systemPrompts.requireSelectable(command.systemPromptId());
        }
        generations.ensureCapacity(command.userId());
        Instant now = Times.now(clock);
        Exchange exchange = Objects.requireNonNull(
                transactions.execute(status -> openExchange(command, text, target, now)));
        List<Message> history = messages.findByConversationId(exchange.conversation().id()).stream()
                .filter(m -> !m.id().equals(exchange.userMessage().id())
                        && !m.id().equals(exchange.assistantMessage().id()))
                .toList();
        KnowledgeContext knowledgeContext = retrieveKnowledge(command.userId(), exchange.conversation(),
                exchange.assistantMessage().id(), text, command.correlationId());
        PreparedContext context = prepareContext(exchange.conversation(), history, text, knowledgeContext);
        LlmRequest request = target.request(context.messages(), command.correlationId());
        String titleSeed = exchange.conversation().titleSource() == TitleSource.DEFAULT
                ? firstUserMessage(history).orElse(text)
                : null;
        return run(new Generation(command.userId(), exchange.conversation(), exchange.created(),
                exchange.userMessage(), exchange.assistantMessage(), target, request, command.correlationId(), false,
                titleSeed, knowledgeContext));
    }

    /** Replaces the answer(s) to the last user message with a new answer (optionally from another provider). */
    public Flux<ChatEvent> regenerate(RegenerateCommand command) {
        ResolvedProvider target = registry.resolve(command.provider(), command.model());
        generations.ensureCapacity(command.userId());
        Conversation conversation = conversations.findByIdAndUserId(command.conversationId(), command.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", command.conversationId()));
        List<Message> all = messages.findByConversationId(conversation.id());
        int lastUserIndex = lastUserMessageIndex(all);
        if (lastUserIndex < 0) {
            throw new ValidationException("NOTHING_TO_REGENERATE", "The conversation has no message to answer");
        }
        List<Message> replaced = List.copyOf(all.subList(lastUserIndex + 1, all.size()));
        if (replaced.stream().anyMatch(m -> generations.isActive(m.id()))) {
            throw new ConflictException("GENERATION_IN_PROGRESS", "A response is still being generated");
        }
        Message lastUser = all.get(lastUserIndex);
        List<Message> history = List.copyOf(all.subList(0, lastUserIndex));
        Instant now = Times.now(clock);
        Message placeholder = Objects.requireNonNull(transactions.execute(status -> {
            messages.deleteAllById(replaced.stream().map(Message::id).toList());
            conversations.touch(conversation.id(), target.providerId(), target.model(), now);
            return messages.save(Message.assistantPlaceholder(conversation.id(), target.providerId(), target.model(),
                    now));
        }));
        KnowledgeContext knowledgeContext = retrieveKnowledge(command.userId(), conversation, placeholder.id(),
                lastUser.content(), command.correlationId());
        PreparedContext context = prepareContext(conversation, history, lastUser.content(), knowledgeContext);
        LlmRequest request = target.request(context.messages(), command.correlationId());
        String titleSeed = conversation.titleSource() == TitleSource.DEFAULT
                ? firstUserMessage(history).orElse(lastUser.content())
                : null;
        return run(new Generation(command.userId(), conversation, false, lastUser, placeholder, target, request,
                command.correlationId(), true, titleSeed, knowledgeContext));
    }

    /** Non-streaming variant: waits for the complete answer. */
    public ChatResult chat(ChatCommand command) {
        List<ChatEvent> events = stream(command)
                .takeUntil(e -> e instanceof ChatEvent.Completed || e instanceof ChatEvent.Failed)
                .collectList()
                .block(BLOCKING_CHAT_TIMEOUT);
        return ChatResult.from(events == null ? List.of() : events);
    }

    /** Stops a running generation of the given user; the partial answer is kept as {@code CANCELLED}. */
    public boolean cancel(UUID userId, UUID assistantMessageId) {
        return generations.cancel(assistantMessageId, userId);
    }

    private Flux<ChatEvent> run(Generation g) {
        UUID generationId = g.assistantMessage().id();
        return Flux.defer(() -> {
            GenerationRegistry.ActiveGeneration active = generations.register(generationId, g.userId());
            StreamAccumulator state = new StreamAccumulator();
            TitleHolder title = new TitleHolder();
            ChatEvent started = new ChatEvent.Started(g.conversation().id(), g.userMessage().id(), generationId,
                    g.target().providerId(), g.target().displayName(), g.target().model(), g.newConversation(),
                    g.conversation().title(), g.regenerated());
            log.info("Generation started provider={} model={} conversation={} messages={} knowledgeChunks={}",
                    g.target().providerId(), g.target().model(), g.conversation().id(), g.request().messages().size(),
                    g.knowledge().citations().size());
            Flux<ChatEvent> knowledgeEvents = g.knowledge().requested()
                    ? Flux.just(new ChatEvent.KnowledgeRetrieved(g.conversation().id(), generationId,
                    g.knowledge().citations(), g.knowledge().retrievedCount(), g.knowledge().notice()))
                    : Flux.empty();

            Flux<ChatEvent> deltas = g.target().provider().stream(g.request())
                    .doOnNext(state::accept)
                    .<ChatEvent>handle((chunk, sink) -> {
                        if (chunk instanceof LlmChunk.TextDelta delta && !delta.text().isEmpty()) {
                            sink.next(new ChatEvent.Delta(delta.text()));
                        } else if (chunk instanceof LlmChunk.ReasoningDelta delta && !delta.text().isEmpty()) {
                            sink.next(new ChatEvent.Reasoning(delta.text()));
                        }
                    })
                    .takeUntilOther(active.cancellation());

            Mono<ChatEvent> completion = Mono.fromCallable(() -> finish(g, state, title,
                            active.isCancelRequested() ? Outcome.CANCELLED : Outcome.COMPLETED, null))
                    .subscribeOn(Schedulers.boundedElastic());

            Flux<ChatEvent> titleEvents = Flux.defer(() -> titleEvents(g, title));

            return Flux.concat(Mono.just(started), knowledgeEvents, deltas, completion, titleEvents)
                    .onErrorResume(error -> Mono.fromCallable(() -> finish(g, state, title, Outcome.FAILED, error))
                            .subscribeOn(Schedulers.boundedElastic()))
                    .doOnCancel(() -> tasks.execute(() -> finish(g, state, title, Outcome.CANCELLED, null)))
                    .doFinally(signal -> generations.unregister(generationId));
        });
    }

    private ChatEvent finish(Generation g, StreamAccumulator state, TitleHolder title, Outcome outcome,
                             Throwable error) {
        if (!state.markFinished()) {
            return null;
        }
        try (MDC.MDCCloseable ignored = MDC.putCloseable(CorrelationIdFilter.MDC_KEY, g.correlationId())) {
            return switch (outcome) {
                case COMPLETED -> complete(g, state, title);
                case CANCELLED -> cancelled(g, state);
                case FAILED -> failed(g, state, error);
            };
        } catch (RuntimeException e) {
            log.error("Could not finalise generation {}", g.assistantMessage().id(), e);
            return new ChatEvent.Failed(g.conversation().id(), g.assistantMessage().id(),
                    LlmErrorMessages.INTERNAL_ERROR, "The response could not be saved.", g.target().providerId(),
                    g.target().displayName(), false, List.of());
        }
    }

    private ChatEvent complete(Generation g, StreamAccumulator state, TitleHolder title) {
        Instant now = Times.now(clock);
        messages.save(g.assistantMessage().complete(state.content(), state.reasoning(), null, now));
        LlmExecution execution = usageRecorder.record(execution(g, state, ExecutionStatus.SUCCESS, null));
        healthMonitor.reportSuccess(g.target().providerId());
        LlmUsage usage = state.usage();
        log.info("Generation completed provider={} model={} latencyMs={} ttftMs={} inputTokens={} outputTokens={} "
                        + "finishReason={}", g.target().providerId(), g.target().model(), execution.latencyMs(),
                execution.timeToFirstTokenMs(), usage.inputTokens(), usage.outputTokens(), state.finishReason());
        summarizer.summarizeIfNeededAsync(g.conversation().id(), g.userId(), g.target(), g.correlationId());
        if (g.titleSeed() != null) {
            title.future = titleService.generateAsync(g.conversation().id(), g.userId(), g.titleSeed(), g.target(),
                    g.correlationId());
        }
        return new ChatEvent.Completed(g.conversation().id(), g.assistantMessage().id(), MessageStatus.COMPLETE,
                g.target().providerId(), g.target().model(), state.finishReason(), usage.inputTokens(),
                usage.outputTokens(), execution.latencyMs(), execution.timeToFirstTokenMs(),
                execution.estimatedCost(), g.target().isLocal());
    }

    private ChatEvent cancelled(Generation g, StreamAccumulator state) {
        Instant now = Times.now(clock);
        messages.save(g.assistantMessage().cancel(state.content(), state.reasoning(), now));
        LlmExecution execution = usageRecorder.record(execution(g, state, ExecutionStatus.CANCELLED, null));
        log.info("Generation cancelled provider={} model={} latencyMs={}", g.target().providerId(),
                g.target().model(), execution.latencyMs());
        LlmUsage usage = state.usage();
        return new ChatEvent.Completed(g.conversation().id(), g.assistantMessage().id(), MessageStatus.CANCELLED,
                g.target().providerId(), g.target().model(), FinishReason.CANCELLED, usage.inputTokens(),
                usage.outputTokens(), execution.latencyMs(), execution.timeToFirstTokenMs(),
                execution.estimatedCost(), g.target().isLocal());
    }

    private ChatEvent failed(Generation g, StreamAccumulator state, Throwable error) {
        Instant now = Times.now(clock);
        String code = LlmErrorMessages.code(error);
        String message = LlmErrorMessages.describe(error, g.target().displayName());
        messages.save(g.assistantMessage().fail(state.content(), state.reasoning(), code, message, now));
        usageRecorder.record(execution(g, state, ExecutionStatus.ERROR, code));
        if (error instanceof LlmException llm) {
            healthMonitor.reportFailure(g.target().providerId(), llm);
            log.warn("Generation failed provider={} model={} code={}: {}", g.target().providerId(),
                    g.target().model(), code, llm.getMessage());
        } else {
            log.error("Generation failed unexpectedly provider={} model={}", g.target().providerId(),
                    g.target().model(), error);
        }
        return new ChatEvent.Failed(g.conversation().id(), g.assistantMessage().id(), code, message,
                g.target().providerId(), g.target().displayName(), LlmErrorMessages.retryable(error),
                catalog.alternativesTo(g.target().providerId()));
    }

    private Flux<ChatEvent> titleEvents(Generation g, TitleHolder holder) {
        CompletableFuture<Optional<String>> future = holder.future;
        if (future == null) {
            return Flux.empty();
        }
        return Mono.fromFuture(future, true)
                .timeout(properties.title().timeout().plusSeconds(5))
                .flatMap(Mono::justOrEmpty)
                .<ChatEvent>map(newTitle -> new ChatEvent.TitleUpdated(g.conversation().id(), newTitle))
                .onErrorResume(e -> Mono.empty())
                .flux();
    }

    private Exchange openExchange(ChatCommand command, String text, ResolvedProvider target, Instant now) {
        Conversation conversation;
        boolean created;
        if (command.conversationId() == null) {
            UUID promptId = command.systemPromptId() != null
                    ? command.systemPromptId()
                    : systemPrompts.defaultPrompt().map(SystemPrompt::id).orElse(null);
            Conversation started = Conversation.start(command.userId(), TitleService.provisionalTitle(text),
                    promptId, target.providerId(), target.model(), now);
            conversation = conversations.save(command.knowledge() == null ? started
                    : started.withKnowledge(command.knowledge(), now));
            created = true;
        } else {
            conversation = conversations.findByIdAndUserId(command.conversationId(), command.userId())
                    .orElseThrow(() -> new ResourceNotFoundException("Conversation", command.conversationId()));
            if (command.systemPromptId() != null && !command.systemPromptId().equals(conversation.systemPromptId())) {
                conversation = conversations.save(conversation.withSystemPrompt(command.systemPromptId(), now));
            }
            if (command.knowledge() != null && !command.knowledge().equals(conversation.knowledge())) {
                conversation = conversations.save(conversation.withKnowledge(command.knowledge(), now));
            }
            conversations.touch(conversation.id(), target.providerId(), target.model(), now);
            created = false;
        }
        Message user = messages.save(Message.user(conversation.id(), text, now));
        Message assistant = messages.save(Message.assistantPlaceholder(conversation.id(), target.providerId(),
                target.model(), now.plus(1, ChronoUnit.MICROS)));
        return new Exchange(conversation, created, user, assistant);
    }

    private PreparedContext prepareContext(Conversation conversation, List<Message> history, String userMessage,
                                           KnowledgeContext knowledgeContext) {
        String systemPrompt = systemPrompts.contentFor(conversation.systemPromptId()).orElse(null);
        PreparedContext context = contextManager.prepare(new ContextInput(systemPrompt, conversation, history,
                userMessage, knowledgeContext));
        log.debug("Context prepared strategy={} messages={} estimatedTokens={} omitted={} summary={}",
                context.strategy(), context.messages().size(), context.estimatedTokens(),
                context.omittedHistoryMessages(), context.summaryIncluded());
        return context;
    }

    /** Knowledge for the answer when the conversation uses the knowledge base (never throws). */
    private KnowledgeContext retrieveKnowledge(UUID userId, Conversation conversation, UUID assistantMessageId,
                                               String question, String correlationId) {
        KnowledgeSettings settings = conversation.knowledge();
        KnowledgeAugmenter augmenter = knowledge.getIfAvailable();
        if (!settings.enabled() || augmenter == null || !augmenter.isEnabled()) {
            return KnowledgeContext.NONE;
        }
        return augmenter.augment(new KnowledgeQuery(userId, conversation.id(), assistantMessageId, question,
                settings.collectionIds(), correlationId));
    }

    private ExecutionRecord execution(Generation g, StreamAccumulator state, ExecutionStatus status, String code) {
        return new ExecutionRecord(g.target().descriptor(), g.target().model(), ExecutionPurpose.CHAT, g.userId(),
                g.conversation().id(), g.assistantMessage().id(), state.usage(), state.latencyMs(),
                state.timeToFirstTokenMs(), status, code);
    }

    private String validateMessage(String message) {
        if (Texts.isBlank(message)) {
            throw new ValidationException("EMPTY_MESSAGE", "Message must not be empty");
        }
        if (message.length() > properties.maxMessageLength()) {
            throw new ValidationException("MESSAGE_TOO_LONG", "Message must not exceed "
                    + properties.maxMessageLength() + " characters");
        }
        return message.strip();
    }

    private static int lastUserMessageIndex(List<Message> all) {
        for (int i = all.size() - 1; i >= 0; i--) {
            if (all.get(i).isUser()) {
                return i;
            }
        }
        return -1;
    }

    private static Optional<String> firstUserMessage(List<Message> history) {
        return history.stream().filter(Message::isUser).map(Message::content).findFirst();
    }

    private enum Outcome {
        COMPLETED,
        CANCELLED,
        FAILED
    }

    private static final class TitleHolder {
        private volatile CompletableFuture<Optional<String>> future;
    }

    private record Exchange(Conversation conversation, boolean created, Message userMessage, Message assistantMessage) {
    }

    private record Generation(UUID userId,
                              Conversation conversation,
                              boolean newConversation,
                              Message userMessage,
                              Message assistantMessage,
                              ResolvedProvider target,
                              LlmRequest request,
                              String correlationId,
                              boolean regenerated,
                              String titleSeed,
                              KnowledgeContext knowledge) {
    }
}
