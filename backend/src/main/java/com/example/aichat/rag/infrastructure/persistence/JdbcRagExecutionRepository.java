package com.example.aichat.rag.infrastructure.persistence;

import static com.example.aichat.rag.infrastructure.persistence.JdbcSupport.nullableDouble;
import static com.example.aichat.rag.infrastructure.persistence.JdbcSupport.nullableInt;
import static com.example.aichat.rag.infrastructure.persistence.JdbcSupport.timestamp;
import static com.example.aichat.rag.infrastructure.persistence.JdbcSupport.uuid;
import static com.example.aichat.rag.infrastructure.persistence.JdbcSupport.uuidArray;

import com.example.aichat.rag.domain.RagExecution;
import com.example.aichat.rag.domain.RagExecutionRepository;
import com.example.aichat.rag.domain.RagRetrievedChunk;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcRagExecutionRepository implements RagExecutionRepository {

    private final JdbcTemplate jdbc;

    public JdbcRagExecutionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(RagExecution e) {
        jdbc.update("""
                        INSERT INTO rag_executions (id, purpose, conversation_id, message_id, user_id, query, collection_ids,
                            embedding_provider, embedding_model, retrieval_mode, retrieved_chunk_count, context_chunk_count,
                            total_context_tokens, embedding_duration_ms, retrieval_duration_ms, status, error_message,
                            created_at)
                        VALUES (?, ?, ?, ?, ?, ?, CAST(? AS uuid[]), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                e.id(), e.purpose().name(), e.conversationId(), e.messageId(), e.userId(), e.query(),
                uuidArray(e.collectionIds()), e.embeddingProvider(), e.embeddingModel(), e.retrievalMode(),
                e.retrievedChunkCount(), e.contextChunkCount(), e.totalContextTokens(), e.embeddingDurationMs(),
                e.retrievalDurationMs(), e.status().name(), truncate(e.errorMessage(), 500),
                timestamp(e.createdAt()));
        if (e.chunks().isEmpty()) {
            return;
        }
        jdbc.batchUpdate("""
                INSERT INTO rag_retrieved_chunks (rag_execution_id, rank, chunk_id, source_id, source_title,
                    collection_name, chunk_index, page_number, section_title, similarity_score, keyword_score,
                    reranker_score, citation_index)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", e.chunks(), 200, (ps, c) -> {
            ps.setObject(1, e.id());
            ps.setInt(2, c.rank());
            ps.setObject(3, c.chunkId());
            ps.setObject(4, c.sourceId());
            ps.setString(5, truncate(c.sourceTitle(), 300));
            ps.setString(6, truncate(c.collectionName(), 100));
            setInt(ps, 7, c.chunkIndex());
            setInt(ps, 8, c.pageNumber());
            ps.setString(9, truncate(c.sectionTitle(), 300));
            setDouble(ps, 10, c.similarityScore());
            setDouble(ps, 11, c.keywordScore());
            setDouble(ps, 12, c.rerankerScore());
            setInt(ps, 13, c.citationIndex());
        });
    }

    @Override
    public Map<UUID, List<RagRetrievedChunk>> citedChunksByMessageIds(Collection<UUID> messageIds) {
        Map<UUID, List<RagRetrievedChunk>> result = new LinkedHashMap<>();
        if (messageIds.isEmpty()) {
            return result;
        }
        jdbc.query("""
                SELECT e.message_id, c.rank, c.chunk_id, c.source_id, c.source_title, c.collection_name, c.chunk_index,
                       c.page_number, c.section_title, c.similarity_score, c.keyword_score, c.reranker_score,
                       c.citation_index
                FROM rag_retrieved_chunks c
                JOIN rag_executions e ON e.id = c.rag_execution_id
                WHERE e.message_id = ANY (CAST(? AS uuid[])) AND c.citation_index IS NOT NULL
                ORDER BY e.message_id, c.citation_index""", rs -> {
            result.computeIfAbsent(uuid(rs, "message_id"), id -> new ArrayList<>()).add(new RagRetrievedChunk(
                    rs.getInt("rank"), uuid(rs, "chunk_id"), uuid(rs, "source_id"), rs.getString("source_title"),
                    rs.getString("collection_name"), nullableInt(rs, "chunk_index"), nullableInt(rs, "page_number"),
                    rs.getString("section_title"), nullableDouble(rs, "similarity_score"),
                    nullableDouble(rs, "keyword_score"), nullableDouble(rs, "reranker_score"),
                    nullableInt(rs, "citation_index")));
        }, (Object) uuidArray(messageIds));
        return result;
    }

    @Override
    public RetrievalStats statsSince(Instant from) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) AS retrievals,
                       COUNT(*) FILTER (WHERE purpose = 'CHAT') AS chat_retrievals,
                       COUNT(*) FILTER (WHERE status = 'EMPTY') AS empty_retrievals,
                       COUNT(*) FILTER (WHERE status = 'ERROR') AS failed_retrievals,
                       COALESCE(AVG(retrieval_duration_ms), 0) AS avg_ms,
                       COALESCE(AVG(context_chunk_count) FILTER (WHERE status = 'SUCCESS'), 0) AS avg_chunks
                FROM rag_executions WHERE created_at >= ?""", (rs, i) -> new RetrievalStats(rs.getLong("retrievals"),
                rs.getLong("chat_retrievals"), rs.getLong("empty_retrievals"), rs.getLong("failed_retrievals"),
                rs.getDouble("avg_ms"), rs.getDouble("avg_chunks")), timestamp(from));
    }

    private static void setInt(java.sql.PreparedStatement ps, int index, Integer value) throws java.sql.SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    private static void setDouble(java.sql.PreparedStatement ps, int index, Double value)
            throws java.sql.SQLException {
        if (value == null) {
            ps.setNull(index, Types.DOUBLE);
        } else {
            ps.setDouble(index, value);
        }
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
