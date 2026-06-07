package com.example.aichat.infrastructure.llm.compatible;

import com.example.aichat.domain.llm.LlmCapability;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Model-family specific behaviour of an OpenAI-compatible provider, expressed as data rather than code.
 *
 * @param defaultDisplayName      display name used when none is configured
 * @param capabilities            advertised capabilities
 * @param parseThinkTags          split a leading {@code <think>...</think>} block into reasoning (for servers that
 *                                return reasoning inline in the content)
 * @param reasoningDisabledParams request fields that switch off reasoning, per inference engine
 */
public record ModelFamilyProfile(String defaultDisplayName,
                                 Set<LlmCapability> capabilities,
                                 boolean parseThinkTags,
                                 Map<InferenceEngine, Map<String, Object>> reasoningDisabledParams) {

    public static final ModelFamilyProfile GENERIC = new ModelFamilyProfile(null,
            Set.of(LlmCapability.STREAMING), false, Map.of());

    public ModelFamilyProfile {
        capabilities = capabilities.isEmpty() ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(capabilities));
        reasoningDisabledParams = Map.copyOf(reasoningDisabledParams);
    }

    public Map<String, Object> reasoningDisabledParams(InferenceEngine engine) {
        return reasoningDisabledParams.getOrDefault(engine, Map.of());
    }
}
