package com.example.aichat.rag.infrastructure.persistence;

import static com.example.aichat.rag.infrastructure.persistence.JdbcSupport.textArray;
import static com.example.aichat.rag.infrastructure.persistence.JdbcSupport.uuid;
import static com.example.aichat.rag.infrastructure.persistence.JdbcSupport.uuidArray;

import com.example.aichat.rag.domain.KeywordSearcher;
import com.example.aichat.rag.domain.VectorFilter;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Keyword retrieval with PostgreSQL full-text search over {@code knowledge_chunks.content_tsv} ({@code simple}
 * configuration: language-neutral, no stemming). Questions are turned into an OR query of their significant words,
 * ranked with {@code ts_rank_cd}.
 */
@Repository
public class PostgresKeywordSearcher implements KeywordSearcher {

    private static final int MAX_TERMS = 24;
    private static final Set<String> STOP_WORDS = Set.of("a", "about", "an", "and", "are", "as", "at", "be", "by",
            "can", "could", "did", "do", "does", "for", "from", "has", "have", "how", "i", "in", "into", "is", "it",
            "its", "me", "my", "of", "on", "or", "our", "please", "should", "tell", "that", "the", "their", "them",
            "there", "these", "they", "this", "to", "us", "was", "we", "what", "when", "where", "which", "who", "why",
            "will", "with", "would", "you", "your");

    private final JdbcTemplate jdbc;

    public PostgresKeywordSearcher(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<KeywordSearchResult> search(String query, VectorFilter filter, int limit) {
        List<String> terms = terms(query);
        if (terms.isEmpty() || filter.collectionIds().isEmpty()) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder("""
                SELECT c.id, c.source_id, ts_rank_cd(c.content_tsv, q.query) AS score
                FROM knowledge_chunks c
                JOIN knowledge_sources s ON s.id = c.source_id
                CROSS JOIN (SELECT to_tsquery('simple', ?) AS query) q
                WHERE c.content_tsv @@ q.query AND s.enabled AND s.status = 'INDEXED'
                  AND c.collection_id = ANY (CAST(? AS uuid[]))""");
        List<Object> args = new ArrayList<>();
        args.add(String.join(" | ", terms));
        args.add(uuidArray(filter.collectionIds()));
        if (!filter.sourceIds().isEmpty()) {
            sql.append(" AND c.source_id = ANY (CAST(? AS uuid[]))");
            args.add(uuidArray(filter.sourceIds()));
        }
        if (!filter.tags().isEmpty()) {
            sql.append(" AND s.tags && CAST(? AS text[])");
            args.add(textArray(filter.tags()));
        }
        sql.append(" ORDER BY score DESC LIMIT ?");
        args.add(limit);
        return jdbc.query(sql.toString(), (rs, i) -> new KeywordSearchResult(uuid(rs, "id"), uuid(rs, "source_id"),
                rs.getDouble("score")), args.toArray());
    }

    /** Lower-cased letter/digit words without stop words, safe to embed in a tsquery. */
    static List<String> terms(String query) {
        if (query == null) {
            return List.of();
        }
        String normalized = Normalizer.normalize(query, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        Set<String> terms = new LinkedHashSet<>();
        for (String word : normalized.split("[^\\p{L}\\p{N}]+")) {
            if (word.length() > 1 && !STOP_WORDS.contains(word)) {
                terms.add(word);
            }
            if (terms.size() == MAX_TERMS) {
                break;
            }
        }
        return List.copyOf(terms);
    }
}
