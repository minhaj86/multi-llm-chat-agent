package com.example.aichat.infrastructure.persistence.adapter;

import com.example.aichat.domain.usage.LlmExecution;
import com.example.aichat.domain.usage.LlmExecutionRepository;
import com.example.aichat.infrastructure.persistence.entity.LlmExecutionEntity;
import com.example.aichat.infrastructure.persistence.jpa.LlmExecutionJpaRepository;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Execution records via JPA; analytics via plain SQL aggregations (PostgreSQL {@code FILTER} clauses).
 */
@Repository
@Transactional(readOnly = true)
public class JpaLlmExecutionRepositoryAdapter implements LlmExecutionRepository {

    private static final String TOTALS_SQL = """
            SELECT count(*)                                                        AS requests,
                   count(*) FILTER (WHERE status = 'ERROR')                        AS errors,
                   count(*) FILTER (WHERE status = 'CANCELLED')                    AS cancelled,
                   coalesce(sum(input_tokens), 0)                                  AS input_tokens,
                   coalesce(sum(output_tokens), 0)                                 AS output_tokens,
                   coalesce(sum(estimated_cost) FILTER (WHERE provider_kind = 'CLOUD'), 0) AS cloud_cost,
                   coalesce(avg(latency_ms) FILTER (WHERE status = 'SUCCESS'), 0)  AS avg_latency,
                   count(*) FILTER (WHERE provider_kind = 'LOCAL')                 AS local_requests,
                   count(*) FILTER (WHERE provider_kind = 'CLOUD')                 AS cloud_requests
            FROM llm_executions
            WHERE created_at >= :from AND created_at <= :to
            """;

    private static final String BY_PROVIDER_SQL = """
            SELECT provider,
                   max(provider_kind)                                             AS provider_kind,
                   count(*)                                                       AS requests,
                   count(*) FILTER (WHERE status = 'ERROR')                       AS errors,
                   coalesce(sum(input_tokens), 0)                                 AS input_tokens,
                   coalesce(sum(output_tokens), 0)                                AS output_tokens,
                   coalesce(sum(estimated_cost), 0)                               AS cost,
                   coalesce(avg(latency_ms) FILTER (WHERE status = 'SUCCESS'), 0) AS avg_latency
            FROM llm_executions
            WHERE created_at >= :from AND created_at <= :to
            GROUP BY provider
            ORDER BY requests DESC, provider
            """;

    private static final String DAILY_SQL = """
            SELECT CAST(created_at AT TIME ZONE :tz AS date)  AS day,
                   provider,
                   count(*)                                   AS requests,
                   count(*) FILTER (WHERE status = 'ERROR')   AS errors,
                   coalesce(sum(input_tokens), 0)             AS input_tokens,
                   coalesce(sum(output_tokens), 0)            AS output_tokens,
                   coalesce(sum(estimated_cost), 0)           AS cost
            FROM llm_executions
            WHERE created_at >= :from AND created_at <= :to
            GROUP BY 1, 2
            ORDER BY 1, 2
            """;

    private final LlmExecutionJpaRepository jpa;
    private final JdbcClient jdbc;

    public JpaLlmExecutionRepositoryAdapter(LlmExecutionJpaRepository jpa, JdbcClient jdbc) {
        this.jpa = jpa;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public LlmExecution save(LlmExecution execution) {
        return jpa.save(LlmExecutionEntity.from(execution)).toDomain();
    }

    @Override
    public Map<UUID, LlmExecution> findLatestByMessageIds(Collection<UUID> messageIds) {
        if (messageIds.isEmpty()) {
            return Map.of();
        }
        BinaryOperator<LlmExecutionEntity> latest = (a, b) -> a.getCreatedAt().isAfter(b.getCreatedAt()) ? a : b;
        return jpa.findByMessageIds(messageIds).stream()
                .collect(Collectors.toMap(LlmExecutionEntity::getMessageId, e -> e, latest))
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toDomain()));
    }

    @Override
    public UsageTotals totals(Instant from, Instant to) {
        return jdbc.sql(TOTALS_SQL)
                .param("from", utc(from))
                .param("to", utc(to))
                .query((rs, row) -> new UsageTotals(
                        rs.getLong("requests"),
                        rs.getLong("errors"),
                        rs.getLong("cancelled"),
                        rs.getLong("input_tokens"),
                        rs.getLong("output_tokens"),
                        decimal(rs, "cloud_cost"),
                        rs.getDouble("avg_latency"),
                        rs.getLong("local_requests"),
                        rs.getLong("cloud_requests")))
                .single();
    }

    @Override
    public List<ProviderUsage> usageByProvider(Instant from, Instant to) {
        return jdbc.sql(BY_PROVIDER_SQL)
                .param("from", utc(from))
                .param("to", utc(to))
                .query((rs, row) -> new ProviderUsage(
                        rs.getString("provider"),
                        rs.getString("provider_kind"),
                        rs.getLong("requests"),
                        rs.getLong("errors"),
                        rs.getLong("input_tokens"),
                        rs.getLong("output_tokens"),
                        decimal(rs, "cost"),
                        rs.getDouble("avg_latency")))
                .list();
    }

    @Override
    public List<DailyUsage> dailyUsage(Instant from, Instant to, String timeZone) {
        return jdbc.sql(DAILY_SQL)
                .param("tz", timeZone)
                .param("from", utc(from))
                .param("to", utc(to))
                .query((rs, row) -> new DailyUsage(
                        rs.getDate("day").toLocalDate(),
                        rs.getString("provider"),
                        rs.getLong("requests"),
                        rs.getLong("errors"),
                        rs.getLong("input_tokens"),
                        rs.getLong("output_tokens"),
                        decimal(rs, "cost")))
                .list()
                .stream()
                .sorted(Comparator.comparing(DailyUsage::day).thenComparing(DailyUsage::provider))
                .toList();
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static BigDecimal decimal(ResultSet rs, String column) throws SQLException {
        BigDecimal value = rs.getBigDecimal(column);
        return value == null ? BigDecimal.ZERO : value;
    }
}
