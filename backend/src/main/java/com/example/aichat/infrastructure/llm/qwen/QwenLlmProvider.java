package com.example.aichat.infrastructure.llm.qwen;

import com.example.aichat.domain.llm.LlmCapability;
import com.example.aichat.domain.llm.LlmMessage;
import com.example.aichat.domain.llm.LlmRequest;
import com.example.aichat.domain.llm.LlmRole;
import com.example.aichat.domain.llm.ReasoningMode;
import com.example.aichat.infrastructure.llm.LlmAdapterSupport;
import com.example.aichat.infrastructure.llm.ProviderProperties;
import com.example.aichat.infrastructure.llm.compatible.InferenceEngine;
import com.example.aichat.infrastructure.llm.compatible.ModelFamilyProfile;
import com.example.aichat.infrastructure.llm.compatible.OpenAiCompatibleLlmProvider;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * Qwen (Qwen3 / Qwen2.5) served by any OpenAI-compatible engine.
 *
 * <p>Qwen-specific behaviour is configuration-like data: reasoning ("thinking") is surfaced separately from the
 * answer, inline {@code <think>} blocks are parsed, and reasoning can be switched off per engine (Ollama:
 * {@code reasoning_effort=none}; vLLM/SGLang/llama.cpp: {@code chat_template_kwargs.enable_thinking=false};
 * other engines: Qwen3's {@code /no_think} soft switch).
 */
public class QwenLlmProvider extends OpenAiCompatibleLlmProvider {

    public static final String FAMILY = "qwen";
    static final String NO_THINK_SWITCH = "/no_think";

    private static final Map<String, Object> DISABLE_THINKING_TEMPLATE =
            Map.of("chat_template_kwargs", Map.of("enable_thinking", false));

    static final ModelFamilyProfile PROFILE = new ModelFamilyProfile(
            "Qwen Local",
            EnumSet.of(LlmCapability.STREAMING, LlmCapability.REASONING, LlmCapability.TOOL_CALLING),
            true,
            Map.of(
                    InferenceEngine.OLLAMA, Map.of("reasoning_effort", "none"),
                    InferenceEngine.VLLM, DISABLE_THINKING_TEMPLATE,
                    InferenceEngine.SGLANG, DISABLE_THINKING_TEMPLATE,
                    InferenceEngine.LLAMA_CPP, DISABLE_THINKING_TEMPLATE));

    public QwenLlmProvider(String providerId, ProviderProperties properties, LlmAdapterSupport support) {
        super(providerId, properties, PROFILE, support);
    }

    @Override
    protected List<LlmMessage> adaptMessages(LlmRequest request) {
        if (request.options().reasoning() != ReasoningMode.DISABLED || hasReasoningDisabledParams()) {
            return request.messages();
        }
        List<LlmMessage> messages = new ArrayList<>(request.messages());
        for (int i = messages.size() - 1; i >= 0; i--) {
            LlmMessage message = messages.get(i);
            if (message.role() == LlmRole.USER) {
                messages.set(i, LlmMessage.user(message.content() + " " + NO_THINK_SWITCH));
                break;
            }
        }
        return messages;
    }
}
