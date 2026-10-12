# 0003. One OpenAI-compatible adapter for local models

- Status: Accepted
- Date: 2026-10-12

## Context

Qwen and Llama run on local inference servers. Which server is used varies by machine and team: Ollama, vLLM,
llama.cpp, SGLang, LM Studio, LocalAI. Nearly all of them expose the OpenAI Chat Completions protocol
(`POST /v1/chat/completions`, `GET /v1/models`), but they differ in details:

- reasoning output: a `reasoning` or `reasoning_content` delta field, or `<think>...</think>` inline in the content;
- how to switch thinking off: `reasoning_effort: none` (Ollama), `chat_template_kwargs.enable_thinking=false`
  (vLLM, SGLang, llama.cpp) or the `/no_think` soft switch;
- usage in streams (`stream_options.include_usage`), the name of the max-tokens parameter, extra headers.

The application must not assume Ollama.

## Decision

- Implement one `OpenAiCompatibleLlmProvider` for the Chat Completions protocol (streaming, usage, models, health,
  error translation).
- Select server quirks with `engine` (`generic`, `ollama`, `vllm`, `sglang`, `llama-cpp`, `lm-studio`, `localai`).
- Express model-family behaviour as data in a `ModelFamilyProfile`: display name, capabilities, `<think>` parsing,
  and reasoning-off parameters per engine. `QwenLlmProvider` and `LlamaLlmProvider` are thin subclasses that
  contribute a profile (Qwen also adds the soft switch for generic engines).
- Keep everything else configurable: `base-url`, `api-key`, `default-model`, `models`, `extra-body`, `headers`,
  `max-tokens-parameter`, `stream-usage`, `reasoning-disabled-params`, timeouts.

## Consequences

- Any OpenAI-compatible server, local or remote, becomes a provider through configuration alone.
- Protocol handling is written and tested once; family and engine differences are small and explicit.
- Engine-native features (Ollama model pulls, keep-alive control, llama.cpp slots) are not used.
- New engine quirks are handled by adding an `InferenceEngine` value or configuration, not by forking the adapter.

## Alternatives considered

- **Ollama's native `/api/chat`**: richer for Ollama, but locks the application to one engine.
- **One adapter per engine**: duplicates streaming, error and usage handling.
- **The OpenAI Java SDK against local servers**: strict typing struggles with non-standard fields (`reasoning`),
  and gives less control over streaming and cancellation. The cloud OpenAI adapter uses the Responses API directly
  over WebClient for the same reasons.
