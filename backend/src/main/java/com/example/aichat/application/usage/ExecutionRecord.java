package com.example.aichat.application.usage;

import com.example.aichat.domain.llm.LlmUsage;
import com.example.aichat.domain.llm.ProviderDescriptor;
import com.example.aichat.domain.usage.ExecutionPurpose;
import com.example.aichat.domain.usage.ExecutionStatus;
import java.util.UUID;

/**
 * Data needed to record one LLM invocation.
 */
public record ExecutionRecord(ProviderDescriptor provider,
                              String model,
                              ExecutionPurpose purpose,
                              UUID userId,
                              UUID conversationId,
                              UUID messageId,
                              LlmUsage usage,
                              long latencyMs,
                              Long timeToFirstTokenMs,
                              ExecutionStatus status,
                              String errorCode) {
}
