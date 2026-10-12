---
name: add-llm-provider
description: Add a new LLM provider or model server to AI Workspace (for example Mistral, DeepSeek, Anthropic Claude, Google Gemini, Azure OpenAI, AWS Bedrock, or another OpenAI-compatible server) by adding configuration or one infrastructure adapter, without changing chat logic, controllers or the frontend. Use this when asked to integrate another model vendor or inference engine.
---

# Add an LLM provider

The chat logic depends only on the `LlmProvider` port (`domain/llm`). Providers are created from configuration by
`LlmProviderFactory` beans and resolved at runtime by `LlmProviderRegistry`. The frontend discovers them through
`GET /api/providers`. A new provider therefore never requires changes in `application`, `domain`, `api` or `frontend`.

## Pick the smallest path

| Situation | Path |
|-----------|------|
| The server speaks OpenAI Chat Completions (`POST /v1/chat/completions`, `GET /v1/models`): Ollama, vLLM, SGLang, llama.cpp, LM Studio, LocalAI, DeepSeek API, Mistral API, Groq, OpenRouter, ... | **A. Configuration only** |
| OpenAI-compatible, but the model family needs its own defaults (display name, capabilities, reasoning switch, `<think>` parsing) | **B. Family profile** |
| A different protocol (Anthropic Messages, Gemini, Bedrock Converse, Azure OpenAI deployments, ...) | **C. New adapter** |

## A. Configuration only

1. Add an entry under `llm.providers` in `backend/src/main/resources/application.yml`. The id must match
   `[a-z0-9][a-z0-9_-]*`. Every value must be overridable through an environment variable:
   ```yaml
       mistral:
         enabled: ${MISTRAL_ENABLED:true}
         type: openai-compatible
         engine: ${MISTRAL_ENGINE:vllm}      # generic | ollama | vllm | sglang | llama-cpp | lm-studio | localai
         display-name: Mistral Local
         kind: LOCAL                         # LOCAL shows "Cost: Local"; CLOUD uses the pricing table
         base-url: ${MISTRAL_BASE_URL:http://localhost:8003/v1}
         api-key: ${MISTRAL_API_KEY:local}
         default-model: ${MISTRAL_MODEL:mistralai/Mistral-7B-Instruct-v0.3}
         models: ${MISTRAL_MODELS:}          # optional allow-list; empty = discover via GET /models
         temperature: ${MISTRAL_TEMPERATURE:0.7}
         max-tokens: ${MISTRAL_MAX_TOKENS:4096}
         timeout: ${MISTRAL_TIMEOUT:PT180S}
         retry:
           max-attempts: 2
         circuit-breaker:
           wait-duration-in-open-state: PT20S
   ```
   Cloud providers also need `pricing` (USD per million tokens, longest model prefix wins) and `default-pricing`, so
   that the usage dashboard can estimate costs.
2. Pass the variables to the backend container in `docker-compose.yml` (`backend.environment`) and document them in
   `.env.example`.
3. Optional: add a model-server service behind a new Compose profile, and register the profile in
   `ALL_PROFILES` (`scripts/lib/common.sh`) and in the profile validation of `scripts/deploy.sh`.

## B. Family profile (OpenAI-compatible)

Copy `infrastructure/llm/llama/` and adapt it:

```java
public class MistralLlmProvider extends OpenAiCompatibleLlmProvider {

    public static final String FAMILY = "mistral";

    static final ModelFamilyProfile PROFILE = new ModelFamilyProfile(
            "Mistral Local",                                        // default display name
            EnumSet.of(LlmCapability.STREAMING, LlmCapability.TOOL_CALLING),
            false,                                                  // split leading <think>...</think> into reasoning
            Map.of());                                              // reasoning-off request fields per InferenceEngine

    public MistralLlmProvider(String providerId, ProviderProperties properties, LlmAdapterSupport support) {
        super(providerId, properties, PROFILE, support);
    }
}

@Component
public class MistralProviderFactory implements LlmProviderFactory {
    private final LlmAdapterSupport support;
    public MistralProviderFactory(LlmAdapterSupport support) { this.support = support; }
    @Override public String type() { return OpenAiCompatibleLlmProvider.TYPE; }
    @Override public String family() { return MistralLlmProvider.FAMILY; }
    @Override public LlmProvider create(String providerId, ProviderProperties properties) {
        return new MistralLlmProvider(providerId, properties, support);
    }
}
```

