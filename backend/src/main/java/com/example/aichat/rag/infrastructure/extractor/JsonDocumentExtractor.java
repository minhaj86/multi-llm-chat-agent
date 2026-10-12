package com.example.aichat.rag.infrastructure.extractor;

import com.example.aichat.rag.application.RagProperties;
import com.example.aichat.rag.domain.DocumentExtractionException;
import com.example.aichat.rag.domain.DocumentExtractor;
import com.example.aichat.rag.domain.DocumentMetadata;
import com.example.aichat.rag.domain.ExtractedDocument;
import com.example.aichat.rag.domain.ExtractedDocument.SectionSpan;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * JSON files are flattened into {@code path: value} lines (e.g. {@code pricing.currencies[0].code: EUR}), which
 * keeps every value next to its meaning. Top-level keys become sections.
 */
@Component
@Order(60)
public class JsonDocumentExtractor implements DocumentExtractor {

    private static final int MAX_DEPTH = 64;

    private final RagProperties properties;
    private final ObjectMapper objectMapper;

    public JsonDocumentExtractor(RagProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "json";
    }

    @Override
    public boolean supports(String contentType, String filename) {
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT).split(";")[0].strip();
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        return "application/json".equals(type) || name.endsWith(".json");
    }

    @Override
    public ExtractedDocument extract(InputStream input, DocumentMetadata metadata) throws IOException {
        JsonNode root;
        try {
            root = objectMapper.readTree(TextDecoding.decode(input.readAllBytes()));
        } catch (JsonProcessingException e) {
            throw new DocumentExtractionException("The JSON file is not valid (line "
                    + e.getLocation().getLineNr() + ")", e);
        }
        StringBuilder text = new StringBuilder();
        List<SectionSpan> sections = new ArrayList<>();
        if (root != null && root.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                sections.add(new SectionSpan(field.getKey(), 1, text.length()));
                flatten(field.getKey(), field.getValue(), text, 1);
            }
        } else if (root != null) {
            flatten("", root, text, 0);
        }
        return new ExtractedDocument(metadata, text.toString().strip(), List.of(), sections, Map.of());
    }

    private void flatten(String path, JsonNode node, StringBuilder text, int depth) {
        if (depth > MAX_DEPTH) {
            throw new DocumentExtractionException("The JSON document is nested too deeply");
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                flatten(path.isEmpty() ? field.getKey() : path + "." + field.getKey(), field.getValue(), text,
                        depth + 1);
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                flatten(path + "[" + i + "]", node.get(i), text, depth + 1);
            }
        } else if (!node.isNull()) {
            String value = node.asText().replaceAll("\\s+", " ").strip();
            if (!value.isEmpty()) {
                text.append(path.isEmpty() ? "value" : path).append(": ").append(value).append('\n');
            }
            if (text.length() > properties.ingestion().maxExtractedCharacters()) {
                throw new DocumentExtractionException("The document text exceeds the limit of "
                        + properties.ingestion().maxExtractedCharacters()
                        + " characters (rag.ingestion.max-extracted-characters)");
            }
        }
    }
}
