# Backend — AGENTS.md

Spring Boot 3.5 on Java 21. Read the [root AGENTS.md](../AGENTS.md) first for the architecture invariants, commands and
definition of done. Diagrams: [docs/architecture.md](../docs/architecture.md).

## Package layout (hexagonal)

```text
com.example.aichat
├── api/                 Inbound adapters: REST + SSE controllers, request/response records, RFC 9457 errors
│   ├── auth, chat, compare, conversation, provider, prompt, admin
│   └── error/           ApiExceptionHandler, Problems, LlmHttpStatus (LlmErrorCode → HTTP status)
├── application/         Use cases; provider-neutral orchestration
│   ├── chat/            ChatService, GenerationRegistry (cancel), TitleService, ConversationSummarizer,
│   │   │                StreamAccumulator, StaleGenerationCleaner, ChatEvent (sealed SSE event model)
│   │   └── context/     ConversationContextManager, ContextStrategy, TokenEstimator
│   ├── compare/         CompareService (one question → several providers in parallel)
│   ├── provider/        LlmProviderRegistry, ProviderHealthMonitor, ProviderSettingsService, ProviderCatalogService
│   ├── usage/           UsageRecorder, UsageDashboardService, CostEstimator (outbound port)
│   ├── auth/            AuthService, AccountValidator, PasswordHasher + TokenIssuer (outbound ports)
│   └── conversation, prompt, user, common
├── domain/              Pure model and ports (JDK + Reactor types only)
│   ├── llm/             LlmProvider port, LlmRequest/LlmResponse/LlmChunk/LlmMessage/LlmModel, ProviderHealth,
│   │                    ProviderDescriptor, LlmCapability, exception/ (LlmException hierarchy, LlmErrorCode)
│   └── conversation, user, prompt, provider, usage   Entities + repository ports
├── infrastructure/      Outbound adapters
│   ├── llm/             Provider wiring, resilience, metrics, error translation, pricing
│   │   ├── openai/      OpenAiLlmProvider (Responses API) + factory
│   │   ├── compatible/  OpenAiCompatibleLlmProvider (Chat Completions), ModelFamilyProfile, InferenceEngine,
│   │   │                ThinkTagParser + generic factory
│   │   ├── qwen/        QwenLlmProvider (reasoning switch per engine) + factory
│   │   └── llama/       LlamaLlmProvider (profile only) + factory
│   ├── persistence/     JPA entities, Spring Data repositories, adapters implementing the domain repository ports
│   └── security/        JWT issue/verify, SecurityConfiguration, RateLimitFilter, BCrypt
├── configuration/       Cross-cutting Spring configuration (metrics, OpenAPI, CORS, web)
├── common/              CorrelationIdFilter and tiny utilities (Texts, Times, Secrets)
└── rag/                 Knowledge base (RAG), a second hexagon with the same layering
    ├── domain/          KnowledgeSource/Chunk/Collection, IngestionStatus, ExtractedDocument and the ports
    │                    DocumentExtractor, ChunkingStrategy, EmbeddingProvider, VectorStore, KeywordSearcher,
    │                    repository ports (KnowledgeSourceRepository, KnowledgeChunkRepository, ...)
    ├── application/     Ingestion (DocumentIngestionService, TextIngestionService, KnowledgeSourceWriter,
    │                    IngestionQueue, IngestionPipeline), KnowledgeBaseService, KnowledgeCollectionService,
    │                    retrieval (DefaultRetrievalService, RerankingService), context (DefaultRagContextBuilder,
    │                    RagContextService), RagChatAugmenter (chat port), RagTestService, KnowledgeAccessPolicy,
    │                    RagProperties, RagMetrics, KnowledgeMaintenance (startup recovery, gauges)
    ├── infrastructure/  extractor/ (Tika 4 + own parsers), embedding/ (OpenAI-compatible, hashing),
    │                    vector/ (PgVectorStore), persistence/ (JdbcTemplate repositories, PostgresKeywordSearcher),
    │                    security/ (default KnowledgeAccessPolicy)
    └── api/             RagAdminController (/api/admin/knowledge), RagSearchController (search, test-answer),
                         KnowledgeBaseController (/api/knowledge for all users), KnowledgeDtos, exception handler
```

Placement rules:

- A new use case goes to `application/<feature>`; it talks to the outside only through ports (interfaces in
  `domain` or `application`) that `infrastructure` implements.
- Controllers stay thin: validate input, map DTOs and call one application service. No repository access from `api`.
- JPA entities never leave `infrastructure/persistence`; adapters map them to domain objects.
- Vendor-specific code (HTTP payloads, headers, error bodies, streaming formats) lives only in
  `infrastructure/llm/<vendor>`.

## How a provider is wired

