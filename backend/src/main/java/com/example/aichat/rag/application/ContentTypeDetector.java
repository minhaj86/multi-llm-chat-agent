package com.example.aichat.rag.application;

/**
 * Detects the real media type of uploaded bytes (magic numbers, container inspection), so that a file cannot
 * pretend to be another format through its name or declared content type.
 */
public interface ContentTypeDetector {

    /** Base media type such as {@code application/pdf} or {@code text/plain}. */
    String detect(byte[] content, String filename);
}
