package com.example.aichat.domain.llm;

/**
 * Optional features a provider supports. The application uses these flags to enable features
 * (e.g. tool calling, image input) per provider without knowing which vendor sits behind it.
 */
public enum LlmCapability {
    STREAMING,
    REASONING,
    TOOL_CALLING,
    VISION,
    JSON_OUTPUT
}
