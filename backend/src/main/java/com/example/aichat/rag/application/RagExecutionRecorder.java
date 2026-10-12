package com.example.aichat.rag.application;

import com.example.aichat.rag.application.RagContextService.RagPreparation;
import com.example.aichat.rag.domain.RagExecution;
import com.example.aichat.rag.domain.RagExecutionRepository;
import com.example.aichat.rag.domain.RagRetrievedChunk;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Persists a {@link RagExecution} for every retrieval (query, scores, citation numbers, timings) to analyse
 * retrieval quality. Recording problems are logged and never break chat.
 */
@Component
public class RagExecutionRecorder {

    private static final Logger log = LoggerFactory.getLogger(RagExecutionRecorder.class);

    private final RagExecutionRepository executions;
    private final EmbeddingService embedding;
    private final Clock clock;

    public RagExecutionRecorder(RagExecutionRepository executions, EmbeddingService embedding, Clock clock) {
        this.executions = executions;
        this.embedding = embedding;
        this.clock = clock;
    }

    public void success(RagExecution.Purpose purpose, Scope scope, RagPreparation preparation) {
        RetrievalResult retrieval = preparation.retrieval();
        RagContext context = preparation.context();
        Map<UUID, Integer> citationByChunk = new HashMap<>();
        context.citations().forEach(c -> citationByChunk.put(c.chunkId(), c.index()));
        List<RagRetrievedChunk> chunks = new ArrayList<>();
        for (int i = 0; i < retrieval.chunks().size(); i++) {
            RetrievedChunk chunk = retrieval.chunks().get(i);
            chunks.add(new RagRetrievedChunk(i + 1, chunk.chunkId(), chunk.sourceId(), chunk.sourceTitle(),
                    chunk.collectionName(), chunk.chunkIndex(), chunk.pageNumber(), chunk.sectionTitle(),
                    chunk.similarity(), chunk.keywordScore(), chunk.rerankScore(), citationByChunk.get(chunk.chunkId())));
        }
        save(new RagExecution(UUID.randomUUID(), purpose, scope.conversationId(), scope.messageId(), scope.userId(),
                retrieval.query(), scope.requestedCollections(), retrieval.embeddingProvider(),
                retrieval.embeddingModel(), retrieval.mode().name(), retrieval.chunks().size(),
                context.includedChunks().size(), context.tokens(), retrieval.embeddingMs(), retrieval.totalMs(),
                context.isEmpty() ? RagExecution.Status.EMPTY : RagExecution.Status.SUCCESS, null, clock.instant(),
                chunks));
    }

    public void failure(RagExecution.Purpose purpose, Scope scope, String query, String retrievalMode,
                        Throwable error, long durationMs) {
        save(new RagExecution(UUID.randomUUID(), purpose, scope.conversationId(), scope.messageId(), scope.userId(),
                query, scope.requestedCollections(), embedding.providerId(), embedding.modelId(), retrievalMode, 0, 0,
                0, null, durationMs, RagExecution.Status.ERROR, error.getMessage(), clock.instant(), List.of()));
    }

    private void save(RagExecution execution) {
        try {
            executions.save(execution);
        } catch (RuntimeException e) {
            log.warn("Could not record retrieval {}: {}", execution.id(), e.getMessage());
        }
    }

    public record Scope(UUID userId, UUID conversationId, UUID messageId, Set<UUID> requestedCollections) {
    }
}
