package com.example.aichat.rag.infrastructure.persistence;

import static com.example.aichat.rag.infrastructure.persistence.JdbcSupport.instant;
import static com.example.aichat.rag.infrastructure.persistence.JdbcSupport.json;
import static com.example.aichat.rag.infrastructure.persistence.JdbcSupport.jsonMap;
import static com.example.aichat.rag.infrastructure.persistence.JdbcSupport.nullableInt;
import static com.example.aichat.rag.infrastructure.persistence.JdbcSupport.timestamp;
import static com.example.aichat.rag.infrastructure.persistence.JdbcSupport.uuid;
import static com.example.aichat.rag.infrastructure.persistence.JdbcSupport.uuidArray;

import com.example.aichat.rag.domain.KnowledgeChunk;
import com.example.aichat.rag.domain.KnowledgeChunkRepository;
import com.example.aichat.rag.domain.PageResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcKnowledgeChunkRepository implements KnowledgeChunkRepository {

    private static final String COLUMNS = """
            id, source_id, collection_id, chunk_index, content, token_count, character_start, character_end,
            page_number, section_title, metadata, created_at""";
    private static final int BATCH_SIZE = 500;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcKnowledgeChunkRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public void replaceForSource(UUID sourceId, List<KnowledgeChunk> chunks) {
        deleteBySourceId(sourceId);
        jdbc.batchUpdate("INSERT INTO knowledge_chunks (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
                + "CAST(? AS jsonb), ?)", chunks, BATCH_SIZE, (ps, c) -> {
            ps.setObject(1, c.id());
            ps.setObject(2, c.sourceId());
            ps.setObject(3, c.collectionId());
            ps.setInt(4, c.chunkIndex());
            ps.setString(5, c.content());
            ps.setInt(6, c.tokenCount());
            ps.setInt(7, c.characterStart());
            ps.setInt(8, c.characterEnd());
            if (c.pageNumber() == null) {
                ps.setNull(9, Types.INTEGER);
            } else {
                ps.setInt(9, c.pageNumber());
            }
            ps.setString(10, c.sectionTitle() == null ? null : truncate(c.sectionTitle(), 300));
            ps.setString(11, json(objectMapper, c.metadata()));
            ps.setTimestamp(12, timestamp(c.createdAt()));
        });
    }

    @Override
    public void deleteBySourceId(UUID sourceId) {
        jdbc.update("DELETE FROM knowledge_chunks WHERE source_id = ?", sourceId);
    }

    @Override
    public PageResult<KnowledgeChunk> findBySourceId(UUID sourceId, int page, int size) {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_chunks WHERE source_id = ?", Long.class,
                sourceId);
        List<KnowledgeChunk> items = jdbc.query("SELECT " + COLUMNS + " FROM knowledge_chunks WHERE source_id = ? "
                + "ORDER BY chunk_index LIMIT ? OFFSET ?", this::map, sourceId, size, (long) page * size);
        return new PageResult<>(items, page, size, total == null ? 0 : total);
    }

    @Override
    public Optional<KnowledgeChunk> findById(UUID chunkId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM knowledge_chunks WHERE id = ?", this::map, chunkId)
                .stream().findFirst();
    }

    @Override
    public List<KnowledgeChunk> findByIds(Collection<UUID> chunkIds) {
        if (chunkIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query("SELECT " + COLUMNS + " FROM knowledge_chunks WHERE id = ANY (CAST(? AS uuid[]))", this::map,
                (Object) uuidArray(chunkIds));
    }

    @Override
    public long countBySourceId(UUID sourceId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_chunks WHERE source_id = ?", Long.class,
                sourceId);
        return count == null ? 0 : count;
    }

    @Override
    public void updateCollection(UUID sourceId, UUID collectionId) {
        jdbc.update("UPDATE knowledge_chunks SET collection_id = ? WHERE source_id = ?", collectionId, sourceId);
    }

    private KnowledgeChunk map(ResultSet rs, int row) throws SQLException {
        return new KnowledgeChunk(uuid(rs, "id"), uuid(rs, "source_id"), uuid(rs, "collection_id"),
                rs.getInt("chunk_index"), rs.getString("content"), rs.getInt("token_count"),
                rs.getInt("character_start"), rs.getInt("character_end"), nullableInt(rs, "page_number"),
                rs.getString("section_title"), jsonMap(objectMapper, rs.getString("metadata")),
                instant(rs, "created_at"));
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
