package com.example.aichat.rag.domain;

/**
 * Where the knowledge comes from. {@code URL} is reserved for future web-page ingestion.
 */
public enum KnowledgeSourceType {
    FILE,
    TEXT,
    URL
}
