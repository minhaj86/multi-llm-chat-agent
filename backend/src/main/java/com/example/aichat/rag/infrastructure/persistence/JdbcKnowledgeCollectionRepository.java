package com.example.aichat.rag.infrastructure.persistence;

import static com.example.aichat.rag.infrastructure.persistence.JdbcSupport.instant;
import static com.example.aichat.rag.infrastructure.persistence.JdbcSupport.timestamp;

import com.example.aichat.rag.domain.KnowledgeCollection;
import com.example.aichat.rag.domain.KnowledgeCollectionRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcKnowledgeCollectionRepository implements KnowledgeCollectionRepository {

    private static final String COLUMNS = "id, name, description, enabled, is_default, created_at, updated_at";

    private final JdbcTemplate jdbc;

    public JdbcKnowledgeCollectionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<KnowledgeCollection> findAll() {
        return jdbc.query("SELECT " + COLUMNS + " FROM knowledge_collections ORDER BY is_default DESC, LOWER(name)",
                JdbcKnowledgeCollectionRepository::map);
    }

    @Override
    public Optional<KnowledgeCollection> findById(UUID id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM knowledge_collections WHERE id = ?",
                JdbcKnowledgeCollectionRepository::map, id).stream().findFirst();
    }

    @Override
    public Optional<KnowledgeCollection> findDefault() {
        return jdbc.query("SELECT " + COLUMNS + " FROM knowledge_collections WHERE is_default",
                JdbcKnowledgeCollectionRepository::map).stream().findFirst();
    }

    @Override
    public boolean existsByName(String name, UUID excludeId) {
        Boolean exists = excludeId == null
                ? jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM knowledge_collections WHERE LOWER(name) = LOWER(?))",
                Boolean.class, name)
                : jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM knowledge_collections WHERE LOWER(name) = LOWER(?) "
                + "AND id <> ?)", Boolean.class, name, excludeId);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public KnowledgeCollection save(KnowledgeCollection c) {
        jdbc.update("""
                        INSERT INTO knowledge_collections (id, name, description, enabled, is_default, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, description = EXCLUDED.description,
                            enabled = EXCLUDED.enabled, updated_at = EXCLUDED.updated_at""",
                c.id(), c.name(), c.description(), c.enabled(), c.defaultCollection(), timestamp(c.createdAt()),
                timestamp(c.updatedAt()));
        return c;
    }

    @Override
    public void delete(UUID id) {
        jdbc.update("DELETE FROM knowledge_collections WHERE id = ?", id);
    }

    @Override
    public Map<UUID, CollectionUsage> usage() {
        Map<UUID, CollectionUsage> usage = new HashMap<>();
        jdbc.query("""
                SELECT collection_id, COUNT(*) AS sources, COUNT(*) FILTER (WHERE enabled) AS enabled_sources,
                       COALESCE(SUM(chunk_count), 0) AS chunks, COALESCE(SUM(file_size), 0) AS bytes,
                       MAX(updated_at) AS last_updated
                FROM knowledge_sources GROUP BY collection_id""", rs -> {
            usage.put(rs.getObject("collection_id", UUID.class), new CollectionUsage(rs.getLong("sources"),
                    rs.getLong("enabled_sources"), rs.getLong("chunks"), rs.getLong("bytes"),
                    instant(rs, "last_updated")));
        });
        return usage;
    }

    private static KnowledgeCollection map(ResultSet rs, int row) throws SQLException {
        return new KnowledgeCollection(rs.getObject("id", UUID.class), rs.getString("name"),
                rs.getString("description"), rs.getBoolean("enabled"), rs.getBoolean("is_default"),
                instant(rs, "created_at"), instant(rs, "updated_at"));
    }
}
