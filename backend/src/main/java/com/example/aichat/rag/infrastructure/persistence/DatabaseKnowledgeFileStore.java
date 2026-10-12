package com.example.aichat.rag.infrastructure.persistence;

import com.example.aichat.rag.domain.KnowledgeFileStore;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Stores original files in PostgreSQL ({@code bytea}). Keeps uploads transactional with their metadata and is
 * removed together with the source by cascade. Large deployments can swap in an object-storage implementation.
 */
@Repository
public class DatabaseKnowledgeFileStore implements KnowledgeFileStore {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public DatabaseKnowledgeFileStore(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public void save(UUID sourceId, byte[] content) {
        jdbc.update("""
                INSERT INTO knowledge_source_files (source_id, content, created_at) VALUES (?, ?, ?)
                ON CONFLICT (source_id) DO UPDATE SET content = EXCLUDED.content, created_at = EXCLUDED.created_at""",
                sourceId, content, Timestamp.from(clock.instant()));
    }

    @Override
    public Optional<byte[]> load(UUID sourceId) {
        return jdbc.query("SELECT content FROM knowledge_source_files WHERE source_id = ?",
                (rs, i) -> rs.getBytes("content"), sourceId).stream().findFirst();
    }

    @Override
    public void delete(UUID sourceId) {
        jdbc.update("DELETE FROM knowledge_source_files WHERE source_id = ?", sourceId);
    }
}
