package com.example.aichat.rag.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persistence port for retrieval records.
 */
public interface RagExecutionRepository {

    void save(RagExecution execution);

    /** Chunks that were cited (sent to the LLM) per assistant message, ordered by citation number. */
    Map<UUID, List<RagRetrievedChunk>> citedChunksByMessageIds(Collection<UUID> messageIds);

    RetrievalStats statsSince(Instant from);

    record RetrievalStats(long retrievals, long chatRetrievals, long emptyRetrievals, long failedRetrievals,
                          double averageRetrievalMs, double averageChunksInContext) {
    }
}
