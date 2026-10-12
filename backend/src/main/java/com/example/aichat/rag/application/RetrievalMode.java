package com.example.aichat.rag.application;

/**
 * How candidates are found. {@code HYBRID} merges vector search with PostgreSQL full-text search using reciprocal
 * rank fusion, which helps for exact terms such as product codes or error numbers.
 */
public enum RetrievalMode {
    VECTOR,
    HYBRID
}
