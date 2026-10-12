package com.example.aichat.rag.domain;

import java.io.IOException;
import java.io.InputStream;

/**
 * Extracts plain text (plus pages/sections when the format has them) from one family of document formats.
 * Implementations must not execute embedded content, must not resolve external entities and must honour the
 * size limits they are given; OCR is out of scope.
 */
public interface DocumentExtractor {

    /** Short identifier shown in the indexing details, e.g. {@code pdf}, {@code docx}, {@code markdown}. */
    String name();

    boolean supports(String contentType, String filename);

    ExtractedDocument extract(InputStream input, DocumentMetadata metadata) throws IOException;
}
