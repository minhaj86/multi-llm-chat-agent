package com.example.aichat.infrastructure.llm;

import com.example.aichat.application.usage.CostEstimate;
import com.example.aichat.application.usage.CostEstimator;
import com.example.aichat.domain.llm.LlmUsage;
import com.example.aichat.domain.llm.ProviderDescriptor;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Estimates costs from the per-provider price lists in configuration ({@code llm.providers.<id>.pricing}).
 * Local providers always cost zero ("Local"); unknown models yield an unknown cost rather than a guess.
 */
@Component
public class ConfiguredPricingCostEstimator implements CostEstimator {

    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);

    private final LlmProperties properties;

    public ConfiguredPricingCostEstimator(LlmProperties properties) {
        this.properties = properties;
    }

    @Override
    public CostEstimate estimate(ProviderDescriptor provider, String model, LlmUsage usage) {
        if (provider.isLocal()) {
            return CostEstimate.localInference();
        }
        ProviderProperties config = properties.getProviders().get(provider.id());
        if (config == null || model == null || usage == null || !usage.isKnown()) {
            return CostEstimate.unknown();
        }
        ProviderProperties.ModelPricing pricing = pricingFor(config, model);
        if (pricing == null || pricing.getInputPerMillion() == null || pricing.getOutputPerMillion() == null) {
            return CostEstimate.unknown();
        }
        BigDecimal input = BigDecimal.valueOf(usage.inputTokens() == null ? 0 : usage.inputTokens())
                .multiply(pricing.getInputPerMillion());
        BigDecimal output = BigDecimal.valueOf(usage.outputTokens() == null ? 0 : usage.outputTokens())
                .multiply(pricing.getOutputPerMillion());
        return new CostEstimate(input.add(output).divide(MILLION, 6, RoundingMode.HALF_UP), false);
    }

    static ProviderProperties.ModelPricing pricingFor(ProviderProperties config, String model) {
        String lower = model.toLowerCase(Locale.ROOT);
        return config.getPricing().stream()
                .filter(p -> p.getModel() != null && lower.startsWith(p.getModel().toLowerCase(Locale.ROOT)))
                .max(Comparator.comparingInt(p -> p.getModel().length()))
                .orElse(config.getDefaultPricing());
    }
}
