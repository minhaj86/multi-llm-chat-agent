# 0002. `LlmProvider` port with registry and factories

- Status: Accepted
- Date: 2026-10-12

## Context

Users pick a provider and a model for every message, and may switch mid-conversation. Providers are defined by
configuration: the same adapter type can back several providers (Qwen and Llama both use the OpenAI-compatible
adapter, and a Mistral server would too). Provider selection must not use `if/else` or `switch` on provider names.
The UI needs metadata (display name, local or cloud, capabilities, default model, availability) without seeing
credentials.

## Decision

1. **Port** (`domain/llm/LlmProvider`), refining the initially suggested interface:
   - `descriptor()` replaces a bare `providerId()`. It returns a `ProviderDescriptor` with id, display name, type,
     kind (`CLOUD`/`LOCAL`), capabilities, endpoint, masked API key and configured defaults.
   - `chat(LlmRequest)` returns a lazy `Mono<LlmResponse>` instead of a blocking response, so it composes with
     timeouts, retries and cancellation.
   - `stream(LlmRequest)` returns `Flux<LlmChunk>`: `TextDelta`, `ReasoningDelta`, then exactly one `Completed`
     carrying usage and the finish reason. Cancelling the subscription aborts the upstream request.
   - `availableModels()` and `health()` must be cheap and must never run inference.
2. **Factories**: `LlmProviderFactory` beans declare a `type()` (e.g. `openai`, `openai-compatible`) and an optional
   `family()` (e.g. `qwen`, `llama`). For each `llm.providers.<id>` entry, `LlmProviderConfiguration` chooses the
   factory matching `(type, family)`, falling back to `(type, null)`, and creates the adapter.
3. **Decorators**: each adapter is wrapped as `ObservedLlmProvider(ResilientLlmProvider(adapter))`, adding metrics,
   circuit breaker, safe retry ([0007](0007-retry-only-when-safe.md)) and timeouts uniformly.
4. **Registry**: `LlmProviderRegistry.resolve(provider, model)` returns the provider plus effective settings. It falls
   back to the default provider and the provider's default model, and applies runtime overrides from the
   `provider_settings` table.

## Consequences

- Adding a provider means configuration only, or one adapter + one factory bean. Chat logic, controllers and the
  frontend (which reads `GET /api/providers`) stay untouched.
- Enabling or disabling providers and changing default model, timeout, temperature or max tokens work at runtime
  from the admin UI. Adding a *new* provider entry needs a backend restart, because adapters are created at startup.
- Factory conflicts (two factories for the same key) and unknown types fail fast at startup with a clear message.

## Alternatives considered

- **One Spring bean per provider with `@Qualifier` names**: cannot instantiate the same adapter several times from
  configuration, and puts provider ids in code.
- **`switch`/`if` on provider ids**: explicitly ruled out; every new provider would modify chat logic.
- **Blocking `ChatResponse chat(ChatRequest)`**: simpler signature, but cancellation, timeouts and streaming
  composition would be handled inconsistently.
