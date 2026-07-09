package com.example.aichat.infrastructure.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.aichat.application.usage.CostEstimate;
import com.example.aichat.domain.llm.LlmUsage;
import com.example.aichat.domain.llm.ProviderDefaults;
import com.example.aichat.domain.llm.ProviderDescriptor;
import com.example.aichat.domain.llm.ProviderKind;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ConfiguredPricingCostEstimatorTest {

    private final ConfiguredPricingCostEstimator estimator = new ConfiguredPricingCostEstimator(properties());

    private static LlmProperties properties() {
        ProviderProperties openai = new ProviderProperties();
        openai.setPricing(List.of(
                new ProviderProperties.ModelPricing("gpt-5", new BigDecimal("1.25"), new BigDecimal("10.00")),
                new ProviderProperties.ModelPricing("gpt-5-mini", new BigDecimal("0.25"), new BigDecimal("2.00"))));
        LlmProperties properties = new LlmProperties();
        properties.getProviders().put("openai", openai);
        properties.getProviders().put("qwen", new ProviderProperties());
        return properties;
    }

    private static ProviderDescriptor descriptor(String id, ProviderKind kind) {
        return new ProviderDescriptor(id, id, "t", kind, Set.of(), "http://x", null,
                new ProviderDefaults(true, "m", null, null, null));
    }

    @Test
    void usesTheLongestMatchingModelPrefix() {
        CostEstimate estimate = estimator.estimate(descriptor("openai", ProviderKind.CLOUD), "gpt-5-mini-2025-08-07",
                LlmUsage.of(1_000_000, 500_000));

        assertThat(estimate.local()).isFalse();
        assertThat(estimate.amount()).isEqualByComparingTo("1.25");
    }

    @Test
    void localInferenceIsFree() {
        CostEstimate estimate = estimator.estimate(descriptor("qwen", ProviderKind.LOCAL), "qwen3", LlmUsage.of(10, 10));

        assertThat(estimate.local()).isTrue();
        assertThat(estimate.amount()).isEqualByComparingTo("0");
    }

    @Test
    void unknownModelsOrUsageYieldNoEstimate() {
        assertThat(estimator.estimate(descriptor("openai", ProviderKind.CLOUD), "o9", LlmUsage.of(1, 1)).amount()).isNull();
        assertThat(estimator.estimate(descriptor("openai", ProviderKind.CLOUD), "gpt-5", LlmUsage.UNKNOWN).amount()).isNull();
    }
}
