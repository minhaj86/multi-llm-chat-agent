package com.example.aichat.rag.domain;

/**
 * Extraction failed for a reason that can be shown to administrators (encrypted PDF, corrupt file, text limit
 * exceeded, ...). The message must not contain document text.
 */
public class DocumentExtractionException extends RuntimeException {

    public DocumentExtractionException(String message) {
        super(message);
    }

    public DocumentExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
