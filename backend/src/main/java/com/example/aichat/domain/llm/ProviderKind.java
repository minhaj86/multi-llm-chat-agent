package com.example.aichat.domain.llm;

/**
 * Where inference happens. Used for cost reporting ("Local") and usage analytics (local vs cloud).
 */
public enum ProviderKind {
    CLOUD,
    LOCAL
}
