package com.example.aichat.rag.infrastructure.vector;

import com.example.aichat.rag.application.RagProperties;
import com.example.aichat.rag.domain.VectorRecord;
import com.example.aichat.rag.domain.VectorSearchRequest;
import com.example.aichat.rag.domain.VectorSearchResult;
import com.example.aichat.rag.domain.VectorStore;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@link VectorStore} on PostgreSQL with the pgvector extension.
 *
 * <p>Vectors of several embedding models can coexist (column type {@code vector} without a fixed dimension); every
 * query is restricted to one model and dimension. For each dimension up to 2000 an HNSW cosine index on
 * {@code embedding::vector(n)} is created on first use, and queries use the same expression so the planner can
 * use it. Filtered searches enable pgvector's iterative index scan so that filters do not starve the result list.
 */
@Component
public class PgVectorStore implements VectorStore {

    private static final Logger log = LoggerFactory.getLogger(PgVectorStore.class);
    private static final int MAX_INDEXED_DIMENSIONS = 2000;
    private static final int BATCH_SIZE = 200;

    private final JdbcTemplate jdbc;
    private final RagProperties properties;
    private final TransactionTemplate readTransaction;
    private final TransactionTemplate indexTransaction;
    private final Clock clock;
    private final Set<Integer> preparedDimensions = ConcurrentHashMap.newKeySet();

    public PgVectorStore(JdbcTemplate jdbc, RagProperties properties, PlatformTransactionManager transactionManager,
                         Clock clock) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.clock = clock;
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);
        this.indexTransaction = new TransactionTemplate(transactionManager);
        this.indexTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public String id() {
        return "pgvector";
    }

    @Override
    public void prepare(String embeddingModel, int dimensions) {
        if (dimensions <= 0 || !properties.vector().createIndex() || preparedDimensions.contains(dimensions)) {
            return;
        }
        if (dimensions > MAX_INDEXED_DIMENSIONS) {
            log.info("Embedding dimension {} exceeds the pgvector HNSW limit ({}); exact search is used",
                    dimensions, MAX_INDEXED_DIMENSIONS);
            preparedDimensions.add(dimensions);
            return;
        }
        try {
            indexTransaction.executeWithoutResult(status -> jdbc.execute(
                    "CREATE INDEX IF NOT EXISTS ix_knowledge_vectors_hnsw_" + dimensions + " ON knowledge_vectors "
                            + "USING hnsw ((embedding::vector(" + dimensions + ")) vector_cosine_ops) "
                            + "WHERE dimensions = " + dimensions));
            preparedDimensions.add(dimensions);
            log.info("HNSW index ready for {}-dimensional embeddings", dimensions);
        } catch (RuntimeException e) {
            log.warn("Could not create the HNSW index for {} dimensions; exact search is used: {}", dimensions,
                    e.getMessage());
        }
    }

    @Override
    public void store(List<VectorRecord> records) {
        if (records.isEmpty()) {
            return;
        }
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.batchUpdate("""
                INSERT INTO knowledge_vectors (chunk_id, source_id, collection_id, embedding_model, dimensions, embedding,
                                               source_enabled, tags, created_at)
                VALUES (?, ?, ?, ?, ?, CAST(? AS vector), ?, CAST(? AS text[]), ?)
                ON CONFLICT (chunk_id) DO UPDATE SET source_id = EXCLUDED.source_id,
                    collection_id = EXCLUDED.collection_id, embedding_model = EXCLUDED.embedding_model,
                    dimensions = EXCLUDED.dimensions, embedding = EXCLUDED.embedding,
                    source_enabled = EXCLUDED.source_enabled, tags = EXCLUDED.tags""", records, BATCH_SIZE, (ps, r) -> {
            ps.setObject(1, r.chunkId());
            ps.setObject(2, r.sourceId());
            ps.setObject(3, r.collectionId());
            ps.setString(4, r.embeddingModel());
            ps.setInt(5, r.embedding().length);
            ps.setString(6, literal(r.embedding()));
            ps.setBoolean(7, r.sourceEnabled());
            ps.setObject(8, r.tags().toArray(String[]::new));
            ps.setTimestamp(9, now);
        });
    }

    @Override
    public List<VectorSearchResult> search(VectorSearchRequest request) {
        if (request.filter().collectionIds().isEmpty()) {
            return List.of();
        }
        int dimensions = request.embedding().length;
        String cast = "::vector(" + dimensions + ")";
        StringBuilder sql = new StringBuilder("SELECT chunk_id, source_id, 1 - (embedding" + cast
                + " <=> CAST(? AS vector)" + cast + ") AS similarity FROM knowledge_vectors "
                + "WHERE source_enabled AND embedding_model = ? AND dimensions = ? "
                + "AND collection_id = ANY (CAST(? AS uuid[]))");
        String vector = literal(request.embedding());
        List<Object> args = new ArrayList<>();
        args.add(vector);
        args.add(request.embeddingModel());
        args.add(dimensions);
        args.add(request.filter().collectionIds().stream().map(UUID::toString).toArray(String[]::new));
        if (!request.filter().sourceIds().isEmpty()) {
            sql.append(" AND source_id = ANY (CAST(? AS uuid[]))");
            args.add(request.filter().sourceIds().stream().map(UUID::toString).toArray(String[]::new));
        }
        if (!request.filter().tags().isEmpty()) {
            sql.append(" AND tags && CAST(? AS text[])");
            args.add(request.filter().tags().toArray(String[]::new));
        }
        sql.append(" ORDER BY embedding").append(cast).append(" <=> CAST(? AS vector)").append(cast)
                .append(" LIMIT ?");
        args.add(vector);
        args.add(request.limit());
        return readTransaction.execute(status -> {
            jdbc.query("SELECT set_config('hnsw.ef_search', ?, true), "
                            + "set_config('hnsw.iterative_scan', 'relaxed_order', true)", (RowCallbackHandler) rs -> {
                    }, Integer.toString(Math.max(request.limit(), properties.vector().efSearch())));
            return jdbc.query(sql.toString(), (rs, i) -> new VectorSearchResult(rs.getObject("chunk_id", UUID.class),
                    rs.getObject("source_id", UUID.class), rs.getDouble("similarity")), args.toArray());
        });
    }

    @Override
    public void deleteBySourceId(UUID sourceId) {
        jdbc.update("DELETE FROM knowledge_vectors WHERE source_id = ?", sourceId);
    }

    @Override
    public void deleteByCollectionId(UUID collectionId) {
        jdbc.update("DELETE FROM knowledge_vectors WHERE collection_id = ?", collectionId);
    }

    @Override
    public void updateSourceMetadata(UUID sourceId, UUID collectionId, boolean enabled, List<String> tags) {
        jdbc.update("UPDATE knowledge_vectors SET collection_id = ?, source_enabled = ?, tags = CAST(? AS text[]) "
                + "WHERE source_id = ?", collectionId, enabled, tags.toArray(String[]::new), sourceId);
    }

    @Override
    public long countBySourceId(UUID sourceId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_vectors WHERE source_id = ?", Long.class,
                sourceId);
        return count == null ? 0 : count;
    }

    /** pgvector text format: {@code [0.1,0.2,...]}. */
    static String literal(float[] vector) {
        StringBuilder text = new StringBuilder(vector.length * 10 + 2).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                text.append(',');
            }
            float value = vector[i];
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("Embedding contains a non-finite value");
            }
            text.append(value);
        }
        return text.append(']').toString();
    }
}
