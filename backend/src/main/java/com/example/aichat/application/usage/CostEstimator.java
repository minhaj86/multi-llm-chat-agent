package com.example.aichat.application.usage;

import com.example.aichat.domain.llm.LlmUsage;
import com.example.aichat.domain.llm.ProviderDescriptor;

/**
 * Outbound port: estimates the cost of a call from token usage. Implemented in infrastructure from configured
 * price lists, so pricing changes never touch the application layer.
 */
public interface CostEstimator {

    CostEstimate estimate(ProviderDescriptor provider, String model, LlmUsage usage);
}
