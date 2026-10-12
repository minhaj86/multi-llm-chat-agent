# 0001. Hexagonal (ports and adapters) backend

- Status: Accepted
- Date: 2026-10-12

## Context

The application talks to several LLM vendors (OpenAI in the cloud; Qwen and Llama on local inference servers) and
must accept more later (Anthropic, Gemini, Bedrock, ...) without changing chat logic. It also persists conversations
and usage, authenticates users and exposes REST/SSE APIs. Vendor APIs change often and differ in streaming formats,
error semantics and reasoning output. The chat use cases (context management, persistence, titles, usage accounting,
cancellation) should not depend on any of that.

## Decision

Structure the backend as ports and adapters:

- `domain`: entities, value objects and ports (`LlmProvider`, repository interfaces), plus the neutral LLM model
  (`LlmRequest`, `LlmChunk`, `LlmResponse`, `LlmModel`, `ProviderHealth`) and the `LlmException` hierarchy. It depends
  only on the JDK and Reactor (`Mono`/`Flux` are the port's streaming contract).
- `application`: use cases (`ChatService`, `CompareService`, `ConversationContextManager`, `LlmProviderRegistry`, ...)
  and outbound ports (`CostEstimator`, `PasswordHasher`, `TokenIssuer`). Spring stereotypes and transaction
  boundaries are allowed; web, JPA and vendor types are not.
- `api`: inbound adapters (controllers, DTO records, SSE mapping, problem details).
- `infrastructure`: outbound adapters (LLM vendors, JPA persistence, JWT and BCrypt security).
- Dependencies point inward only. JPA entities are separate from domain objects and mapped in persistence adapters.

## Consequences

- Vendor code is confined to `infrastructure/llm/<vendor>`; adding a vendor touches no use case ([0002](0002-provider-port-registry-and-factories.md)).
- Use cases are unit-testable with fakes (for example `InMemoryProviderSettingsRepository`), and adapters are tested
  against WireMock in isolation.
- Extension points (tools, RAG, speech) have an obvious home: new ports in `domain`/`application`, adapters in
  `infrastructure`.
- Cost: more types and mapping code (entity ↔ domain, DTO ↔ domain) than a classic layered design.
- Reactor types appear in the domain port. This is accepted because backpressure and cancellation are part of the
  streaming contract, not an implementation detail.

## Alternatives considered

- **Classic controller → service → repository with vendor SDKs in services**: less code, but couples business logic
  to vendors and makes switching providers per message hard.
- **Spring AI `ChatModel` as the domain abstraction**: a quick start, but it ties the core to a framework abstraction
  and gives less control over reasoning deltas, engine-specific quirks, usage reporting and error translation. It
  remains possible to use Spring AI *inside* an adapter later.
