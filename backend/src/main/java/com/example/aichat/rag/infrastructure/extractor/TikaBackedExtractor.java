package com.example.aichat.rag.infrastructure.extractor;

import com.example.aichat.rag.application.RagProperties;
import com.example.aichat.rag.domain.DocumentExtractor;
import com.example.aichat.rag.domain.DocumentMetadata;
import com.example.aichat.rag.domain.ExtractedDocument;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;

/**
 * Base class for extractors that delegate to Apache Tika and differ only in the formats they accept.
 */
abstract class TikaBackedExtractor implements DocumentExtractor {

    private final Set<String> contentTypes;
    private final Set<String> extensions;
    private final RagProperties properties;

    TikaBackedExtractor(Set<String> contentTypes, Set<String> extensions, RagProperties properties) {
        this.contentTypes = contentTypes;
        this.extensions = extensions;
        this.properties = properties;
    }

    @Override
    public boolean supports(String contentType, String filename) {
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT).split(";")[0].strip();
        if (contentTypes.contains(type)) {
            return true;
        }
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        return extensions.stream().anyMatch(extension -> name.endsWith("." + extension));
    }

    @Override
    public ExtractedDocument extract(InputStream input, DocumentMetadata metadata) throws IOException {
        return TikaDocumentParser.parse(input, metadata, metadata.contentType(),
                properties.ingestion().maxExtractedCharacters());
    }
}
