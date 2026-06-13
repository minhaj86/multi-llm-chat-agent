package com.example.aichat.infrastructure.llm.llama;

import com.example.aichat.domain.llm.LlmCapability;
import com.example.aichat.infrastructure.llm.LlmAdapterSupport;
import com.example.aichat.infrastructure.llm.ProviderProperties;
import com.example.aichat.infrastructure.llm.compatible.ModelFamilyProfile;
import com.example.aichat.infrastructure.llm.compatible.OpenAiCompatibleLlmProvider;
import java.util.EnumSet;
import java.util.Map;

/**
 * Meta Llama (3.x) served by any OpenAI-compatible engine. Llama models do not expose a separate reasoning channel,
 * so the generic protocol handling is used unchanged; only the profile differs.
 */
public class LlamaLlmProvider extends OpenAiCompatibleLlmProvider {

    public static final String FAMILY = "llama";

    static final ModelFamilyProfile PROFILE = new ModelFamilyProfile(
            "Llama Local",
            EnumSet.of(LlmCapability.STREAMING, LlmCapability.TOOL_CALLING),
            false,
            Map.of());

    public LlamaLlmProvider(String providerId, ProviderProperties properties, LlmAdapterSupport support) {
        super(providerId, properties, PROFILE, support);
    }
}
