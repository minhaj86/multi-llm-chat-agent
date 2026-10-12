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
 * Markdown files and manual text entries. The Markdown source is kept as-is (it reads well for both humans and
 * models); ATX headings become sections for citations.
 */
@Component
@Order(35)
public class MarkdownDocumentExtractor implements DocumentExtractor {

    private static final Set<String> TYPES = Set.of("text/markdown", "text/x-markdown", "text/x-web-markdown");
    private static final Set<String> EXTENSIONS = Set.of("md", "markdown", "mdown");

    private final RagProperties properties;

    public MarkdownDocumentExtractor(RagProperties properties) {
        this.properties = properties;
    }

    @Override
    public String name() {
        return "markdown";
    }

    @Override
    public boolean supports(String contentType, String filename) {
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT).split(";")[0].strip();
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        return TYPES.contains(type) || EXTENSIONS.stream().anyMatch(e -> name.endsWith("." + e));
    }

    @Override
    public ExtractedDocument extract(InputStream input, DocumentMetadata metadata) throws IOException {
        String text = PlainText.normalize(TextDecoding.decode(input.readAllBytes()),
                properties.ingestion().maxExtractedCharacters());
        return new ExtractedDocument(metadata, text, List.of(), PlainText.markdownSections(text), Map.of());
    }
}