1. `llm.providers.<id>` in `application.yml` binds to `ProviderProperties` (`type`, `family`, `engine`, `base-url`,
   `api-key`, `default-model`, `models`, timeouts, `retry`, `circuit-breaker`, `pricing`, ...).
2. `LlmProviderConfiguration` finds the `LlmProviderFactory` bean whose `type()` and `family()` match. If no
   family-specific factory exists, it falls back to the generic factory for the type.
3. The adapter is wrapped as `ObservedLlmProvider(ResilientLlmProvider(adapter))`: metrics outside, then circuit
   breaker, safe retry and timeout.
4. `LlmProviderRegistry` holds all providers and resolves `(provider, model)` for each request, applying the runtime
   overrides from `ProviderSettingsService` (admin UI, `provider_settings` table).

Adding a provider: follow the [add-llm-provider skill](../.github/skills/add-llm-provider/SKILL.md).

## Streaming chat flow

`POST /api/chat/stream` → `ChatController` returns `Flux<ServerSentEvent>` (Spring MVC with virtual threads) →
`ChatService.stream`:

1. Persists the user message and an assistant placeholder (`STREAMING`) in one transaction.
2. `ConversationContextManager` builds the neutral `LlmRequest`: system prompt kept separate, and the history trimmed
   by the configured strategy (default `SUMMARY_PLUS_RECENT`).
3. `LlmProviderRegistry.resolve(provider, model)` → `LlmProvider.stream(request)` emits `LlmChunk` values, which are
   mapped to `ChatEvent`s (`start`, `citations`, `delta`, `reasoning`, `done`, `error`, `title`).
4. On completion, failure or cancellation the assistant message is finalized, and `UsageRecorder` stores an
   `LlmExecution` (tokens, latency, cost, status).

Cancellation happens when the client disconnects, or explicitly via
`POST /api/chat/generations/{assistantMessageId}/cancel` (`GenerationRegistry`). The partial answer is kept.

With knowledge enabled (`knowledge` in the request, stored on the conversation), `ChatService` asks the
`KnowledgeAugmenter` port (`application/chat/knowledge`) before building the request. `ConversationContextManager`
appends the returned rules to the system message and puts the delimited excerpts before the question in the current
user turn only; a `citations` event follows `start`. Failures degrade to an answer without knowledge plus a notice.

## Knowledge base (RAG)

- **Ingestion**: controllers → `DocumentIngestionService`/`TextIngestionService` (validation, metadata) →
  `KnowledgeSourceWriter` (SHA-256 duplicates, versions, replacement; one transaction) → `IngestionQueue` (after
  commit) → `IngestionPipeline` (extract → chunk → embed → write chunks and vectors under the source row lock).
- **Retrieval**: `DefaultRetrievalService` (embed query, `VectorStore` search, optional `KeywordSearcher` + RRF,
  threshold, `RerankingService`, top K) → `DefaultRagContextBuilder` (budget, numbering, rules) →
  `RagChatAugmenter` (chat) or `RagTestService` (admin test console).
- **New file type**: a `DocumentExtractor` bean in `rag/infrastructure/extractor` plus an entry in `DocumentType`
  (extensions, accepted sniffed content types). **New embedding backend**: an `EmbeddingProviderFactory` bean whose
  `types()` contain the new `rag.embedding.provider` value. **New vector store**: implement `VectorStore` (and
  `KeywordSearcher`). Playbook:
  [knowledge-base skill](../.github/skills/knowledge-base/SKILL.md).
- Every change that affects retrieval (metadata, enable/disable, content, delete) locks the source row
  (`KnowledgeSourceRepository.lockStatus`) and updates `knowledge_vectors` in the same transaction.
- Settings: `rag.*` in `application.yml` bound to `RagProperties` (`RAG_*` environment variables).

## Commands

```bash
./mvnw -B -q -DskipTests compile            # fast compile check
./mvnw -B -DskipTests package               # builds target/ai-chat-backend.jar
../scripts/test.sh backend                  # all tests, container engine auto-configured (Docker or Podman)
../scripts/test.sh backend --test QwenLlmProviderTest
```

Use `./mvnw`, never a global `mvn`. The wrapper uses `JAVA_HOME` (JDK 21 or newer). The `macos-dns` Maven profile
activates automatically on macOS (native DNS resolver for Netty).

## Testing

- **Unit tests** (no Spring context): adapters against a WireMock server (`OpenAiLlmProviderTest`,
  `QwenLlmProviderTest`, `LlamaLlmProviderTest`, ...), plus Mockito tests for context strategies, the registry,
  resilience and pricing. Builders for properties, requests and SSE bodies live in `support/LlmTestSupport`.
