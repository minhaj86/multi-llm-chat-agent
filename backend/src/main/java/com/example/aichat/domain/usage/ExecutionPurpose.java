package com.example.aichat.domain.usage;

/**
 * Why an LLM call was made.
 */
public enum ExecutionPurpose {
    CHAT,
    COMPARE,
    TITLE,
    SUMMARY,
    /** Answer generated from the admin knowledge-base test console. */
    RAG_TEST
}
