package com.example.aichat.rag.infrastructure.persistence;

import static com.example.aichat.rag.infrastructure.persistence.JdbcSupport.instant;
import static com.example.aichat.rag.infrastructure.persistence.JdbcSupport.timestamp;

import com.example.aichat.rag.domain.ExtractedDocument;
import com.example.aichat.rag.domain.ExtractedDocument.PageSpan;
import com.example.aichat.rag.domain.ExtractedDocument.SectionSpan;
import com.example.aichat.rag.domain.KnowledgeContentRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcKnowledgeContentRepository implements KnowledgeContentRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcKnowledgeContentRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(UUID sourceId, ExtractedDocument document, Instant extractedAt) {
        Structure structure = new Structure(document.pages(), document.sections(), document.properties());
        jdbc.update("""
                        INSERT INTO knowledge_source_contents (source_id, content, structure, character_count, extracted_at)
                        VALUES (?, ?, CAST(? AS jsonb), ?, ?)
                        ON CONFLICT (source_id) DO UPDATE SET content = EXCLUDED.content, structure = EXCLUDED.structure,
                            character_count = EXCLUDED.character_count, extracted_at = EXCLUDED.extracted_at""",
                sourceId, document.text(), write(structure), document.text().length(), timestamp(extractedAt));
    }

    @Override
    public Optional<StoredContent> find(UUID sourceId) {
        return jdbc.query("SELECT content, structure, extracted_at FROM knowledge_source_contents WHERE source_id = ?",
                (rs, i) -> {
                    Structure structure = read(rs.getString("structure"));
                    ExtractedDocument document = new ExtractedDocument(null, rs.getString("content"),
                            structure.pages(), structure.sections(), structure.properties());
                    return new StoredContent(document, instant(rs, "extracted_at"));
                }, sourceId).stream().findFirst();
    }

    @Override
    public void delete(UUID sourceId) {
        jdbc.update("DELETE FROM knowledge_source_contents WHERE source_id = ?", sourceId);
    }

    private String write(Structure structure) {
        try {
            return objectMapper.writeValueAsString(structure);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise document structure", e);
        }
    }

    private Structure read(String json) {
        if (json == null || json.isBlank() || "{}".equals(json.strip())) {
            return new Structure(List.of(), List.of(), Map.of());
        }
        try {
            return objectMapper.readValue(json, Structure.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not read document structure", e);
        }
    }

    record Structure(List<PageSpan> pages, List<SectionSpan> sections, Map<String, String> properties) {

        Structure {
            pages = pages == null ? List.of() : pages;
            sections = sections == null ? List.of() : sections;
            properties = properties == null ? Map.of() : properties;
        }
    }
}
