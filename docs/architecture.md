# Architecture

This document describes how AI Workspace is structured and why. Decisions are recorded as ADRs in [adr/](adr/README.md);
day-to-day rules for contributors are in [AGENTS.md](../AGENTS.md). Diagrams use Mermaid and render on GitHub and in
most IDEs.

Contents:
[Goals](#1-goals-and-constraints) ·
[Context](#2-system-context) ·
[Containers](#3-containers) ·
[Backend layers](#4-backend-layers-ports-and-adapters) ·
[Provider abstraction](#5-llm-provider-abstraction) ·
[Runtime flows](#6-runtime-flows) ·
[Context management](#7-context-management) ·
[Knowledge base](#8-knowledge-base-rag) ·
[Data model](#data-model) ·
[Deployment](#10-deployment) ·
[Cross-cutting concerns](#11-cross-cutting-concerns) ·
[Extension points](#12-extension-points) ·
[Limitations](#13-known-limitations-and-scaling-notes)

## 1. Goals and constraints

| Goal | How it is achieved |
|------|--------------------|
| Switch between cloud and local models at any time, even inside one conversation | Provider and model are chosen per request; every assistant message stores the provider/model that produced it |
| Add vendors without touching business logic | Hexagonal architecture; `LlmProvider` port; adapters created by `LlmProviderFactory` beans from configuration |
| Do not assume a specific local inference engine | One OpenAI-compatible adapter; engine quirks (Ollama, vLLM, llama.cpp, SGLang, LM Studio, LocalAI) are configuration |
| Responsive UX | Server-Sent Events from the model server through the backend to the browser, without buffering; stop at any time |
| Keep working when a local model is down | Health monitoring, circuit breakers, graceful errors with one-click alternatives |
| Keep secrets on the server | All LLM calls go through the backend; keys are never serialized or logged |
| Operable | Actuator health, Prometheus metrics, structured logs with correlation ids, an admin usage dashboard |
| Runs on a laptop | One `docker compose` stack; model servers are optional and pluggable |
| Ground answers in curated knowledge, with sources | A separate RAG module behind ports: pluggable extractors, embedding providers (independent of the chat provider), vector store and reranker; citations built from the retrieved chunks |

## 2. System context

```mermaid
flowchart LR
    user(["User<br/>browser"])
    admin(["Administrator<br/>browser"])
    ws["AI Workspace<br/>web UI + API"]
    openai["OpenAI API<br/>cloud, Responses API"]
    qwen["Qwen model server<br/>Ollama, vLLM, ..."]
    llama["Llama model server<br/>Ollama, llama.cpp, ..."]
    future["Future vendors<br/>Anthropic, Gemini, Bedrock, ..."]
    embed["Embedding model<br/>OpenAI or OpenAI-compatible,<br/>optional"]
    prom["Prometheus<br/>optional"]

    user -->|"chat with knowledge, compare"| ws
    admin -->|"users, providers, prompts, usage,<br/>knowledge base"| ws
    ws -->|"HTTPS + API key"| openai
    ws -->|"OpenAI-compatible HTTP"| qwen
    ws -->|"OpenAI-compatible HTTP"| llama
    ws -.->|"one adapter each"| future
    ws -.->|"POST /v1/embeddings"| embed
    prom -.->|"scrape metrics"| ws
```

## 3. Containers

```mermaid
flowchart LR
    browser["Browser<br/>React SPA"]

    subgraph compose["Docker Compose project: ai-workspace"]
        nginx["frontend<br/>nginx: static files + /api proxy<br/>127.0.0.1:3080"]
        backend["backend<br/>Spring Boot, Java 21<br/>127.0.0.1:8088"]
        db[("postgres<br/>PostgreSQL 17 + pgvector<br/>127.0.0.1:5434")]
        vllm["qwen · profile qwen<br/>vLLM, NVIDIA GPU<br/>:8001"]
        llamacpp["llama · profile llama<br/>llama.cpp server, NVIDIA GPU<br/>:8002"]
        ollamac["ollama · profile ollama<br/>CPU<br/>:11435"]
        promc["prometheus · profile monitoring<br/>:9090"]
    end

    hostollama["Ollama on the host<br/>:11434, Metal on macOS"]
    openai["api.openai.com"]

    browser -->|"HTML/JS, /api/* incl. SSE"| nginx
    nginx -->|"proxy, buffering off"| backend
    backend -->|"JDBC, Flyway"| db
    backend -->|HTTPS| openai
    backend -.-> vllm
    backend -.-> llamacpp
    backend -.-> ollamac
    backend -.->|"host.docker.internal"| hostollama
    promc -.->|"/actuator/prometheus"| backend
```

Dashed connections are optional. The backend starts and serves requests when none of the model servers are reachable;
those providers are reported as offline. The browser talks only to nginx (same origin), so no CORS is needed in
Docker. In dev mode, Vite proxies `/api` the same way. The knowledge base needs no extra service: chunks and vectors
live in the same PostgreSQL database (pgvector), and the embedding model is reached like a chat model (OpenAI or an
OpenAI-compatible server such as Ollama), or not at all with the offline hashing embeddings.

## 4. Backend layers (ports and adapters)

```mermaid
flowchart TB
    subgraph api["api · inbound adapters"]
        rest["REST controllers<br/>auth, conversations, providers,<br/>system prompts, admin"]
        ssec["SSE controllers<br/>chat stream / regenerate, compare"]
    end

    subgraph application["application · use cases"]
        chat["ChatService"]
        cmp["CompareService"]
        ctx["ConversationContextManager"]
        reg["LlmProviderRegistry"]
        hm["ProviderHealthMonitor"]
        use["UsageRecorder<br/>UsageDashboardService"]
        acc["AuthService<br/>AdminUserService"]
    end

    subgraph domain["domain · model and ports"]
        port{{"LlmProvider port"}}
        repo{{"repository ports"}}
        model["Conversation, Message, User,<br/>SystemPrompt, LlmExecution"]
    end

    subgraph infrastructure["infrastructure · outbound adapters"]
        llm["llm<br/>OpenAI, OpenAI-compatible, Qwen, Llama<br/>+ resilience, metrics, error translation"]
        jpa["persistence<br/>JPA entities + repository adapters"]
        sec["security<br/>JWT, BCrypt, rate limiting"]
    end

    api --> application
    application --> domain
    llm -. implements .-> port
    jpa -. implements .-> repo
    sec -. "implements PasswordHasher, TokenIssuer" .-> application
```

| Layer | May depend on | Contains |
|-------|---------------|----------|
| `domain` | JDK, Reactor (`Mono`/`Flux`) | Entities, value objects, the `LlmProvider` port, repository ports, the `LlmException` hierarchy |
| `application` | `domain` | Use cases, context management, provider registry and health, usage accounting, outbound ports such as `CostEstimator`, `PasswordHasher` and `TokenIssuer` |
| `api` | `application`, `domain` | Controllers, DTO records, SSE mapping, RFC 9457 error handling |
| `infrastructure` | `application`, `domain` | LLM adapters, JPA, security, configuration binding |

The application layer uses Spring stereotypes (`@Service`) for wiring, but no web, persistence or vendor types.
See [ADR 0001](adr/0001-hexagonal-architecture.md). The knowledge base is a second hexagon with the same layering in
`com.example.aichat.rag` ([section 8](#8-knowledge-base-rag)); chat reaches it only through the `KnowledgeAugmenter`
port that the chat application layer owns.

## 5. LLM provider abstraction

```mermaid
classDiagram
    direction LR
    class LlmProvider {
        <<interface>>
        +descriptor() ProviderDescriptor
        +chat(LlmRequest) Mono~LlmResponse~
        +stream(LlmRequest) Flux~LlmChunk~
        +availableModels() List~LlmModel~
        +health() ProviderHealth
    }
    class LlmProviderFactory {
        <<interface>>
        +type() String
        +family() String
        +create(providerId, ProviderProperties) LlmProvider
    }
    class LlmProviderRegistry {
        +resolve(provider, model) ResolvedProvider
        +all() List~LlmProvider~
    }
    class OpenAiLlmProvider {
        Responses API
    }
    class OpenAiCompatibleLlmProvider {
        Chat Completions API
        #customizeBody(body, request)
        #adaptMessages(request)
    }
    class QwenLlmProvider {
        reasoning switch per engine
    }
    class LlamaLlmProvider {
        family profile only
    }
    class ResilientLlmProvider {
        circuit breaker
        safe retry
        timeout
    }
    class ObservedLlmProvider {
        Micrometer metrics
    }

    LlmProvider <|.. OpenAiLlmProvider
    LlmProvider <|.. OpenAiCompatibleLlmProvider
    OpenAiCompatibleLlmProvider <|-- QwenLlmProvider
    OpenAiCompatibleLlmProvider <|-- LlamaLlmProvider
    LlmProvider <|.. ResilientLlmProvider
    LlmProvider <|.. ObservedLlmProvider
    ObservedLlmProvider o-- ResilientLlmProvider : decorates
    ResilientLlmProvider o-- LlmProvider : decorates adapter
    LlmProviderRegistry "1" o-- "*" LlmProvider
    LlmProviderFactory ..> LlmProvider : creates
```

- **Neutral types** (`domain/llm`): `LlmRequest` (model, messages, `GenerationOptions`), `LlmMessage`, `LlmResponse`,
  `LlmChunk` (sealed: `TextDelta`, `ReasoningDelta`, `Completed` with `LlmUsage` and `FinishReason`), `LlmModel`,
  `ProviderDescriptor`, `ProviderHealth`, `LlmCapability`.
- **Wiring**: every entry under `llm.providers.<id>` is turned into an adapter by the factory matching `type`
  (`openai`, `openai-compatible`, ...) and optional `family` (`qwen`, `llama`). The adapter is then decorated with
  resilience and metrics and registered. No code switches on provider ids. See [ADR 0002](adr/0002-provider-port-registry-and-factories.md).
- **Local engines**: one Chat Completions adapter serves Ollama, vLLM, SGLang, llama.cpp, LM Studio and LocalAI. The
  `engine` setting only selects quirks, such as how Qwen 3 thinking is switched off. See [ADR 0003](adr/0003-openai-compatible-adapter-for-local-models.md).
- **Resolution**: `resolve(provider, model)` falls back to the default provider and to the provider's effective
  default model (configuration, overridden by the admin settings). It fails with a clear error for unknown or
  disabled providers.

## 6. Runtime flows

### 6.1 Streaming a chat message

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant UI as React · useChatSender
    participant CC as ChatController
    participant CS as ChatService
    participant R as LlmProviderRegistry
    participant CM as ConversationContextManager
    participant P as Provider · metrics, resilience, adapter
    participant M as Model server
    participant DB as PostgreSQL

    U->>UI: Send
    UI->>CC: POST /api/chat/stream with JWT, Accept text/event-stream
    CC->>CS: stream(command)
    CS->>R: resolve(provider, model)
    CS->>DB: insert user message and assistant placeholder (STREAMING)
    CS->>CM: prepare(system prompt, summary, history)
    CM-->>CS: LlmRequest within the token budget
    CS-->>UI: event start (conversation, message ids, provider, model)
    CS->>P: stream(LlmRequest)
    P->>M: POST chat/completions or responses with stream=true
    loop while tokens arrive
        M-->>P: SSE chunk
        P-->>CS: TextDelta or ReasoningDelta
        CS-->>UI: event delta or reasoning
    end
    M-->>P: usage and end of stream
    P-->>CS: Completed (usage, finish reason)
    CS->>DB: complete assistant message, insert llm_execution
    CS-->>UI: event done (tokens, latency, cost)
    opt first answer of the conversation
        CS->>P: title generation in the background
        CS-->>UI: event title
    end
    Note over CS,DB: summarisation of older messages runs in the background after the answer
```

The SSE response is a `Flux<ServerSentEvent>` returned from a Spring MVC controller running on virtual threads.
Heartbeat comments every 15 s keep proxies from closing idle streams. nginx runs with `proxy_buffering off`, and the
backend sends `X-Accel-Buffering: no`. See [ADR 0004](adr/0004-server-sent-events-for-streaming.md) and
[ADR 0005](adr/0005-servlet-stack-with-virtual-threads.md).

### 6.2 Provider unavailable and switching providers

```mermaid
sequenceDiagram
    participant UI as React
    participant CS as ChatService
    participant Q as Qwen provider
    participant H as ProviderHealthMonitor
    participant L as Llama provider

    UI->>CS: POST /api/chat/stream provider=qwen
    CS->>Q: stream(request)
    Q--xCS: LlmProviderUnavailableException (connection refused)
    Note over Q: retried only while nothing was emitted, the circuit breaker counts the failure
    CS->>H: reportFailure(qwen) marks it offline immediately
    CS-->>UI: event error (PROVIDER_UNAVAILABLE, message, alternatives)
    Note over UI: "Qwen Local is currently unavailable. Switch to OpenAI or Llama Local"
    UI->>CS: POST /api/chat/regenerate provider=llama, same conversation
    CS->>L: stream(request with the same history)
    L-->>CS: deltas, Completed
    CS-->>UI: start, delta, done (message stored with provider=llama)
```

Vendor errors are translated into neutral exceptions (`LlmErrorCode`: `PROVIDER_UNAVAILABLE`,
`AUTHENTICATION_FAILED`, `RATE_LIMITED`, `TIMEOUT`, `INVALID_REQUEST`, `MODEL_NOT_FOUND`, `CONTENT_FILTERED`,
`UPSTREAM_ERROR`, `STREAM_INTERRUPTED`). Before a stream starts they become problem details; once streaming, they
become an `error` event. The conversation is never lost: the failed answer is stored with status `ERROR`.

### 6.3 Stopping a generation

```mermaid
sequenceDiagram
    participant UI as React
    participant CC as ChatController
    participant G as GenerationRegistry
    participant CS as ChatService stream
    participant P as Provider

    UI->>CC: POST /api/chat/generations/{assistantMessageId}/cancel
    CC->>G: cancel(messageId, userId), owner checked
    G->>CS: dispose the subscription
    CS->>P: cancel, the HTTP request to the model server is aborted
    CS->>CS: store partial answer (CANCELLED), record execution (CANCELLED)
    CS-->>UI: event done with status CANCELLED
    CC-->>UI: 202 Accepted
    UI->>UI: AbortController.abort() closes the stream
```

Closing the connection alone (tab closed, network lost) has the same effect: the subscription is cancelled when the
servlet container reports the disconnect.

### 6.4 Compare mode

```mermaid
sequenceDiagram
    participant UI as ComparePage
    participant CS as CompareService
    participant A as OpenAI
    participant B as Qwen Local
    participant C as Llama Local

    UI->>CS: POST /api/compare/stream (message, targets)
    CS-->>UI: start (runId, targets t0, t1, t2)
    par target t0
        CS->>A: stream
        A-->>CS: deltas
        CS-->>UI: delta t0 ... done t0 (latency, tokens, cost)
    and target t1
        CS->>B: stream
        B-->>CS: deltas
        CS-->>UI: delta t1 ... done t1 (latency, tokens, Cost: Local)
    and target t2
        CS->>C: stream
        C--xCS: unavailable
        CS-->>UI: error t2, other targets continue
    end
```

Compare runs are not stored in conversations. Each call is recorded in `llm_executions` with purpose `COMPARE`, so it
shows up in the usage dashboard. At most `chat.compare.max-targets` (default 4) targets run per request.

### 6.5 Provider health monitoring

```mermaid
sequenceDiagram
    participant S as Scheduler, every 30 s
    participant H as ProviderHealthMonitor
    participant P as Provider adapters
    participant UI as React, polls every 30 s

    S->>H: checkAll()
    H->>P: health() e.g. GET /v1/models, never inference
    P-->>H: UP or DOWN with latency and message
    Note over H: real chat traffic also reports success and failure (passive signals)
    UI->>H: GET /api/providers
    H-->>UI: available, status, statusMessage per provider
```

The `llm_provider_up` gauge exposes the same information to Prometheus. Administrators can force a check from
**Admin → Providers → Check now**.

### 6.6 Authentication and authorization

```mermaid
sequenceDiagram
    participant UI as React
    participant RL as RateLimitFilter
    participant AU as AuthController and AuthService
    participant DB as PostgreSQL
    participant JC as CurrentUserJwtConverter
    participant API as Protected endpoint

    UI->>RL: POST /api/auth/login
    RL->>AU: allowed, max 10 per minute per IP
    AU->>DB: load user, verify BCrypt hash, update lockout counter
    AU-->>UI: access token (JWT HS256, 12 h) and profile
    UI->>JC: request with Authorization Bearer token
    JC->>DB: load account status and role (cached briefly)
    alt account disabled or deleted
        JC-->>UI: 401 problem details
    else active
        JC->>API: CurrentUser(id, role)
        API-->>UI: data owned by this user only
    end
```

Tokens are stateless, but every request checks the live account state. Disabling or deleting a user therefore takes
effect immediately ([ADR 0008](adr/0008-stateless-jwt-with-live-account-checks.md)). Repositories always filter by the
owner, so other users' conversations return 404.

## 7. Context management

`ConversationContextManager` prepares a provider-neutral request from the conversation:

| Strategy | Behaviour |
|----------|-----------|
| `FULL_CONTEXT` | All completed messages |
| `LAST_N_MESSAGES` | The last `chat.context.last-n-messages` (20), then trimmed to the token budget |
| `TOKEN_WINDOW` | The newest messages that fit `chat.context.max-context-tokens` (8000) |
| `SUMMARY_PLUS_RECENT` (default) | Rolling summary of older messages + the newest messages that fit the budget |

- The system prompt (selected profile) and the rolling summary form one leading system message, kept separate from
  the history.
- Failed and in-flight answers are excluded. Roles are normalised to alternate, because some chat templates reject
  anything else.
- `ConversationSummarizer` runs in the background after an answer when the unsummarised history exceeds
  `summary-trigger-tokens` (6000). It folds all but the last `summary-keep-recent-messages` (6) into the summary,
  using the conversation's provider (or `chat.summary.provider`), so summarisation never delays a response.
- Token counts are estimated (`TokenEstimator`, about 3.5 characters per token). Exact usage reported by providers
  is stored per execution.

See [ADR 0006](adr/0006-summary-plus-recent-context-strategy.md).

## 8. Knowledge base (RAG)

Administrators upload documents or write text; the backend extracts, chunks and embeds them in the background and
stores chunks and vectors in PostgreSQL (pgvector). When a conversation has knowledge enabled, the question is used to
retrieve the most relevant chunks, which are added to the LLM request with rules and numbered for citations. See
[ADR 0009](adr/0009-rag-module-with-pgvector.md) and [ADR 0010](adr/0010-asynchronous-ingestion-and-index-consistency.md).

### 8.1 Components

```mermaid
flowchart TB
    subgraph rapi["rag.api"]
        adminc["RagAdminController<br/>/api/admin/knowledge"]
        searchc["RagSearchController<br/>search, test-answer"]
        userc["KnowledgeBaseController<br/>/api/knowledge"]
    end

    subgraph rapp["rag.application"]
        ingest["DocumentIngestionService<br/>TextIngestionService"]
        writer["KnowledgeSourceWriter<br/>checksums, versions, metadata"]
        queue["IngestionQueue<br/>bounded background pool"]
        pipeline["IngestionPipeline"]
        kb["KnowledgeBaseService<br/>KnowledgeCollectionService"]
        retrieval["DefaultRetrievalService"]
        rerank["RerankingService"]
        ctxb["DefaultRagContextBuilder"]
        aug["RagChatAugmenter"]
        test["RagTestService"]
        policy["KnowledgeAccessPolicy"]
    end

    subgraph rports["rag.domain · ports"]
        ext{{"DocumentExtractor"}}
        chunk{{"ChunkingStrategy"}}
        emb{{"EmbeddingProvider"}}
        vs{{"VectorStore"}}
        kw{{"KeywordSearcher"}}
        repos{{"repository ports"}}
    end

    subgraph rinfra["rag.infrastructure"]
        extractors["extractor<br/>PDF, DOCX, HTML via Tika 4;<br/>text, Markdown, CSV, JSON"]
        embedders["embedding<br/>OpenAI-compatible, hashing"]
        pgv["vector<br/>PgVectorStore"]
        jdbc["persistence<br/>JdbcTemplate repositories,<br/>PostgresKeywordSearcher"]
    end

    chat["ChatService"] -->|"KnowledgeAugmenter port"| aug
    adminc --> ingest
    adminc --> kb
    searchc --> test
    userc --> policy
    ingest --> writer --> queue --> pipeline
    pipeline --> ext
    pipeline --> chunk
    pipeline --> emb
    pipeline --> vs
    aug --> policy
    aug --> retrieval
    test --> retrieval
    retrieval --> emb
    retrieval --> vs
    retrieval --> kw
    retrieval --> rerank
    aug --> ctxb
    test --> ctxb
    test -->|"LlmProviderRegistry"| llm["LlmProvider"]
    extractors -. implements .-> ext
    embedders -. implements .-> emb
    pgv -. implements .-> vs
    jdbc -. implements .-> kw
    jdbc -. implements .-> repos
```

| Rule | Why |
|------|-----|
| The chat module depends only on its own `KnowledgeAugmenter` port | Chat works unchanged without the RAG module; RAG can evolve freely |
| LLM providers never see storage; the vector store never sees conversations | Each part can be replaced alone (another vector database, another chat vendor) |
| Embeddings are configured separately from chat providers | Any chat model can answer with knowledge embedded by any embedding model |
| Persistence uses `JdbcTemplate`, not JPA | pgvector types, arrays, JSONB, batch inserts and targeted updates under row locks |

### 8.2 Ingestion

```mermaid
sequenceDiagram
    autonumber
    actor A as Administrator
    participant API as RagAdminController
    participant ING as DocumentIngestionService
    participant W as KnowledgeSourceWriter
    participant DB as PostgreSQL
    participant Q as IngestionQueue
    participant P as IngestionPipeline
    participant X as DocumentExtractor
    participant C as ChunkingStrategy
    participant E as EmbeddingProvider
    participant V as VectorStore

    A->>API: POST /api/admin/knowledge/files (multipart)
    API->>ING: upload(file, metadata, duplicate strategy)
    ING->>ING: validate name, extension, size and real content type
    ING->>W: ingest(content, metadata)
    W->>DB: find sources with the same SHA-256 checksum
    alt duplicate without a strategy
        W-->>A: 409 DUPLICATE_DOCUMENT with the existing source
    else new source, new version or replacement
        W->>DB: insert or update source (UPLOADED) and original file, one transaction
        W->>Q: submit(sourceId) after commit
        API-->>A: 202 sourceId, status UPLOADED
    end
    Q->>P: run(sourceId) on a rag-ingest thread
    P->>X: extract text, pages and sections (timeout, character limit)
    P->>DB: EXTRACTING, then EXTRACTED with the extracted text
    P->>C: chunk(document)
    P->>E: embed chunks in batches
    P->>V: prepare(model, dimensions), creates the HNSW index if missing
    P->>DB: lock source row, stop if DELETING
    P->>V: replace chunks and vectors (same transaction)
    P->>DB: INDEXED, chunk count, embedding model, run statistics
    loop while processing
        A->>API: GET /api/admin/knowledge/{id}/status
    end
```

### 8.3 Answering with knowledge

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant CS as ChatService
    participant AUG as RagChatAugmenter
    participant R as RetrievalService
    participant E as EmbeddingProvider
    participant V as VectorStore
    participant K as KeywordSearcher
    participant RR as RerankingService
    participant CB as RagContextBuilder
    participant L as LlmProvider

    U->>CS: POST /api/chat/stream with knowledge enabled
    CS->>AUG: augment(question, collections)
    AUG->>AUG: readable collections (enabled, selected, KnowledgeAccessPolicy)
    AUG->>R: search(question, options)
    R->>E: embed the question
    R->>V: nearest vectors of the active model, filtered
    opt HYBRID mode
        R->>K: full-text candidates
    end
    R->>RR: rerank candidates above the threshold
    R-->>AUG: top K chunks
    AUG->>CB: build context within the token budget
    AUG->>AUG: record rag_execution and retrieved chunks
    AUG-->>CS: rules, knowledge block, citations
    CS-->>U: event start, event citations
    CS->>L: system prompt + rules, history, knowledge block + question
    L-->>CS: token stream
    CS-->>U: event delta ..., event done
```

- The rules go into the system message; the excerpts go only into the current user turn, between `<knowledge>`
  delimiters, and are never stored in `messages`. Excerpts are untrusted data: the rules say so, and delimiter
  look-alikes inside documents are neutralised.
- Citations are built from the chunks included in the context (number, source, page, section, score) and stored in
  `rag_retrieved_chunks`, so they are shown again when the conversation is reopened and survive source deletion.
- If retrieval fails or finds nothing, the answer is generated without knowledge and carries a notice.

### 8.4 Index consistency

| Situation | Behaviour |
|-----------|-----------|
| Concurrent changes to one source | Every write locks the source row (`SELECT … FOR UPDATE`). The pipeline re-reads collection, enabled flag and tags under the lock before it writes vectors, so a metadata edit during indexing is never lost. |
| Reindex | Chunks and vectors are replaced in one transaction; the old ones stay searchable until then and duplicates cannot occur. |
| Content changes (text edit, replaced file) | Old chunks and vectors are deleted immediately, so outdated knowledge is never retrieved. |
| Delete | The source is marked `DELETING` (a running pipeline discards its result), then source, file, text, chunks and vectors are removed in one transaction. |
| Disable (source or collection) | The `source_enabled` flag on the vector rows is updated in the same transaction; retrieval excludes it immediately. |
| Embedding model change | Vectors carry their model; retrieval only uses the active model; sources indexed with another model are reported as stale until *Reindex all*. |
| Restart during work | `KnowledgeMaintenance` queues sources in processing states again and completes interrupted deletions. |

### 8.5 Knowledge data model

```mermaid
erDiagram
    KNOWLEDGE_COLLECTIONS ||--o{ KNOWLEDGE_SOURCES : groups
    KNOWLEDGE_SOURCES ||--o| KNOWLEDGE_SOURCE_FILES : "original file"
    KNOWLEDGE_SOURCES ||--o| KNOWLEDGE_SOURCE_CONTENTS : "extracted text"
    KNOWLEDGE_SOURCES ||--o{ KNOWLEDGE_CHUNKS : "split into"
    KNOWLEDGE_CHUNKS ||--o| KNOWLEDGE_VECTORS : "embedded as"
    KNOWLEDGE_SOURCES |o--o{ KNOWLEDGE_SOURCES : "previous version of"
    RAG_EXECUTIONS ||--o{ RAG_RETRIEVED_CHUNKS : returned
    KNOWLEDGE_CHUNKS |o--o{ RAG_RETRIEVED_CHUNKS : "cited as"

    KNOWLEDGE_COLLECTIONS {
        uuid id PK
        varchar name UK
        varchar description
        boolean enabled
        boolean is_default
    }
    KNOWLEDGE_SOURCES {
        uuid id PK
        varchar type "FILE, TEXT, URL reserved"
        varchar title
        varchar original_filename
        varchar mime_type
        varchar source_url
        uuid collection_id FK
        varchar status "UPLOADED ... INDEXED, FAILED, DELETING"
        boolean enabled
        text_array tags
        varchar checksum "SHA-256"
        bigint file_size
        int version
        uuid parent_source_id FK
        uuid created_by FK
        varchar embedding_provider
        varchar embedding_model
        int chunk_count
        varchar error_message
        jsonb ingestion_stats
    }
    KNOWLEDGE_SOURCE_FILES {
        uuid source_id PK
        bytea content
    }
    KNOWLEDGE_SOURCE_CONTENTS {
        uuid source_id PK
        text content
        jsonb structure "pages, sections, properties"
    }
    KNOWLEDGE_CHUNKS {
        uuid id PK
        uuid source_id FK
        uuid collection_id
        int chunk_index
        text content
        int token_count
        int character_start
        int character_end
        int page_number
        varchar section_title
        jsonb metadata
        tsvector content_tsv "generated, GIN index"
    }
    KNOWLEDGE_VECTORS {
        uuid chunk_id PK
        uuid source_id FK
        uuid collection_id
        varchar embedding_model
        int dimensions
        vector embedding "HNSW per dimension"
        boolean source_enabled
        text_array tags
    }
    RAG_EXECUTIONS {
        uuid id PK
        varchar purpose "CHAT or TEST"
        uuid conversation_id FK
        uuid message_id FK
        uuid user_id FK
        text query
        uuid_array collection_ids
        varchar embedding_model
        varchar retrieval_mode
        int retrieved_chunk_count
        int total_context_tokens
        bigint embedding_duration_ms
        bigint retrieval_duration_ms
        varchar status "SUCCESS, EMPTY, ERROR"
    }
    RAG_RETRIEVED_CHUNKS {
        uuid rag_execution_id PK
        int rank PK
        uuid chunk_id FK "SET NULL on delete"
        uuid source_id FK
        varchar source_title
        int page_number
        double similarity_score
        double keyword_score
        double reranker_score
        int citation_index
    }
```

- Collection, enabled flag and tags are copied onto `knowledge_vectors`, so a single indexed query applies every
  filter; they are updated together with the source.
- `knowledge_vectors.embedding` has no fixed dimension. An HNSW cosine index on `embedding::vector(n)` is created
  per dimension (up to 2000); queries always filter by model and dimension.
- `rag_executions.conversation_id`, `message_id` and `user_id` are set to `NULL` when those rows are deleted;
  `rag_retrieved_chunks` keeps the source title, page and section for citations.

<a id="data-model"></a>

## 9. Data model

```mermaid
erDiagram
    USERS ||--o{ CONVERSATIONS : owns
    CONVERSATIONS ||--o{ MESSAGES : contains
    SYSTEM_PROMPTS |o--o{ CONVERSATIONS : "profile of"
    MESSAGES |o--o{ LLM_EXECUTIONS : "produced by"
    CONVERSATIONS |o--o{ LLM_EXECUTIONS : "billed to"
    USERS |o--o{ LLM_EXECUTIONS : "requested by"

    USERS {
        uuid id PK
        varchar username UK
        varchar email UK
        varchar password_hash
        varchar role "USER or ADMIN"
        varchar status "ACTIVE, DISABLED, LOCKED"
        int failed_login_attempts
        timestamptz locked_until
    }
    CONVERSATIONS {
        uuid id PK
        uuid user_id FK
        varchar title
        varchar title_source "DEFAULT, GENERATED, USER"
        uuid system_prompt_id FK
        varchar last_provider
        varchar last_model
        text summary "rolling summary"
        timestamptz summarized_until
        boolean knowledge_enabled
        uuid_array knowledge_collection_ids "empty = all"
    }
    MESSAGES {
        uuid id PK
        uuid conversation_id FK
        varchar role "USER or ASSISTANT"
        text content
        text reasoning
        varchar provider "assistant messages"
        varchar model "assistant messages"
        varchar status "STREAMING, COMPLETE, CANCELLED, ERROR"
        varchar error_code
    }
    LLM_EXECUTIONS {
        uuid id PK
        uuid message_id FK "nullable"
        uuid conversation_id FK "nullable"
        uuid user_id FK "nullable"
        varchar provider
        varchar model
        varchar provider_kind "CLOUD or LOCAL"
        varchar purpose "CHAT, COMPARE, TITLE, SUMMARY, RAG_TEST"
        int input_tokens
        int output_tokens
        bigint latency_ms
        bigint time_to_first_token_ms
        numeric estimated_cost
        varchar status "SUCCESS, ERROR, CANCELLED"
    }
    SYSTEM_PROMPTS {
        uuid id PK
        varchar name UK
        text content
        boolean is_default
        boolean enabled
        int sort_order
    }
    PROVIDER_SETTINGS {
        varchar provider_id PK
        boolean enabled
        varchar default_model
        int timeout_seconds
        double temperature
        int max_tokens
    }
```

- Every assistant message records `provider` and `model`, so one conversation can mix answers from several models.
- `llm_executions` holds one row per LLM call, including titles and summaries. References use `ON DELETE SET NULL`,
  so usage statistics survive deletions.
- `provider_settings` stores administrator overrides; `NULL` means "use the configured value".
- The knowledge-base tables are described in [section 8.5](#85-knowledge-data-model).
- The schema is managed by Flyway (`backend/src/main/resources/db/migration`); Hibernate only validates it.

## 9. Deployment

| Mode | Command | Components |
|------|---------|------------|
| Containers | `scripts/deploy.sh` | postgres, backend, frontend (+ optional profiles) |
| Development | `scripts/run-dev.sh start` | postgres container; backend jar and Vite dev server on the host |
| Status | `scripts/status.sh` | Checks everything above plus providers, model servers, the embedding model and the knowledge base |

Local model options:

| Setup | Base URL seen by the backend container | Notes |
|-------|----------------------------------------|-------|
| Ollama on the host (recommended on macOS) | `http://host.docker.internal:11434/v1` | Uses the Apple GPU (Metal); containers on macOS cannot |
| `--profile ollama` | `http://ollama:11434/v1` | CPU-only on macOS; pulls the configured models on first start |
| `--profile qwen` | `http://qwen:8000/v1` | vLLM, NVIDIA GPU, Hugging Face model id |
| `--profile llama` | `http://llama:8000/v1` | llama.cpp `server-cuda`, NVIDIA GPU, GGUF model |
| Any other server | Any URL | LM Studio, LocalAI, SGLang, remote vLLM, ... |

Container health: postgres uses `pg_isready`, and the backend uses `/actuator/health/readiness` (it depends on the
database, not on LLM providers). All ports bind to `127.0.0.1`. The database image is `pgvector/pgvector:pg17-trixie`
(PostgreSQL 17 with pgvector, same Debian base as `postgres:17`, so existing volumes keep their collations). nginx
allows larger request bodies only for the upload endpoint (`NGINX_MAX_UPLOAD_SIZE`) and streams them to the backend.

Embedding options for the knowledge base: OpenAI (`RAG_EMBEDDING_PROVIDER=openai`), any OpenAI-compatible
`/v1/embeddings` server such as Ollama on the host (`ollama`, `http://host.docker.internal:11434/v1`), or offline
hashing embeddings (`hashing`, no model). On the first run, `deploy.sh` picks an embedding model served by local
Ollama if there is one, and hashing otherwise.

## 10. Cross-cutting concerns

**Configuration.** Precedence, from lowest to highest: `application.yml` defaults, then environment variables
(`.env` via Compose), then runtime overrides in `provider_settings` (enabled, default model, timeout, temperature,
max tokens). Overrides are reloaded every 15 s, so several backend instances converge. No URL, model, credential,
timeout or token limit is hardcoded.

**Security.**
- JWT (HS256) bearer tokens; passwords hashed with BCrypt (strength 12); lockout after 5 failed logins for 15 min.
- Roles `USER` and `ADMIN`; `/api/admin/**` and non-public actuator endpoints require `ADMIN`.
- Per-user data isolation in every repository query.
- Token-bucket rate limits: auth 10/min per IP, chat 30/min per user, compare 10/min per user. At most 3
  concurrent generations per user.
- CORS allow-list for development origins.
- API keys masked in the admin API and never logged. Prompts are not logged by default.
- Knowledge base: management endpoints (`/api/admin/knowledge/**`) are `ADMIN` only; users reach knowledge only
  through chat and cited excerpts of collections that `KnowledgeAccessPolicy` lets them read. Uploads are validated
  (file name sanitising, extension allow-list, content sniffing, size limits, extracted-character limit against
  decompression bombs, extraction timeout); extracted text is never rendered as HTML; retrieved text is treated as
  untrusted data in prompts.

**Resilience** (per provider, Resilience4j; see [ADR 0007](adr/0007-retry-only-when-safe.md)):
- Circuit breaker: count-based window of 20 calls, opens at 50 % failures, half-open after 20–30 s. Only provider
  failures count, not invalid requests.
- Retry: at most 2 attempts with exponential backoff, only for retryable errors (connection refused, 502/503, short
  429 with `Retry-After` of 5 s or less), never after the first streamed chunk and never on timeouts.
- Timeout: overall for non-streaming calls; for streams it bounds the wait for each chunk (time to first token
  included).
- `StaleGenerationCleaner` finalizes answers left in `STREAMING` after a crash.

**Observability.**
- Metrics: `llm_requests_total`, `llm_request_duration_seconds`, `llm_input_tokens`, `llm_output_tokens`,
  `llm_errors_total` (tags `provider`, `model`, `status`, plus `code` on errors), `llm_provider_up` and
  `llm_active_generations`; knowledge base: `rag_ingestion_total`, `rag_ingestion_failed_total`,
  `rag_ingestion_duration_seconds`, `rag_documents`, `rag_chunks`, `rag_embedding_requests_total`,
  `rag_embedding_duration_seconds`, `rag_retrieval_requests_total`, `rag_retrieval_duration_seconds`,
  `rag_retrieved_chunks` (tags `embedding_provider`, `embedding_model`, `collection`, `status`). Document text is
  never logged.
- `X-Correlation-Id` on every request and log line; structured ECS logs in containers.
- Actuator liveness and readiness probes; the admin usage dashboard (requests, tokens, cost, latency, error rate,
  local vs cloud).

## 11. Extension points

| Capability | Where it plugs in |
|------------|-------------------|
| New vendors: Anthropic, Gemini, Mistral, DeepSeek, Azure OpenAI, Bedrock | One adapter + factory, or configuration only for OpenAI-compatible APIs ([skill](../.github/skills/add-llm-provider/SKILL.md)) |
| Tool/function calling, MCP servers | Tool definitions on `LlmRequest`, a `ToolCall` chunk type, gating on `LlmCapability.TOOL_CALLING`, an application-level tool executor with an MCP client as an outbound adapter |
| Multimodal input, file uploads | Content parts on `LlmMessage`, `LlmCapability.VISION`, a storage port |
| Knowledge connectors (web pages, Google Drive, SharePoint, Confluence, GitHub, Notion, S3, OneDrive), scheduled sync | A connector producing `NewContent` for `KnowledgeSourceWriter`; source type `URL` is reserved and checksums detect changes |
| OCR, image understanding | Another `DocumentExtractor` ordered before the PDF extractor |
| Other vector databases (Qdrant, Weaviate, Pinecone, Milvus, Elasticsearch, OpenSearch) | Implement `VectorStore` (and `KeywordSearcher` for hybrid search) |
| Rerankers (cross-encoder, Cohere, LLM), query rewriting, multi-query, HyDE, parent-child retrieval, GraphRAG | `RerankingService`; the retrieval step in `RagContextService`; `ChunkingStrategy` metadata; an extra retriever fused like keyword results |
| Collection-level permissions | A `KnowledgeAccessPolicy` bean (roles, groups, grants) |
| Web search | Another knowledge source for `ChatService`, through a port like `KnowledgeAugmenter` |
| Speech-to-text, text-to-speech | Separate ports (`TranscriptionProvider`, `SpeechProvider`) using the same registry/factory pattern |
| Agentic workflows | An application service orchestrating several `LlmProvider` calls and tools; SSE already supports new event types |
| Accurate token budgets | A model-specific `TokenEstimator` implementation |

These are designed seams, not implemented features (the knowledge base itself is implemented, see
[section 8](#8-knowledge-base-rag)).

## 12. Known limitations and scaling notes

- `GenerationRegistry` (explicit cancel), the rate-limit buckets and the cached health state live in memory per
  backend instance. With several instances, route a user's requests to the same instance (sticky sessions) or move
  this state to a shared store such as Redis. Closing the SSE connection cancels on any instance.
- Each open stream holds one virtual thread and one HTTP connection to the model server. This is cheap, but local
  model servers usually process only a few requests in parallel.
- Cost estimates come from the configured price table (`llm.providers.openai.pricing`) and must be kept in sync with
  vendor pricing.
- Context budgeting uses an estimated token count; very different tokenizers can over- or under-fill the context.
- Logout is client-side (stateless JWT). Account disabling and deletion still take effect immediately.
- The ingestion queue is in memory per backend instance. Several instances are safe (row locks serialise writes per
  source, and startup recovery re-queues unfinished work), but work is not redistributed when an instance stops; a
  shared job queue would be the next step.
- Original files are stored in PostgreSQL (`bytea`, up to `RAG_MAX_FILE_SIZE`). For large volumes, move them to
  object storage behind `KnowledgeFileStore`.
- pgvector's HNSW index supports up to 2000 dimensions; larger embeddings are searched exactly (correct, but slower
  on big knowledge bases) unless shortened with `RAG_EMBEDDING_DIMENSIONS`.
- No OCR: scanned documents without a text layer cannot be indexed. Offline hashing embeddings match words, not
  meaning; use a real embedding model for semantic search.

## Architecture decision records

| ADR | Decision |
|-----|----------|
| [0001](adr/0001-hexagonal-architecture.md) | Hexagonal (ports and adapters) backend |
| [0002](adr/0002-provider-port-registry-and-factories.md) | `LlmProvider` port with registry and factories |
| [0003](adr/0003-openai-compatible-adapter-for-local-models.md) | One OpenAI-compatible adapter for local models |
| [0004](adr/0004-server-sent-events-for-streaming.md) | Server-Sent Events over POST for streaming |
| [0005](adr/0005-servlet-stack-with-virtual-threads.md) | Servlet stack with virtual threads, WebClient for outbound calls |
| [0006](adr/0006-summary-plus-recent-context-strategy.md) | `SUMMARY_PLUS_RECENT` as the default context strategy |
| [0007](adr/0007-retry-only-when-safe.md) | Retry LLM calls only when safe |
| [0008](adr/0008-stateless-jwt-with-live-account-checks.md) | Stateless JWT with live account checks |
| [0009](adr/0009-rag-module-with-pgvector.md) | Knowledge base as a separate RAG module behind ports, PostgreSQL + pgvector as the first vector store |
| [0010](adr/0010-asynchronous-ingestion-and-index-consistency.md) | Asynchronous ingestion with row-locked, transactional index updates |
