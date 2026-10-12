package com.example.aichat.rag.infrastructure.extractor;

import com.example.aichat.rag.application.RagProperties;
import com.example.aichat.rag.domain.DocumentExtractor;
import com.example.aichat.rag.domain.DocumentMetadata;
import com.example.aichat.rag.domain.ExtractedDocument;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Plain text files (UTF-8/UTF-16 with BOM, or legacy Windows-1252).
 */
@Component
@Order(40)
public class TextDocumentExtractor implements DocumentExtractor {

    private static final Set<String> EXTENSIONS = Set.of("txt", "text", "log");

    private final RagProperties properties;

    public TextDocumentExtractor(RagProperties properties) {
        this.properties = properties;
    }

    @Override
    public String name() {
        return "text";
    }

    @Override
    public boolean supports(String contentType, String filename) {
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT).split(";")[0].strip();
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        return "text/plain".equals(type) || EXTENSIONS.stream().anyMatch(e -> name.endsWith("." + e));
    }

    @Override
    public ExtractedDocument extract(InputStream input, DocumentMetadata metadata) throws IOException {
        String text = PlainText.normalize(TextDecoding.decode(input.readAllBytes()),
                properties.ingestion().maxExtractedCharacters());
        return new ExtractedDocument(metadata, text, List.of(), List.of(), Map.of());
    }
}
