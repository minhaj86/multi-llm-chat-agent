package com.example.aichat.rag.application;

import com.example.aichat.application.chat.knowledge.KnowledgeAugmenter;
import com.example.aichat.application.chat.knowledge.KnowledgeCitation;
import com.example.aichat.application.chat.knowledge.KnowledgeContext;
import com.example.aichat.application.chat.knowledge.KnowledgeQuery;
import com.example.aichat.common.Times;
import com.example.aichat.rag.application.RagContextService.RagPreparation;
import com.example.aichat.rag.domain.KnowledgeCollection;
import com.example.aichat.rag.domain.RagExecution;
import com.example.aichat.rag.domain.RagExecutionRepository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Connects the chat module to the RAG pipeline (implements the chat-owned {@link KnowledgeAugmenter} port). Chat
 * stays provider-neutral: the prepared context works with OpenAI, Qwen, Llama or any other chat provider, and
 * retrieval problems degrade to an answer without knowledge plus a notice.
 */
@Component
public class RagChatAugmenter implements KnowledgeAugmenter {

    private static final Logger log = LoggerFactory.getLogger(RagChatAugmenter.class);

    private final RagContextService contextService;
    private final RagExecutionRecorder recorder;
    private final RagExecutionRepository executions;
    private final KnowledgeCollectionService collections;
    private final KnowledgeAccessPolicy accessPolicy;
    private final RagProperties properties;

    public RagChatAugmenter(RagContextService contextService, RagExecutionRecorder recorder,
                            RagExecutionRepository executions, KnowledgeCollectionService collections,
                            KnowledgeAccessPolicy accessPolicy, RagProperties properties) {
        this.contextService = contextService;
        this.recorder = recorder;
        this.executions = executions;
        this.collections = collections;
        this.accessPolicy = accessPolicy;
        this.properties = properties;
    }

    @Override
    public boolean isEnabled() {
        return properties.enabled();
    }

    @Override
    public KnowledgeContext augment(KnowledgeQuery query) {
        if (!properties.enabled()) {
            return KnowledgeContext.NONE;
        }
        RagExecutionRecorder.Scope scope = new RagExecutionRecorder.Scope(query.userId(), query.conversationId(),
                query.assistantMessageId(), query.collectionIds());
        long started = System.nanoTime();
        try {
            Set<UUID> readable = readableCollections(query);
            if (readable.isEmpty()) {
                return KnowledgeContext.withoutKnowledge(0, "None of the selected knowledge collections is "
                        + "available to you, so this answer does not use the knowledge base.");
            }
            RagPreparation preparation = contextService.prepare(query.question(),
                    RetrievalOptions.forCollections(readable));
            recorder.success(RagExecution.Purpose.CHAT, scope, preparation);
            RagContext context = preparation.context();
            int retrieved = preparation.retrieval().chunks().size();
            log.info("Knowledge retrieval conversation={} retrieved={} inContext={} tokens={} ms={}",
                    query.conversationId(), retrieved, context.includedChunks().size(), context.tokens(),
                    preparation.retrieval().totalMs());
            if (context.isEmpty()) {
                return KnowledgeContext.withoutKnowledge(retrieved,
                        "No matching knowledge was found, so this answer is not based on the knowledge base.");
            }
            List<KnowledgeCitation> citations = context.citations().stream().map(c -> new KnowledgeCitation(
                    c.index(), c.sourceId(), c.chunkId(), c.title(), c.pageNumber(), c.sectionTitle(),
                    c.collectionName(), c.similarity())).toList();
            return new KnowledgeContext(true, context.instructions(), context.knowledgeBlock(), citations,
                    context.tokens(), retrieved, null);
        } catch (RuntimeException e) {
            log.warn("Knowledge retrieval failed for conversation {}: {}", query.conversationId(), e.getMessage());
            recorder.failure(RagExecution.Purpose.CHAT, scope, query.question(),
                    properties.retrieval().mode().name(), e, Times.elapsedMillis(started));
            return KnowledgeContext.withoutKnowledge(0,
                    "The knowledge base could not be searched right now, so this answer does not use it.");
        }
    }

    /** Requested collections (all when none were selected) that are enabled and readable by the user. */
    private Set<UUID> readableCollections(KnowledgeQuery query) {
        return collections.enabled().stream()
                .filter(c -> query.collectionIds().isEmpty() || query.collectionIds().contains(c.id()))
                .filter(c -> accessPolicy.canRead(query.userId(), c))
                .map(KnowledgeCollection::id)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public Map<UUID, List<KnowledgeCitation>> citations(Collection<UUID> assistantMessageIds) {
        Map<UUID, List<KnowledgeCitation>> result = new LinkedHashMap<>();
        executions.citedChunksByMessageIds(assistantMessageIds).forEach((messageId, chunks) ->
                result.put(messageId, chunks.stream().map(c -> new KnowledgeCitation(c.citationIndex(),
                        c.sourceId(), c.chunkId(), c.sourceTitle(), c.pageNumber(), c.sectionTitle(),
                        c.collectionName(), c.similarityScore())).toList()));
        return result;
    }
}
