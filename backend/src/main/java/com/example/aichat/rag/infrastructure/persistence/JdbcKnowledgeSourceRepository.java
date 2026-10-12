package com.example.aichat.rag.infrastructure.persistence;

import static com.example.aichat.rag.infrastructure.persistence.JdbcSupport.instant;
import static com.example.aichat.rag.infrastructure.persistence.JdbcSupport.json;
import static com.example.aichat.rag.infrastructure.persistence.JdbcSupport.jsonMap;
import static com.example.aichat.rag.infrastructure.persistence.JdbcSupport.nullableInt;
import static com.example.aichat.rag.infrastructure.persistence.JdbcSupport.strings;
import static com.example.aichat.rag.infrastructure.persistence.JdbcSupport.textArray;
import static com.example.aichat.rag.infrastructure.persistence.JdbcSupport.timestamp;
import static com.example.aichat.rag.infrastructure.persistence.JdbcSupport.uuid;
import static com.example.aichat.rag.infrastructure.persistence.JdbcSupport.uuidArray;

import com.example.aichat.rag.domain.IngestionStatus;
import com.example.aichat.rag.domain.KnowledgeSource;
import com.example.aichat.rag.domain.KnowledgeSourceQuery;
import com.example.aichat.rag.domain.KnowledgeSourceRepository;
import com.example.aichat.rag.domain.KnowledgeSourceType;
import com.example.aichat.rag.domain.PageResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcKnowledgeSourceRepository implements KnowledgeSourceRepository {

    private static final String COLUMNS = """
            id, type, title, original_filename, mime_type, source_url, collection_id, status, enabled, tags, checksum,
            file_size, character_count, version, parent_source_id, created_by, created_at, updated_at, indexed_at,
            embedding_provider, embedding_model, chunk_count, error_message, ingestion_stats""";
    private static final int MAX_ERROR_LENGTH = 1000;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcKnowledgeSourceRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public void insert(KnowledgeSource s) {
        jdbc.update("INSERT INTO knowledge_sources (" + COLUMNS + ") VALUES "
                        + "(?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS text[]), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))",
                s.id(), s.type().name(), s.title(), s.originalFilename(), s.mimeType(), s.sourceUrl(),
                s.collectionId(), s.status().name(), s.enabled(), textArray(s.tags()), s.checksum(), s.fileSize(),
                s.characterCount(), s.version(), s.parentSourceId(), s.createdBy(), timestamp(s.createdAt()),
                timestamp(s.updatedAt()), timestamp(s.indexedAt()), s.embeddingProvider(), s.embeddingModel(),
                s.chunkCount(), truncate(s.errorMessage()), json(objectMapper, s.ingestionStats()));
    }

    @Override
    public Optional<KnowledgeSource> findById(UUID id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM knowledge_sources WHERE id = ?", this::map, id)
                .stream().findFirst();
    }

    @Override
    public Map<UUID, KnowledgeSource> findByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return jdbc.query("SELECT " + COLUMNS + " FROM knowledge_sources WHERE id = ANY (CAST(? AS uuid[]))",
                        this::map, (Object) uuidArray(ids)).stream()
                .collect(Collectors.toMap(KnowledgeSource::id, Function.identity()));
    }

    @Override
    public PageResult<KnowledgeSource> search(KnowledgeSourceQuery q) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (q.text() != null && !q.text().isBlank()) {
            where.append(" AND (title ILIKE ? ESCAPE '\\' OR original_filename ILIKE ? ESCAPE '\\')");
            String pattern = JdbcSupport.likePattern(q.text().strip());
            args.add(pattern);
            args.add(pattern);
        }
        if (q.collectionId() != null) {
            where.append(" AND collection_id = ?");
            args.add(q.collectionId());
        }
        if (q.type() != null) {
            where.append(" AND type = ?");
            args.add(q.type().name());
        }
        if (q.status() != null) {
            where.append(" AND status = ?");
            args.add(q.status().name());
        }
        if (q.enabled() != null) {
            where.append(" AND enabled = ?");
            args.add(q.enabled());
        }
        if (q.tag() != null && !q.tag().isBlank()) {
            where.append(" AND ? = ANY (tags)");
            args.add(q.tag().strip().toLowerCase(java.util.Locale.ROOT));
        }
        if (q.createdFrom() != null) {
            where.append(" AND created_at >= ?");
            args.add(timestamp(q.createdFrom()));
        }
        if (q.createdTo() != null) {
            where.append(" AND created_at < ?");
            args.add(timestamp(q.createdTo()));
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_sources" + where, Long.class, args.toArray());
        String order = switch (q.sort()) {
            case TITLE -> "LOWER(title)";
            case CREATED_AT -> "created_at";
            case UPDATED_AT -> "updated_at";
            case CHUNK_COUNT -> "chunk_count";
            case FILE_SIZE -> "file_size";
        } + (q.ascending() ? " ASC" : " DESC") + ", id";
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(q.size());
        pageArgs.add((long) q.page() * q.size());
        List<KnowledgeSource> items = jdbc.query("SELECT " + COLUMNS + " FROM knowledge_sources" + where
                + " ORDER BY " + order + " LIMIT ? OFFSET ?", this::map, pageArgs.toArray());
        return new PageResult<>(items, q.page(), q.size(), total == null ? 0 : total);
    }

    @Override
    public List<KnowledgeSource> findByChecksum(String checksum) {
        return jdbc.query("SELECT " + COLUMNS + " FROM knowledge_sources WHERE checksum = ? AND status <> 'DELETING' "
                + "ORDER BY version DESC, created_at DESC", this::map, checksum);
    }

    @Override
    public List<UUID> findIdsByStatus(Collection<IngestionStatus> statuses) {
        if (statuses.isEmpty()) {
            return List.of();
        }
        return jdbc.query("SELECT id FROM knowledge_sources WHERE status = ANY (CAST(? AS text[])) ORDER BY created_at",
                (rs, i) -> uuid(rs, "id"),
                (Object) textArray(statuses.stream().map(IngestionStatus::name).toList()));
    }

    @Override
    public List<UUID> findStaleIds(String activeModel) {
        return jdbc.query("SELECT id FROM knowledge_sources WHERE status = 'INDEXED' AND embedding_model IS NOT NULL "
                + "AND embedding_model <> ? ORDER BY created_at", (rs, i) -> uuid(rs, "id"), activeModel);
    }

    @Override
    public boolean updateMetadata(UUID id, String title, UUID collectionId, List<String> tags, String sourceUrl,
                                  Instant now) {
        return jdbc.update("UPDATE knowledge_sources SET title = ?, collection_id = ?, tags = CAST(? AS text[]), "
                        + "source_url = ?, updated_at = ? WHERE id = ?", title, collectionId, textArray(tags), sourceUrl,
                timestamp(now), id) > 0;
    }

    @Override
    public boolean updateEnabled(UUID id, boolean enabled, Instant now) {
        return jdbc.update("UPDATE knowledge_sources SET enabled = ?, updated_at = ? WHERE id = ?", enabled,
                timestamp(now), id) > 0;
    }

    @Override
    public boolean updateStatus(UUID id, IngestionStatus status, String errorMessage, Instant now) {
        return jdbc.update("UPDATE knowledge_sources SET status = ?, error_message = ?, updated_at = ? "
                        + "WHERE id = ? AND status <> 'DELETING'", status.name(), truncate(errorMessage), timestamp(now),
                id) > 0;
    }

    @Override
    public void markIndexed(UUID id, IndexingResult r, Instant now) {
        jdbc.update("""
                        UPDATE knowledge_sources SET status = 'INDEXED', error_message = NULL, indexed_at = ?, updated_at = ?,
                            embedding_provider = ?, embedding_model = ?, chunk_count = ?, character_count = ?,
                            ingestion_stats = CAST(? AS jsonb)
                        WHERE id = ?""", timestamp(now), timestamp(now), r.embeddingProvider(), r.embeddingModel(),
                r.chunkCount(), r.characterCount(), json(objectMapper, r.stats()), id);
    }

    @Override
    public void markFailed(UUID id, String errorMessage, Map<String, Object> stats, Instant now) {
        jdbc.update("UPDATE knowledge_sources SET status = 'FAILED', error_message = ?, updated_at = ?, "
                        + "ingestion_stats = CAST(? AS jsonb) WHERE id = ? AND status <> 'DELETING'",
                truncate(errorMessage), timestamp(now), json(objectMapper, stats), id);
    }

    @Override
    public void replaceContent(UUID id, ContentReplacement r, Instant now) {
        jdbc.update("""
                        UPDATE knowledge_sources SET title = ?, original_filename = ?, mime_type = ?, checksum = ?,
                            file_size = ?, version = ?, status = 'UPLOADED', error_message = NULL, chunk_count = 0,
                            character_count = NULL, indexed_at = NULL, embedding_provider = NULL, embedding_model = NULL,
                            ingestion_stats = '{}', updated_at = ?
                        WHERE id = ?""", r.title(), r.originalFilename(), r.mimeType(), r.checksum(), r.fileSize(),
                r.version(), timestamp(now), id);
    }

    @Override
    public Optional<IngestionStatus> lockStatus(UUID id) {
        return jdbc.query("SELECT status FROM knowledge_sources WHERE id = ? FOR UPDATE",
                (rs, i) -> IngestionStatus.valueOf(rs.getString("status")), id).stream().findFirst();
    }

    @Override
    public void delete(UUID id) {
        jdbc.update("DELETE FROM knowledge_sources WHERE id = ?", id);
    }

    @Override
    public KnowledgeTotals totals() {
        Map<String, Long> byModel = new LinkedHashMap<>();
        jdbc.query("SELECT embedding_model, SUM(chunk_count) AS chunks FROM knowledge_sources WHERE status = 'INDEXED' "
                + "AND embedding_model IS NOT NULL GROUP BY embedding_model ORDER BY chunks DESC", rs -> {
            byModel.put(rs.getString("embedding_model"), rs.getLong("chunks"));
        });
        return jdbc.queryForObject("""
                SELECT COUNT(*) AS sources,
                       COUNT(*) FILTER (WHERE type = 'FILE') AS files,
                       COUNT(*) FILTER (WHERE type = 'TEXT') AS texts,
                       COUNT(*) FILTER (WHERE status = 'INDEXED') AS indexed,
                       COUNT(*) FILTER (WHERE status = 'FAILED') AS failed,
                       COUNT(*) FILTER (WHERE status IN ('UPLOADED', 'EXTRACTING', 'EXTRACTED', 'CHUNKING', 'EMBEDDING'))
                           AS processing,
                       COUNT(*) FILTER (WHERE NOT enabled) AS disabled,
                       COALESCE(SUM(chunk_count), 0) AS chunks,
                       COALESCE(SUM(file_size), 0) AS bytes
                FROM knowledge_sources""", (rs, i) -> new KnowledgeTotals(rs.getLong("sources"), rs.getLong("files"),
                rs.getLong("texts"), rs.getLong("indexed"), rs.getLong("failed"), rs.getLong("processing"),
                rs.getLong("disabled"), rs.getLong("chunks"), rs.getLong("bytes"), byModel));
    }

    @Override
    public List<InventoryEntry> inventory() {
        return jdbc.query("""
                SELECT collection_id, status, embedding_model, COUNT(*) AS sources,
                       COALESCE(SUM(chunk_count), 0) AS chunks
                FROM knowledge_sources GROUP BY collection_id, status, embedding_model""",
                (rs, i) -> new InventoryEntry(uuid(rs, "collection_id"), IngestionStatus.valueOf(rs.getString("status")),
                        rs.getString("embedding_model"), rs.getLong("sources"), rs.getLong("chunks")));
    }

    private KnowledgeSource map(ResultSet rs, int row) throws SQLException {
        return new KnowledgeSource(uuid(rs, "id"), KnowledgeSourceType.valueOf(rs.getString("type")),
                rs.getString("title"), rs.getString("original_filename"), rs.getString("mime_type"),
                rs.getString("source_url"), uuid(rs, "collection_id"), IngestionStatus.valueOf(rs.getString("status")),
                rs.getBoolean("enabled"), strings(rs, "tags"), rs.getString("checksum"), rs.getLong("file_size"),
                nullableInt(rs, "character_count"), rs.getInt("version"), uuid(rs, "parent_source_id"),
                uuid(rs, "created_by"), instant(rs, "created_at"), instant(rs, "updated_at"),
                instant(rs, "indexed_at"), rs.getString("embedding_provider"), rs.getString("embedding_model"),
                rs.getInt("chunk_count"), rs.getString("error_message"),
                jsonMap(objectMapper, rs.getString("ingestion_stats")));
    }

    private static String truncate(String message) {
        return message == null || message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }
}
