package com.example.aichat.domain.llm;

/**
 * Provider-neutral reason why generation stopped.
 */
public enum FinishReason {
    STOP,
    LENGTH,
    CONTENT_FILTER,
    TOOL_CALLS,
    CANCELLED,
    ERROR,
    UNKNOWN
}
