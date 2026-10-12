package com.example.aichat.rag.application;

/**
 * What to do when uploaded content already exists (same SHA-256).
 */
public enum DuplicateStrategy {
    /** Default: reject with {@link DuplicateKnowledgeException} so the user can decide. */
    REJECT,
    /** Store as the next version of the existing source; the previous version is disabled. */
    NEW_VERSION,
    /** Replace the content of the existing source and reindex it. */
    REPLACE
}
