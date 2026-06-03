package com.example.aichat.infrastructure.llm;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Helpers for vendor specific JSON fragments configured in YAML.
 */
public final class JsonValues {

    private JsonValues() {
    }

    /**
     * Spring binds scalar YAML values in {@code Map<String, Object>} as strings. Convert booleans and numbers back so
     * that e.g. {@code enable_thinking: false} is sent as JSON {@code false} and not as the (truthy) string "false".
     */
    public static Object coerce(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((k, v) -> result.put(String.valueOf(k), coerce(v)));
            return isIndexedList(result) ? new ArrayList<>(result.values()) : result;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(JsonValues::coerce).toList();
        }
        if (value instanceof String text) {
            String trimmed = text.strip();
            if ("true".equalsIgnoreCase(trimmed)) {
                return Boolean.TRUE;
            }
            if ("false".equalsIgnoreCase(trimmed)) {
                return Boolean.FALSE;
            }
            if ("null".equals(trimmed)) {
                return null;
            }
            if (trimmed.matches("-?\\d{1,18}")) {
                return Long.parseLong(trimmed);
            }
            if (trimmed.matches("-?\\d+\\.\\d+")) {
                return new BigDecimal(trimmed);
            }
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> coerceMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return Map.of();
        }
        Object coerced = coerce(map);
        return coerced instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    /** Deep-merges {@code overrides} into {@code target}; nested maps are merged, other values replaced. */
    @SuppressWarnings("unchecked")
    public static void deepMerge(Map<String, Object> target, Map<String, Object> overrides) {
        overrides.forEach((key, value) -> {
            Object existing = target.get(key);
            if (existing instanceof Map<?, ?> existingMap && value instanceof Map<?, ?> valueMap) {
                Map<String, Object> merged = new LinkedHashMap<>((Map<String, Object>) existingMap);
                deepMerge(merged, (Map<String, Object>) valueMap);
                target.put(key, merged);
            } else {
                target.put(key, value);
            }
        });
    }

    /** Spring binds YAML lists inside maps as maps with keys "0", "1", ... */
    private static boolean isIndexedList(Map<String, Object> map) {
        if (map.isEmpty()) {
            return false;
        }
        int index = 0;
        for (String key : map.keySet()) {
            if (!key.equals(Integer.toString(index++))) {
                return false;
            }
        }
        return true;
    }
}
