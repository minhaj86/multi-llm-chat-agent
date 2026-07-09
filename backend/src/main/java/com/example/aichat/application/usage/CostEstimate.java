package com.example.aichat.application.usage;

import java.math.BigDecimal;

/**
 * Estimated cost of an LLM call in USD.
 *
 * @param amount {@code null} when no pricing is known; zero for local providers
 * @param local  inference ran on a local provider ("Cost: Local")
 */
public record CostEstimate(BigDecimal amount, boolean local) {

    public static CostEstimate localInference() {
        return new CostEstimate(BigDecimal.ZERO, true);
    }

    public static CostEstimate unknown() {
        return new CostEstimate(null, false);
    }
}