- **Integration tests** extend `support/AbstractIntegrationTest`: one shared PostgreSQL 17 + pgvector Testcontainer
  (`pgvector/pgvector:pg17-trixie`) and one WireMock server. They provide the `openai`, `qwen` and `llama` providers
  (WireMock paths `/openai`, `/qwen/v1`, `/llama/v1`), an `offline` provider on a dead port, and helpers `register`,
  `login`, `adminToken`, `call`, `callStream`, `streamEvents`, `stubStream` and `stubTitle`.
- Knowledge-base tests use `support/FakeEmbeddingProvider` (deterministic vectors where shared vocabulary means
  similarity, call counters, `failing()` mode), `RagTestProperties` (unit tests) and `KnowledgeFixtures` (real PDF and
  DOCX bytes). `application-test.yml`
  uses small chunks (120/20) and a low threshold (0.15). Ingestion is asynchronous: poll the status endpoint
  (`awaitStatus`, `awaitIndexedAfter` in `KnowledgeBaseIntegrationTest`) instead of sleeping. Integration tests share
  one database, so use unique content (for example a random marker) to avoid duplicate-detection conflicts.
- Every test starts clean: `@BeforeEach` deletes `provider_settings` and resets WireMock.
  `application-test.yml` sets `llm.settings-reload-interval: PT0S`, so admin changes apply immediately.
- SSE tests must send `Accept: text/event-stream` (use `callStream`/`streamEvents`).
- Never call real LLM endpoints from tests.

## Gotchas

- Resilience4j `TimeLimiterOperator` is an overall timeout for a `Mono` but a **per-element** timeout for a `Flux`.
  For streams, the provider `timeout` therefore bounds time-to-first-token and gaps between tokens.
- Retry decisions belong to `LlmException.isRetryable()`. Once a stream has emitted anything, wrap errors with
  `nonRetryable()`. Never make timeouts retryable.
- Spring 6.2's `ResponseEntityExceptionHandler` already handles `AsyncRequestNotUsableException` (client went away).
  Do not add another handler for it.
- Problem details are rendered even when the request accepted only `text/event-stream`. Keep that behaviour; the
  frontend relies on it for errors raised before a stream starts.
- Surefire's `argLine` is assembled from the `<argLine/>` property plus the Mockito java agent. Keep the empty
  property, or `@{argLine}` fails when no other plugin sets it.
- Qwen 3 "thinking" is disabled per engine: `reasoning_effort: none` (Ollama), `chat_template_kwargs.enable_thinking`
  (vLLM, SGLang, llama.cpp) or the `/no_think` soft switch (generic). `<think>` tags in content are split out by
  `ThinkTagParser`.
- RAG persistence uses `JdbcTemplate`. Bind arrays as `String[]` with `CAST(? AS uuid[])` / `CAST(? AS text[])`; when
  an array is the only vararg, cast it to `(Object)`. Vectors are sent as text and cast to `vector`; searches compare
  `embedding::vector(n)` so that the per-dimension HNSW index is used.
- `PgVectorStore.prepare(model, dimensions)` creates indexes in its own transaction (`REQUIRES_NEW`) before the
  pipeline's write transaction; creating them inside it can deadlock with concurrent writers.
- Tika 4: parsers take a `TikaInputStream`, `TikaConfig` no longer exists (use `new AutoDetectParser()`), and encrypted
  PDFs raise `EncryptedDocumentException`. The OOXML schema classes are not on the test classpath (fixtures build
  DOCX files as raw ZIPs).
- Upload `tags`/`sourceUrl`/`collectionId` that are not sent keep the current values when a file replaces a source
  (`SourceMetadata.provided`); an explicitly empty value clears them.

## Database

PostgreSQL with Flyway migrations in `src/main/resources/db/migration` (`V1__initial_schema.sql`,
`V2__seed_system_prompts.sql`, `V3__knowledge_base.sql` with `CREATE EXTENSION vector`, ...). Hibernate only
validates the schema (`ddl-auto: validate`); the knowledge-base tables are accessed with `JdbcTemplate`. Schema changes
follow the [database-migration skill](../.github/skills/database-migration/SKILL.md).

## Observability

- Metrics: `llm_requests_total`, `llm_request_duration_seconds`, `llm_input_tokens`, `llm_output_tokens`,
  `llm_errors_total` (tags `provider`, `model`, `status`, plus `code` on errors), `llm_provider_up`,
  `llm_active_generations`; knowledge base (`RagMetrics`): `rag_ingestion_*`, `rag_embedding_*`, `rag_retrieval_*`,
  `rag_retrieved_chunks`, gauges `rag_documents` and `rag_chunks` (tags `embedding_provider`, `embedding_model`,
  `collection`, `status`).
- Every request gets an `X-Correlation-Id` (MDC key `correlationId`). Logs are structured (ECS) in Docker.
- Never log prompts, document text, API keys, `Authorization` headers or passwords.