Then set `family: mistral` in the configuration from path A. When behaviour must differ, override the protected
hooks of `OpenAiCompatibleLlmProvider`: `customizeBody(body, request)`, `adaptMessages(request)`,
`requestBody(request, stream)` or `fetchModels()`. `QwenLlmProvider` is the reference for engine-specific reasoning
switches.

## C. New protocol adapter

Create `infrastructure/llm/<vendor>/` with two classes. Use `OpenAiLlmProvider` (Responses API, named SSE events) as
the reference implementation.

1. `<Vendor>LlmProvider implements LlmProvider`:
   - `descriptor()`: a `ProviderDescriptor` with id, display name, type, `ProviderKind`, capabilities, endpoint,
     `Secrets.mask(apiKey)` (never the key itself) and `ProviderDefaults` from the properties.
   - `chat(LlmRequest)`: a lazy `Mono<LlmResponse>` (`Mono.defer`).
   - `stream(LlmRequest)`: a lazy `Flux<LlmChunk>` that emits `TextDelta` and `ReasoningDelta`, then exactly one
     `Completed` (model, `LlmUsage`, `FinishReason`). Use `WebClient` so that cancelling the subscription (stop
     button or client disconnect) aborts the HTTP request.
   - `availableModels()`: the configured `models` allow-list, or a cheap list call.
   - `health()`: a cheap probe (for example a model list or metadata call). **Never run inference.** Return
     `ProviderHealth.up(...)` or `ProviderHealth.down(...)`; do not throw.
   - Build the HTTP client with `support.webClientFactory().create(providerId, baseUrl, properties)`. It applies the
     connection pool, the connect timeout, `Authorization: Bearer <api-key>` and the configured `headers`. For other
     auth schemes (for example `x-api-key`), set the header in the adapter. Response timeouts come from the resilience
     wrapper.
   - Map every failure through `LlmErrorTranslator` (`onStatus(..., errors::fromResponse)` and
     `onErrorMap(errors::translate)`). It produces `LlmAuthenticationException` (401/403), `LlmRateLimitException`
     (429, honouring `Retry-After`), `LlmProviderUnavailableException` (connection refused, 502/503),
     `LlmTimeoutException` (408/504, read timeouts) and `LlmResponseException` (invalid request, unknown model, other
     upstream errors). If the vendor reports errors inside a 200 response or stream, convert them with
     `fromErrorObject` or by throwing the matching `LlmException`.
2. `<Vendor>ProviderFactory implements LlmProviderFactory` as a `@Component` with a new unique `type()` (for example
   `anthropic`). Then configure `type: anthropic` under `llm.providers.<id>`.

You do not implement retries, circuit breaking, timeouts or metrics: `LlmProviderConfiguration` wraps every adapter
in `ResilientLlmProvider` and `ObservedLlmProvider`. Errors after the first emitted chunk automatically become
non-retryable.

## Tests (required)

- Adapter unit test with WireMock, modelled on `LlamaLlmProviderTest` / `OpenAiLlmProviderTest` and using
  `support/LlmTestSupport`. Cover the non-streaming answer, streaming deltas and usage, the model list, health up and
  down, and error mapping (401, 429 with `Retry-After`, 503, connection refused).
- For path A, an entry in `LlmProviderRegistryTest` or an integration test that uses the new configuration is enough.
- Run `scripts/test.sh backend`. Tests must never call the real vendor API.

## Done when

- [ ] `git diff --stat` shows no changes in `application/`, `domain/`, `api/` or `frontend/`. The exception is a
      genuinely new `LlmCapability`; if you need one, write an ADR.
- [ ] Secrets come only from environment variables, never appear in logs or API responses, and `.env.example` lists
      the variables without real values.
- [ ] `scripts/deploy.sh up --no-build` followed by `scripts/status.sh` lists the new provider. Without a reachable
      server it shows as Offline, and the rest of the application keeps working.
- [ ] The README is updated (environment variables, and "Adding a new provider" if the steps changed).
