package com.example.aichat.rag.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Small JDBC helpers shared by the knowledge-base adapters (PostgreSQL arrays, JSONB and timestamps).
 */
final class JdbcSupport {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private JdbcSupport() {
    }

    static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    static UUID uuid(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }

    static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    static Double nullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    /** Bound with {@code CAST(? AS text[])}. */
    static String[] textArray(Collection<String> values) {
        return values == null ? new String[0] : values.toArray(String[]::new);
    }

    /** Bound with {@code CAST(? AS uuid[])}. */
    static String[] uuidArray(Collection<UUID> values) {
        return values == null ? new String[0] : values.stream().map(UUID::toString).toArray(String[]::new);
    }

    static List<String> strings(ResultSet rs, String column) throws SQLException {
        Array array = rs.getArray(column);
        if (array == null) {
            return List.of();
        }
        Object[] values = (Object[]) array.getArray();
        return Arrays.stream(values).filter(Objects::nonNull).map(Object::toString).toList();
    }

    static List<UUID> uuids(ResultSet rs, String column) throws SQLException {
        Array array = rs.getArray(column);
        if (array == null) {
            return List.of();
        }
        Object[] values = (Object[]) array.getArray();
        return Arrays.stream(values).filter(Objects::nonNull)
                .map(v -> v instanceof UUID id ? id : UUID.fromString(v.toString())).toList();
    }

    static String json(ObjectMapper mapper, Object value) {
        try {
            return mapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise JSON column", e);
        }
    }

    static Map<String, Object> jsonMap(ObjectMapper mapper, String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return mapper.readValue(json, MAP);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not read JSON column", e);
        }
    }

    /** Escapes LIKE wildcards in user input (used with {@code ESCAPE '\'}). */
    static String likePattern(String text) {
        return "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
