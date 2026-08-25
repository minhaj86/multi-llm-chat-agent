package com.example.aichat.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A parsed Server-Sent Event with JSON data.
 */
public record SseEvent(String event, JsonNode data) {

    public static List<SseEvent> parse(String body, ObjectMapper mapper) {
        List<SseEvent> events = new ArrayList<>();
        String name = null;
        StringBuilder data = new StringBuilder();
        for (String line : (body == null ? "" : body).split("\r?\n", -1)) {
            if (line.isEmpty()) {
                if (!data.isEmpty()) {
                    events.add(new SseEvent(name == null ? "message" : name, json(mapper, data.toString())));
                }
                name = null;
                data.setLength(0);
            } else if (line.startsWith("event:")) {
                name = line.substring(6).strip();
            } else if (line.startsWith("data:")) {
                if (!data.isEmpty()) {
                    data.append('\n');
                }
                data.append(line.substring(5).startsWith(" ") ? line.substring(6) : line.substring(5));
            }
        }
        return events;
    }

    public static List<String> names(List<SseEvent> events) {
        return events.stream().map(SseEvent::event).toList();
    }

    public static JsonNode first(List<SseEvent> events, String name) {
        return events.stream().filter(e -> e.event().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("No '" + name + "' event in " + names(events)))
                .data();
    }

    public static String text(List<SseEvent> events, String name) {
        return events.stream().filter(e -> e.event().equals(name))
                .map(e -> e.data().path("text").asText())
                .collect(Collectors.joining());
    }

    private static JsonNode json(ObjectMapper mapper, String data) {
        try {
            return mapper.readTree(data);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
