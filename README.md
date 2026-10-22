# AI Workspace

A production-grade, full-stack AI chat application with **switchable LLM providers**. Chat with **OpenAI** (cloud),
**Qwen** and **Llama** (local, OpenAI-compatible servers such as Ollama, vLLM or llama.cpp) — and switch provider or
model at any time, even in the middle of a conversation, without restarting anything. Answers can be grounded in a
curated **knowledge base** (RAG over PostgreSQL + pgvector) with citations to the exact source passages.

- **Backend:** Java 21 · Spring Boot 3.5 · Spring MVC + WebClient · Spring Security (JWT) · JPA/PostgreSQL · Flyway ·
  pgvector · Apache Tika · Resilience4j · Micrometer/Prometheus
- **Frontend:** React 19 · TypeScript · Vite · Tailwind CSS · TanStack Query · Zustand
- **Infrastructure:** Docker Compose (frontend + backend + PostgreSQL/pgvector, optional GPU model servers, Prometheus)

[Features](#features) · [Quick start](#quick-start-docker-compose) · [Scripts](#scripts) ·
[Architecture](#architecture) · [Configuration](#configuration) · [Knowledge base (RAG)](#knowledge-base-rag) ·
[API](#api) · [Adding a provider](#adding-a-new-provider) · [Documentation](#documentation) ·
[Troubleshooting](#troubleshooting)

---

## Features

**Chat**
- ChatGPT-style UI: conversation sidebar (search, rename, delete), dark/light/system theme, mobile layout
- Token streaming over **Server-Sent Events**; *Stop* cancels generation upstream (`AbortController` + cancel API)
- Markdown with GitHub tables, syntax-highlighted code blocks, copy-code and copy-answer buttons
- Regenerate (optionally with another provider), automatic LLM-generated conversation titles
- Collapsible *reasoning* of thinking models (e.g. Qwen3) shown separately from the answer
- Every assistant message stores the **provider and model** that produced it, plus latency, tokens and cost
- Server-side **system prompt profiles** (General Assistant, Software Architect, Java Expert, Interview Assistant,
  Code Reviewer) — editable by administrators

- **Audio chats:** capture microphone or shared speaker/tab audio, review its transcript or send it automatically,
  and answer with any configured chat model. Saved audio chats have their own source, recording duration, maximum
  output tokens and knowledge-base selection. See [Audio chats](#audio-chats).

**Providers**
- Provider/model selector built **entirely from the backend** (`/api/providers`); offline providers are shown as
  `Qwen Local — Offline` and cannot be selected
- Periodic, cheap health checks (`GET /v1/models`, never inference) plus passive health from real traffic
- Friendly errors with one-click alternatives: *“Qwen Local is currently unavailable. Switch to: [OpenAI] [Llama Local]”*
- **Compare mode**: one prompt, up to 4 providers side by side with latency, input/output tokens and cost (`Local` for
  local models)

**Knowledge base (RAG)** — [details](#knowledge-base-rag)
- Upload PDF, DOCX, HTML, Markdown, text, CSV and JSON, or write knowledge as text; organise it in collections
- Asynchronous indexing (extract → chunk → embed → pgvector) with visible progress, duplicate detection (SHA-256),
  versions, enable/disable without deleting, reindex, delete without orphaned vectors
- Admin panel to browse everything that is stored: extracted text, every chunk, metadata, indexing details, statistics
- RAG test console: test retrieval without an LLM, or generate a test answer and inspect the exact prompt
- *Knowledge* toggle per conversation (all or selected collections) with **citations built from the retrieved chunks**
- Embeddings independent of the chat provider (OpenAI, any OpenAI-compatible server such as Ollama, or offline
  hashing); vector store, reranker and extractors are replaceable adapters

**Platform**
- Registration, login, JWT, BCrypt, per-user data isolation, account lockout, CORS, rate limiting
- Admin UI (`/admin`): users (add, disable, reset status, roles, delete), providers (health, base URL, masked key,
  models; runtime tuning of enabled / default model / timeout / temperature / max tokens), usage dashboard with charts,
  system prompts
- Conversation context management: `FULL_CONTEXT`, `LAST_N_MESSAGES`, `TOKEN_WINDOW`, `SUMMARY_PLUS_RECENT` (default)
- Resilience4j timeout, retry (only when safe) and circuit breaker per provider
- Actuator, structured JSON logs, correlation ids, Prometheus metrics (`llm_requests_total`, `llm_request_duration`,
  `llm_input_tokens`, `llm_output_tokens`, `llm_errors_total` with `provider`/`model`/`status` labels)

---

## Audio chats

Choose **New audio chat** in the sidebar. In **Audio settings**, choose microphone or speaker/browser tab, optionally
select a microphone device, set the capture limit (5–120 seconds), and set maximum output tokens (empty uses the
answering model's default). Save settings before capturing. The model selector at the top chooses the answering
model; **Knowledge** enables the existing RAG pipeline and selects its collections. Retrieval mode, embedding model,
reranking and context budgets use the existing server-side RAG configuration.

**Start capture** requests browser permission. **Stop and transcribe** finishes one bounded recording; it also
finishes at the capture limit or when sharing ends. Review/edit the transcript in the message box, then send it as a
normal prompt. Existing draft text is preserved. Optionally enable **Send transcript automatically after capture**;
each capture then submits one prompt. Start capture again for the next turn. Transcription and answer generation
are separate operations; this is not continuous background listening. Audio chats are saved with an **Audio** badge
after their first prompt, and their settings survive reopening and regeneration.

The browser captures a separate input stream. It does not change the system output device, mute playback, or route
the recording back to the speakers. Speaker capture uses the browser's screen-sharing picker with local playback
suppression disabled: select **Share audio**. Chrome/Edge on desktop support tab audio; full system audio depends
on the OS and browser. A source without an audio track is rejected with instructions. HTTPS or localhost is
required. Display sharing may include a video track for permission purposes, but only audio is recorded/uploaded.
Leaving the chat or pressing **Cancel capture** releases its tracks and cancels any client upload. Cancelling an
upload cannot undo transcription already performed by the upstream service.

Configure speech-to-text independently of the chat models in `.env`, then redeploy. It is disabled by default:

```dotenv
AUDIO_TRANSCRIPTION_ENABLED=true
AUDIO_TRANSCRIPTION_BASE_URL=https://api.openai.com/v1
AUDIO_TRANSCRIPTION_MODEL=whisper-1
# Set AUDIO_TRANSCRIPTION_API_KEY or use the OPENAI_API_KEY fallback.
```

A local server implementing the OpenAI-compatible `POST /audio/transcriptions` multipart API can be used instead:
set its base URL (including `/v1`, for example `http://host.docker.internal:8003/v1` in Compose), model name, and
optional key. A chat-only server does not necessarily implement transcription. No transcription server is started
automatically. In dev mode, use `localhost` for a host-local service; omit `AUDIO_TRANSCRIPTION_API_KEY` to inherit
`OPENAI_API_KEY`, or set it explicitly. Compose uses that fallback when the audio key is empty too.

`AUDIO_TRANSCRIPTION_TIMEOUT` defaults to `PT60S`; `AUDIO_MAX_UPLOAD_BYTES` to `10000000` (at most `25000000`);
`AUDIO_RATE_LIMIT` to 10 transcription requests/minute/user. Keep the shared servlet and nginx multipart limits
at least as large as the audio limit. The duration control bounds browser captures; the backend enforces byte limits.
The authenticated backend holds all credentials, bounds uploads, limits concurrent transcription to one per user,
and never retries a transcription POST. Raw audio is not saved in conversations or the knowledge base; only a
submitted transcript is saved as message text. The configured service receives the captured audio.

API additions: `GET /api/audio/config`, multipart `POST /api/audio/transcriptions` (`file`), and
`PUT /api/conversations/{id}/audio`. New chat requests accept `type: "AUDIO"` and
`audio: {source: "MICROPHONE" | "SPEAKER", maxOutputTokens: null | number, captureSeconds: number, autoSend: boolean}`.
Existing conversations default to `TEXT`; their type cannot be changed. Flyway migration V4 adds the type and saved
audio settings without rewriting existing data. No audio bytes or new event types enter the existing SSE protocol.

## Quick start (Docker Compose)

```bash
scripts/deploy.sh          # creates .env on first run, builds, starts and waits until everything is healthy
scripts/status.sh          # health of containers, endpoints, database and LLM providers
```

On the first run `deploy.sh` copies `.env.example` to `.env` with a generated `JWT_SECRET`, `POSTGRES_PASSWORD` and
`ADMIN_PASSWORD`. If Ollama runs on this machine, Qwen and Llama are pointed at it automatically. Add
`OPENAI_API_KEY` to `.env` to enable OpenAI, then run `scripts/deploy.sh up --no-build`. The scripts detect Docker or
Podman and the available compose command by themselves.

Manual alternative:

```bash
cp .env.example .env
# edit .env: set JWT_SECRET, ADMIN_PASSWORD and (optionally) OPENAI_API_KEY and the Qwen/Llama endpoints
docker compose up -d --build
```

Open **http://localhost:3080** and sign in with `ADMIN_USERNAME` / `ADMIN_PASSWORD` (or register — the first
registered user becomes administrator when no administrator exists).

| Service    | URL                                   | Notes                                      |
|------------|---------------------------------------|--------------------------------------------|
| Frontend   | http://localhost:3080                 | nginx serving the SPA, proxies `/api`      |
| Backend    | http://localhost:8088                 | REST + SSE API, Swagger UI at `/swagger-ui.html` |
| Metrics    | http://localhost:8088/actuator/prometheus | Prometheus format                      |
| PostgreSQL | localhost:5434                        | user/db `aichat`                           |

The application starts and works **without** any local model server — unavailable providers are simply shown as
offline. Add local models any time (see below); the UI picks them up within 30 seconds, no restart required.

> **Podman users:** the scripts find the Podman socket automatically. For plain `docker compose`, point
> `DOCKER_HOST` at the Podman socket (`podman machine inspect --format '{{.ConnectionInfo.PodmanSocket.Path}}'`).
> The standalone `docker-compose` binary or `podman compose` behave the same way.

### Local models

**Option A — Ollama already running on your machine** (CPU/Apple Silicon/GPU):

```bash
ollama pull qwen3:8b && ollama pull llama3.1:8b
```

```dotenv
# .env
QWEN_BASE_URL=http://host.docker.internal:11434/v1
QWEN_MODEL=qwen3:8b
QWEN_ENGINE=ollama
QWEN_MODELS=qwen3:8b
LLAMA_BASE_URL=http://host.docker.internal:11434/v1
LLAMA_MODEL=llama3.1:8b
LLAMA_ENGINE=ollama
LLAMA_MODELS=llama3.1:8b
```

**Option B — GPU containers** (NVIDIA GPU + NVIDIA Container Toolkit). The compose file contains two profiles:

```bash
docker compose --profile qwen --profile llama up -d
```

| Profile | Server                         | Model (configurable)                                  | Served as | Host port |
|---------|--------------------------------|-------------------------------------------------------|-----------|-----------|
| `qwen`  | vLLM (`vllm/vllm-openai`)      | `QWEN_HF_MODEL=Qwen/Qwen3-8B` (`--reasoning-parser qwen3`) | `qwen`  | 8001      |
| `llama` | llama.cpp (`server-cuda`)      | `LLAMA_GGUF=bartowski/Meta-Llama-3.1-8B-Instruct-GGUF:Q4_K_M` | `llama` | 8002 |

The defaults in `.env.example` (`QWEN_BASE_URL=http://qwen:8000/v1`, `QWEN_MODEL=qwen`, `QWEN_ENGINE=vllm`,
`LLAMA_BASE_URL=http://llama:8000/v1`, `LLAMA_MODEL=llama`, `LLAMA_ENGINE=llama-cpp`) already target these containers.
Gated Hugging Face models need `HF_TOKEN`.

**Option C — Ollama in Docker** (`--profile ollama`) serves both families from one container and pulls the models on
first start. Point both providers at `http://ollama:11434/v1` with `*_ENGINE=ollama`.

Any other OpenAI-compatible server (LM Studio, LocalAI, SGLang, a remote vLLM, …) works the same way: set the base
URL, model and engine.

### Other compose commands

```bash
docker compose ps                          # status
docker compose logs -f backend             # JSON logs (set LOG_FORMAT= for plain text)
docker compose --profile monitoring up -d  # Prometheus on http://localhost:9090
docker compose down                        # stop (keeps data)
docker compose down -v                     # stop and delete the database volume
```

### Scripts

The scripts in `scripts/` wrap the common workflows. They run on macOS's bash 3.2 and Linux, work with Docker or
Podman, and print `--help`. The `Makefile` offers shortcuts (`make help`).

| Script | Purpose | Examples |
|--------|---------|----------|
| `scripts/deploy.sh` | Deploy and operate the Compose stack: `up` (default), `down`, `restart`, `logs`, `ps`. Creates `.env` on the first run, validates configuration and ports, waits for readiness | `scripts/deploy.sh up --profile ollama`<br>`scripts/deploy.sh up --no-build`<br>`scripts/deploy.sh logs backend`<br>`scripts/deploy.sh down` |
| `scripts/run-dev.sh` | Development mode with hot reload: PostgreSQL in a container, backend jar and Vite dev server on the host (`start`, `stop`, `restart`, `status`, `logs`). PIDs and logs in `.run/` | `scripts/run-dev.sh start`<br>`scripts/run-dev.sh logs backend`<br>`scripts/run-dev.sh stop --with-db` |
| `scripts/status.sh` | Status check: containers, dev processes, endpoints, database, LLM providers (online/offline, default model) and direct model-server probes. Exit code 0 when usable | `scripts/status.sh`<br>`scripts/status.sh --json`<br>`scripts/status.sh --watch 5` |
| `scripts/test.sh` | Run the test suites, configuring Testcontainers for Docker or Podman automatically | `scripts/test.sh`<br>`scripts/test.sh backend --test ChatFlowIntegrationTest`<br>`scripts/test.sh frontend` |

Make targets: `up`, `up-fast`, `down`, `restart`, `logs`, `ps`, `status`, `watch`, `dev`, `dev-stop`, `dev-logs`,
`test`, `test-backend`, `test-frontend` (for example `make up PROFILES="ollama monitoring"`).

---

## Architecture

### Overview

```text
                                   ┌──────────────────────── OpenAI API (Responses API)
                                   │
 React SPA ── nginx ── Spring Boot ┼──────────────────────── Qwen  (vLLM / Ollama / llama.cpp …)
   (SSE)       (/api)   (REST+SSE) │
                                   └──────────────────────── Llama (llama.cpp / Ollama / vLLM …)
                           │
                       PostgreSQL
```

### Hexagonal (ports & adapters) backend

```text
                 ┌──────────────────────── api (REST, SSE, problem+json) ───────────────────────┐
                 │                                                                              │
                 ▼                                                                              │
   ┌─────────────────────────── application (use cases, no vendor knowledge) ──────────────────┐ │
   │ ChatService · CompareService · ConversationContextManager · TitleService ·                 │ │
   │ ConversationSummarizer · LlmProviderRegistry · ProviderHealthMonitor · UsageRecorder ·      │ │
   │ AuthService · AdminUserService · SystemPromptService                                        │ │
   └───────────────┬─────────────────────────────────────────────────────────────┬─────────────┘ │
                   │ ports (domain)                                               │               │
                   ▼                                                              ▼               │
   ┌──────── domain ────────┐                                    ┌──── infrastructure ─────────────┐
   │ LlmProvider (port)     │◄──────── implements ───────────────│ llm/openai   OpenAiLlmProvider    │
   │ LlmRequest/LlmResponse │                                    │ llm/compatible OpenAiCompatible…   │
   │ LlmMessage/LlmChunk    │                                    │ llm/qwen     QwenLlmProvider      │
   │ Conversation, Message, │                                    │ llm/llama    LlamaLlmProvider     │
   │ LlmExecution, User …   │◄──────── implements ───────────────│ persistence  JPA adapters          │
   │ *Repository (ports)    │                                    │ security     JWT, BCrypt, limits   │
   └────────────────────────┘                                    └────────────────────────────────────┘
```

```text
                      ┌─ ObservedLlmProvider ─ ResilientLlmProvider ─ OpenAiLlmProvider      (type: openai)
ChatService ─► LlmProvider (port) ─┼─ ObservedLlmProvider ─ ResilientLlmProvider ─ QwenLlmProvider       (openai-compatible / qwen)
                      └─ ObservedLlmProvider ─ ResilientLlmProvider ─ LlamaLlmProvider      (openai-compatible / llama)
```

- `domain` is plain Java: provider-neutral LLM types (`LlmRequest`, `LlmResponse`, `LlmMessage`, `LlmChunk`,
  `LlmModel`, `ProviderHealth`), the `LlmProvider` port, provider-neutral exceptions and the repository ports.
- `application` implements the use cases against those ports only. It never knows whether it talks to OpenAI, Qwen,
  Llama or anything else; providers are looked up by id in the `LlmProviderRegistry` (strategy pattern, no
  `if (provider.equals("openai"))`).
- `infrastructure` contains the adapters. Vendor payloads, endpoints, auth and error formats never leave it.
- `api` exposes REST/SSE endpoints and maps errors to RFC 9457 problem details.

```text
backend/src/main/java/com/example/aichat
├── api                 REST controllers, DTOs, SSE support, exception handler
├── application         use cases: chat (+context), compare, conversation, provider, usage, auth, user, prompt
├── domain              llm (port + neutral model + exceptions), conversation, usage, user, prompt, provider
├── infrastructure
│   ├── llm             registry wiring, factories SPI, resilience + metrics decorators, error translation, pricing
│   │   ├── openai      OpenAI Responses API adapter
│   │   ├── compatible  reusable OpenAI-compatible (Chat Completions) adapter, <think> parser, engines
│   │   ├── qwen        QwenLlmProvider (profile + reasoning control)
│   │   └── llama       LlamaLlmProvider (profile)
│   ├── persistence     JPA entities, Spring Data repositories, port adapters, SQL analytics
│   └── security        Spring Security, JWT issue/verify, BCrypt, rate limiting, problem+json handlers
├── configuration       clock/scheduling, CORS, OpenAPI, gauges
└── common              correlation id filter, small utilities
```

### The LLM port

```java
public interface LlmProvider {
    ProviderDescriptor descriptor();          // id, display name, kind (CLOUD/LOCAL), capabilities, defaults
    default String providerId() { return descriptor().id(); }
    Mono<LlmResponse> chat(LlmRequest request);
    Flux<LlmChunk> stream(LlmRequest request); // TextDelta / ReasoningDelta …, then exactly one Completed
    List<LlmModel> availableModels();
    ProviderHealth health();                  // cheap probe, never inference
}
```

Improvements over a minimal interface:

- `chat` returns `Mono` like `stream` returns `Flux`, so timeouts, retries and circuit breakers compose the same way.
- `LlmChunk` is a **sealed** hierarchy (`TextDelta`, `ReasoningDelta`, `Completed`) — future tool-call or citation
  chunks are new record types, handled exhaustively by the compiler.
- `ProviderDescriptor` carries `kind` (cost reporting, local-vs-cloud analytics), `capabilities` (enable features per
  provider) and configured defaults.
- `GenerationOptions.reasoning` lets the application ask for fast, non-thinking answers (titles, summaries); each
  adapter maps it to vendor-specific switches.

### Provider resolution

`llm.providers.<id>` entries are instantiated at startup by **factories** selected by `type` (and optional `family`):

| type                | family  | factory                            | adapter                         |
|---------------------|---------|------------------------------------|---------------------------------|
| `openai`            | –       | `OpenAiProviderFactory`            | `OpenAiLlmProvider`             |
| `openai-compatible` | `qwen`  | `QwenProviderFactory`              | `QwenLlmProvider`               |
| `openai-compatible` | `llama` | `LlamaProviderFactory`             | `LlamaLlmProvider`              |
| `openai-compatible` | other/– | `OpenAiCompatibleProviderFactory`  | `OpenAiCompatibleLlmProvider`   |

Each adapter is wrapped by `ResilientLlmProvider` (Resilience4j) and `ObservedLlmProvider` (Micrometer) and registered
in the `LlmProviderRegistry`. A request's `provider` + `model` are resolved there; blank values fall back to the default
provider and its (possibly admin-overridden) default model.

`QwenLlmProvider` and `LlamaLlmProvider` contain almost no code: the protocol lives in `OpenAiCompatibleLlmProvider`,
family differences are data (`ModelFamilyProfile`: display name, capabilities, `<think>` parsing, how to switch off
reasoning per engine). The application does **not** assume Ollama — `engine` (`generic`, `ollama`, `vllm`,
`llama-cpp`, `lm-studio`, `localai`, `sglang`) only selects defaults for engine-specific parameters.

### Streaming and lifecycle of an answer

1. `POST /api/chat/stream` validates the request, resolves provider/model, stores the user message and an assistant
   placeholder (`STREAMING`) — errors before this point are returned as `application/problem+json`.
2. `ConversationContextManager` builds the provider-neutral context (system prompt + summary + recent messages).
3. The provider stream is relayed as SSE (`start`, `reasoning`, `delta`, `done`, `error`, `title`) with keep-alive
   comments; nginx/Vite proxies are configured not to buffer.
4. The assistant message is finalised exactly once: `COMPLETE`, `CANCELLED` (stop button or closed connection — the
   partial answer is kept) or `ERROR` (with an understandable message and alternative providers).
5. Usage (`llm_executions`), metrics, passive health, background summarisation and title generation follow.

### Context management

| Strategy              | Behaviour                                                                          |
|-----------------------|------------------------------------------------------------------------------------|
| `FULL_CONTEXT`        | every eligible message (short conversations / debugging)                           |
| `LAST_N_MESSAGES`     | the last N messages, still capped by the token budget                              |
| `TOKEN_WINDOW`        | as many recent messages as fit into `max-context-tokens`                           |
| `SUMMARY_PLUS_RECENT` | **default** — rolling summary of older messages + recent messages that fit         |

The system prompt is always kept separate and merged with the summary into one leading system message. Failed and
in-flight answers are never sent; roles are normalised to strictly alternate. Summaries are produced in the background
after an answer completes (never delaying a response) once the unsummarised history exceeds
`summary-trigger-tokens`; the newest `summary-keep-recent-messages` always stay verbatim.

### Resilience

Per provider (configurable under `llm.providers.<id>.retry|circuit-breaker|timeout`):

- **Timeout** — overall for single-shot calls; for streams the limit applies to *each* chunk wait
  (time-to-first-token and idle gaps), so long answers are not cut off.
- **Retry** — only when the provider certainly did not process (and bill) the request: connection refused/DNS/connect
  timeout, HTTP 502/503 and rate limits with a short `Retry-After`. Never after a stream emitted data, never on
  timeouts or 500s.
- **Circuit breaker** — opens on provider failures (unreachable, timeouts, 5xx) and then fails fast with
  `LlmProviderUnavailableException`; client errors (bad request, unknown model) do not count.

Vendor failures are mapped to `LlmProviderUnavailableException`, `LlmAuthenticationException`,
`LlmRateLimitException`, `LlmTimeoutException` and `LlmResponseException` (with codes such as `MODEL_NOT_FOUND`,
`INVALID_REQUEST`, `CONTENT_FILTERED`).

### Data model (PostgreSQL, Flyway)

| Table               | Purpose                                                                                     |
|---------------------|---------------------------------------------------------------------------------------------|
| `users`             | accounts, role, status (`ACTIVE`/`DISABLED`/`LOCKED`), failed logins                        |
| `conversations`     | owner, title (+source), system prompt, last provider/model, rolling summary                 |
| `messages`          | role, content, reasoning, **provider, model**, status, error                                |
| `llm_executions`    | one row per LLM call: provider, model, kind, purpose, tokens, latency, TTFT, cost, status   |
| `system_prompts`    | prompt profiles                                                                              |
| `provider_settings` | administrator overrides (enabled, default model, timeout, temperature, max tokens)          |
| `knowledge_*`       | knowledge base: collections, sources, original files, extracted text, chunks, pgvector vectors — see [RAG data model](#rag-data-model) |
| `rag_executions`, `rag_retrieved_chunks` | every retrieval (query, model, timings, context size) and the chunks it returned, with ranks, scores and citation numbers |

---

## Configuration

All settings live in [`backend/src/main/resources/application.yml`](backend/src/main/resources/application.yml) and can
be overridden with environment variables (explicit placeholders such as `QWEN_BASE_URL`, or Spring relaxed binding,
e.g. `LLM_PROVIDERS_QWEN_TIMEOUT=PT300S`). Nothing provider-specific is hard-coded.

```yaml
llm:
  default-provider: openai
  providers:
    openai:
      enabled: true
      type: openai
      api-key: ${OPENAI_API_KEY}
      base-url: https://api.openai.com
      default-model: ${OPENAI_MODEL:gpt-5.6-sol}
    qwen:
      enabled: true
      type: openai-compatible
      family: qwen
      engine: ${QWEN_ENGINE:generic}
      base-url: ${QWEN_BASE_URL:http://localhost:8001/v1}
      api-key: ${QWEN_API_KEY:local}
      default-model: ${QWEN_MODEL:qwen}
    llama:
      enabled: true
      type: openai-compatible
      family: llama
      base-url: ${LLAMA_BASE_URL:http://localhost:8002/v1}
      api-key: ${LLAMA_API_KEY:local}
      default-model: ${LLAMA_MODEL:llama}
```

### Environment variables

| Variable | Default | Description |
|----------|---------|-------------|
| `OPENAI_API_KEY` | – | OpenAI key (server side only, never sent to the browser) |
| `OPENAI_MODEL` | `gpt-5.6-sol` | default OpenAI model |
| `OPENAI_MODELS` | – | comma-separated allow-list for the model selector (empty = discover via `/v1/models`) |
| `OPENAI_BASE_URL` | `https://api.openai.com` | e.g. a proxy; `/v1` is optional |
| `OPENAI_REASONING_EFFORT` | – | reasoning effort for reasoning models (`low`/`medium`/`high`) |
| `OPENAI_PRICE_INPUT_PER_MILLION` / `…_OUTPUT_…` | `1.25` / `10.00` | fallback price when no `pricing` entry matches |
| `QWEN_BASE_URL` / `LLAMA_BASE_URL` | `http://localhost:8001/v1` / `…:8002/v1` | OpenAI-compatible base URL (incl. `/v1`) |
| `QWEN_MODEL` / `LLAMA_MODEL` | `qwen` / `llama` | default model id on that server |
| `QWEN_ENGINE` / `LLAMA_ENGINE` | `generic` | `ollama`, `vllm`, `llama-cpp`, `lm-studio`, `localai`, `sglang`, `generic` |
| `QWEN_MODELS` / `LLAMA_MODELS` | – | optional model allow-list |
| `QWEN_API_KEY` / `LLAMA_API_KEY` | `local` | bearer token if the server requires one |
| `QWEN_TIMEOUT` / `LLAMA_TIMEOUT` | `PT180S` | per-chunk / overall timeout (model loading can be slow) |
| `*_TEMPERATURE` / `*_MAX_TOKENS` / `*_ENABLED` | | sampling, token limit, enabled flag per provider |
| `LLM_DEFAULT_PROVIDER` | `openai` | preselected provider |
| `LLM_HEALTH_INTERVAL` | `PT30S` | health check interval |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | `jdbc:postgresql://localhost:5434/aichat`, `aichat`, `aichat` | database |
| `JWT_SECRET` | random per start | HMAC secret, ≥ 32 bytes (`openssl rand -base64 48`) |
| `JWT_TTL` | `PT12H` | access token lifetime |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` / `ADMIN_EMAIL` | – | administrator created on first start |
| `REGISTRATION_ENABLED` / `FIRST_USER_ADMIN` | `true` / `true` | self-service registration policy |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://localhost:3080` | browser origins |
| `CHAT_CONTEXT_STRATEGY` | `SUMMARY_PLUS_RECENT` | context strategy |
| `CHAT_CONTEXT_MAX_TOKENS` | `8000` | history token budget |
| `CHAT_TITLE_MODE` / `CHAT_TITLE_PROVIDER` | `LLM` / – | title generation (or `HEURISTIC`) and optional dedicated provider |
| `CHAT_RATE_LIMIT` / `COMPARE_RATE_LIMIT` | `30` / `10` | requests per minute and user |
| `SERVER_PORT` | `8088` | backend port (8080 inside Docker) |
| `LOG_FORMAT` (compose) | `ecs` | JSON log format (`ecs`, `logstash`; empty = plain text) |
| `RAG_*` | | knowledge base: embeddings, chunking, retrieval, limits — see [Embedding configuration](#embedding-configuration) and [RAG configuration](#rag-configuration) |
| `POSTGRES_IMAGE` (compose) | `pgvector/pgvector:pg17-trixie` | PostgreSQL image; must provide the pgvector extension |
| `NGINX_MAX_UPLOAD_SIZE` (compose) | `55m` | request body limit of the bundled nginx for document uploads |

Docker Compose host ports: `FRONTEND_PORT` (3080), `BACKEND_PORT` (8088), `POSTGRES_PORT` (5434).

### OpenAI

Set `OPENAI_API_KEY`. The adapter uses the **Responses API** (`POST /v1/responses`): system messages become
`instructions`, the conversation becomes `input`, streaming uses typed events (`response.output_text.delta`, …),
responses are not stored by OpenAI (`store: false`). Reasoning models (prefixes `o1, o3, o4, gpt-5`, configurable via
`OPENAI_REASONING_MODEL_PREFIXES`) do not receive `temperature`; titles and summaries request
`reasoning.effort=OPENAI_REASONING_DISABLED_EFFORT` (default `low`). Cost estimates use the `pricing` table in
`application.yml` (longest model-prefix match) — verify it against current OpenAI pricing.

### Runtime changes without restart

Administrators can change *enabled*, *default model*, *timeout*, *temperature* and *max tokens* per provider in
**Admin → Providers**; overrides are stored in `provider_settings` and apply to the next request. Endpoints and
credentials stay in configuration (environment) and are only ever shown masked.

---

## Knowledge base (RAG)

Administrators curate a knowledge base — uploaded documents and manually written text, organised in collections — and
chat answers can use it, with **citations created from the chunks that were actually retrieved**. The subsystem is a
separate module (`com.example.aichat.rag`) built from ports and adapters: chat logic does not know how knowledge is
stored, embeddings are configured independently of the chat providers (Qwen can answer with knowledge embedded by
OpenAI, and vice versa), and pgvector is just one implementation of the `VectorStore` port.

```text
                    Admin
                      │
          ┌───────────┴───────────┐
          │                       │
       Upload                  Add Text
          │                       │
          └───────────┬───────────┘
                      ↓
                 Extraction
                      ↓
                  Chunking
                      ↓
                 Embeddings
                      ↓
                  pgvector
                      │
                      │
User Question         │
      │               │
      ↓               │
  RAG Retriever ──────┘
      ↓
   Reranker
      ↓
Context Builder
      ↓
 Model Router
      ↓
┌─────┼───────────┐
│     │           │
OpenAI Qwen      Llama
│     │           │
└─────┼───────────┘
      ↓
Answer + Sources
```

### RAG architecture

Ingestion and question answering are separate flows that only share the stored chunks and vectors:

| Flow | Path |
|------|------|
| Ingestion (background) | `DocumentIngestionService` / `TextIngestionService` → `KnowledgeSourceWriter` (checksum, duplicates, storage) → `IngestionQueue` → `IngestionPipeline`: `DocumentExtractor` → `ChunkingStrategy` → `EmbeddingProvider` → `VectorStore` |
| Question answering | `ChatService` → `KnowledgeAugmenter` (port owned by chat) → `RagChatAugmenter` → `RetrievalService` (`EmbeddingProvider`, `VectorStore`, `KeywordSearcher`) → `RerankingService` → `RagContextBuilder` → `LlmProvider` |

| Port | Responsibility | Implementations |
|------|----------------|-----------------|
| `DocumentExtractor` | bytes → text with pages and sections | PDF, DOCX, HTML, Markdown, text, CSV and JSON extractors (Apache Tika 4 where it helps) |
| `ChunkingStrategy` | text → chunks with offsets, page and section | `RecursiveTextChunkingStrategy` |
| `EmbeddingProvider` | texts → vectors | `OpenAiCompatibleEmbeddingProvider` (OpenAI, Ollama, vLLM, LM Studio, LocalAI, llama.cpp), `HashingEmbeddingProvider` (offline); tests use `FakeEmbeddingProvider` |
| `VectorStore` | store, search and delete vectors | `PgVectorStore` |
| `KeywordSearcher` | full-text candidates for hybrid retrieval | `PostgresKeywordSearcher` |
| `RerankingService` | reorder candidates | `PassThroughRerankingService` |
| `RagContextBuilder` | chunks → prompt context and citations within a token budget | `DefaultRagContextBuilder` |
| `KnowledgeAccessPolicy` | collections a user may use and cite | `EnabledCollectionsAccessPolicy` (every enabled collection) |
| `KnowledgeAugmenter` (chat port) | knowledge for one chat answer | `RagChatAugmenter` |

Invariants: LLM providers never see how documents are stored, the vector store knows nothing about conversations, and
`ChatService` never embeds text or queries vectors itself. Persistence uses `JdbcTemplate` (pgvector, arrays, JSONB,
batches); retrieval filters are denormalised onto the vector rows so that one indexed query applies them.

<a id="rag-data-model"></a>

| Table | Content |
|-------|---------|
| `knowledge_collections` | name, description, enabled; a default collection ("General") always exists |
| `knowledge_sources` | type (`FILE`, `TEXT`, `URL` reserved), title, file name, MIME type, source URL, collection, status, enabled, tags, SHA-256 checksum, size, version and parent version, creator, timestamps, embedding provider/model, chunk count, error, statistics of the last indexing run |
| `knowledge_source_files` / `knowledge_source_contents` | original bytes / extracted text with page and section structure |
| `knowledge_chunks` | chunk number, text, token count, character range, page, section, metadata, full-text vector |
| `knowledge_vectors` | one embedding per chunk (`vector`), model, dimensions and the filter fields (collection, enabled, tags) |
| `rag_executions`, `rag_retrieved_chunks` | retrieval log for analysis and persisted citations |

Everything below a source is deleted with it (`ON DELETE CASCADE`); citations keep the source title.

### pgvector setup

- Docker Compose runs `pgvector/pgvector:pg17-trixie`: PostgreSQL 17 with pgvector on the same Debian base as
  `postgres:17`, so an existing database volume keeps working without collation changes. Override with
  `POSTGRES_IMAGE`.
- Flyway migration `V3__knowledge_base.sql` runs `CREATE EXTENSION IF NOT EXISTS vector` and creates the tables. On a
  managed database (RDS, Azure Database for PostgreSQL, Cloud SQL, …) enable pgvector there first, or run the
  migration with a role that may create extensions.
- The `embedding` column has no fixed size, so vectors of different models can coexist while reindexing; every
  query is limited to the active model. On first use of a dimension (up to 2000) an HNSW cosine index on
  `embedding::vector(n)` is created, and searches use `hnsw.iterative_scan` so that filters do not starve results.
- Check: `docker compose exec postgres psql -U aichat -c "select extversion from pg_extension where extname='vector'"`.

### Embedding configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `RAG_EMBEDDING_PROVIDER` | `openai` | `openai`, `openai-compatible` (aliases `ollama`, `vllm`, `lm-studio`, `localai`, `llama-cpp`) or `hashing` |
| `RAG_EMBEDDING_MODEL` | `text-embedding-3-small` | embedding model on that server |
| `RAG_EMBEDDING_BASE_URL` | `https://api.openai.com/v1` | OpenAI-compatible base URL (including `/v1`) |
| `RAG_EMBEDDING_API_KEY` | `OPENAI_API_KEY` | bearer token |
| `RAG_EMBEDDING_DIMENSIONS` | `0` | `0` = model default; OpenAI `text-embedding-3-*` can return shorter vectors |
| `RAG_EMBEDDING_QUERY_PREFIX` / `RAG_EMBEDDING_DOCUMENT_PREFIX` | – | instruction prefixes for models that expect them (`\n` = line break) |
| `RAG_EMBEDDING_BATCH_SIZE` / `RAG_EMBEDDING_TIMEOUT` | `32` / `PT60S` | batch size and timeout; transient errors are retried |

```bash
# OpenAI (default; uses OPENAI_API_KEY)
RAG_EMBEDDING_PROVIDER=openai
RAG_EMBEDDING_MODEL=text-embedding-3-small

# Local Qwen3 embeddings with Ollama on the host (first: ollama pull qwen3-embedding:0.6b)
RAG_EMBEDDING_PROVIDER=ollama
RAG_EMBEDDING_BASE_URL=http://host.docker.internal:11434/v1
RAG_EMBEDDING_MODEL=qwen3-embedding:0.6b
RAG_EMBEDDING_QUERY_PREFIX=Instruct: Given a question, retrieve passages that answer it\nQuery:

# Offline, without any model: word hashing (keyword-like matching, no semantics) for demos and air-gapped setups
RAG_EMBEDDING_PROVIDER=hashing
RAG_SIMILARITY_THRESHOLD=0.12
```

Each source records the embedding model that indexed it, and retrieval only compares vectors of the active model.
After changing the model, affected sources are marked *needs reindex* and stay unsearchable until **Reindex all**
(Admin → Knowledge base) has embedded them again.

### Document ingestion

- **Upload** a file (`POST /api/admin/knowledge/files`, multipart) or **add text** (`POST /api/admin/knowledge/text`).
  Manual text is stored like an uploaded Markdown file, so it runs through the same pipeline (headings become
  sections) and can be edited later.
- The request returns at once (`202 {"sourceId": "…", "status": "UPLOADED"}`); a bounded background pool
  (`RAG_INGESTION_CONCURRENCY`) does the work. Status: `UPLOADED → EXTRACTING → EXTRACTED → CHUNKING → EMBEDDING →
  INDEXED`, or `FAILED` with the reason; `DELETING` while being removed. The admin UI polls
  `GET /api/admin/knowledge/{id}/status` while anything is in progress.
- **Duplicates:** the SHA-256 checksum is compared with the stored sources. A duplicate is answered with
  `409 DUPLICATE_DOCUMENT` and the existing source ("This document already exists in the knowledge base"); upload again
  with `duplicateStrategy=NEW_VERSION` (new source with version + 1, previous version disabled) or `REPLACE` (the
  existing source is reindexed in place). No duplicate embeddings are created.
- **Replace file** (`replaceSourceId`) keeps the title, collection, tags and URL unless new values are sent; the old
  chunks and vectors are removed immediately.
- **Validation:** the file name is reduced to its last path segment and stripped of control and reserved characters;
  only listed extensions are accepted and the real content is sniffed (a renamed binary is rejected); size limits
  (`RAG_MAX_FILE_SIZE`, `RAG_MAX_REQUEST_SIZE`, nginx `NGINX_MAX_UPLOAD_SIZE`, `413 PAYLOAD_TOO_LARGE`); a limit on the
  extracted text (`RAG_MAX_EXTRACTED_CHARACTERS`, protects against ZIP and decompression bombs) and an extraction
  timeout (`RAG_EXTRACTION_TIMEOUT`). HTML is reduced to visible text and extracted text is never rendered as HTML.
  Source URLs must be `http(s)`.
- **Recovery:** after a restart, interrupted indexing runs are queued again and interrupted deletions are completed.

### Supported file types

| Type | Extensions | Extraction |
|------|------------|------------|
| PDF | `.pdf` | text per page; page numbers are kept for citations. Encrypted PDFs fail with a clear reason |
| Word | `.docx` | paragraphs and tables; headings become sections |
| HTML | `.html`, `.htm`, `.xhtml` | visible text only (scripts and styles are dropped); headings become sections |
| Markdown | `.md`, `.markdown`, `.mdown` | `#` headings become sections |
| Text | `.txt`, `.text`, `.log` | as is (encoding detected) |
| CSV | `.csv` | one line per row as `column: value` pairs (delimiter detected) |
| JSON | `.json` | flattened `path: value` lines; top-level keys become sections |

OCR is not performed: scanned PDFs without a text layer fail with "No text could be extracted". A new format needs a
`DocumentExtractor` bean (`supports(contentType, filename)`, `extract(...)`) and an entry in `DocumentType` (extensions
and accepted content types).

### Chunking

`RecursiveTextChunkingStrategy` splits at the largest natural boundary that keeps a chunk within the size — paragraphs,
then lines, sentences, clauses and words — and starts each chunk with an overlap from the previous one. Sizes are
estimated tokens (about 3.5 characters per token):

```yaml
rag:
  chunking:
    strategy: recursive   # RAG_CHUNKING_STRATEGY
    chunk-size: 800       # RAG_CHUNK_SIZE
    overlap: 150          # RAG_CHUNK_OVERLAP
```

Each chunk stores its number, character start and end, page (and last page when it spans several), section title and
token count, which is what citations and the chunk viewer show. New settings apply to sources indexed afterwards;
reindex existing sources to re-chunk them.

### Retrieval

```text
question ─► embed (query prefix) ─► vector search (cosine, filters) ───────┐
          └──────────────────────► keyword search (HYBRID mode only) ──────┴─► merge (RRF) ─► threshold ─► rerank ─► top K
```

- Only **enabled, indexed** sources in **enabled** collections that the user may read are searched; requests can be
  narrowed by collections, tags and source ids.
- `RAG_SIMILARITY_THRESHOLD` (default `0.70`) drops weak vector matches — no context is better than wrong context. The
  right value depends on the embedding model. With the offline hashing embeddings, relevant chunks measured 0.15–0.45
  and unrelated ones stayed below 0.11 (hence `0.12`); semantic models have their own ranges. Run the **test
  console** with threshold 0 to see the scores of relevant and unrelated chunks and pick a value in between.
- `RAG_RETRIEVAL_MODE=HYBRID` adds PostgreSQL full-text search (`simple` configuration, language-neutral) and merges
  both result lists with reciprocal rank fusion — useful for exact terms, codes and names.
- `RAG_TOP_K` (8) results are returned from a larger candidate pool, so a reranker has room to reorder.
- Every retrieval is recorded in `rag_executions` / `rag_retrieved_chunks`: query, embedding provider and model,
  collections, retrieved chunk ids, ranks, similarity and rerank scores, embedding and search durations, context size.

### RAG chat

The **Knowledge** button in the composer enables the knowledge base for a conversation, and the collection picker
limits it to selected collections (none selected = all available). The setting is stored with the conversation
(`PUT /api/conversations/{id}/knowledge`) and reused by *Regenerate*; chat without knowledge is unchanged. For each
question:

1. The retriever finds the relevant chunks.
2. `RagContextBuilder` numbers them `[1]`, `[2]`, … and keeps them within `RAG_CONTEXT_MAX_TOKENS` (6000),
   `rag.context.max-chunks` (8) and the room left in the model's context window; chunks that do not fit are left out
   rather than cut (only a single oversized top chunk is shortened).
3. The system message gets fixed rules: use and cite the excerpts, do not invent facts, and treat the excerpts as
   **untrusted data** — instructions found in documents never override system or application rules and are never a
   reason to call tools. Delimiter look-alikes inside documents are neutralised.
4. The excerpts go into the current user turn only (`<knowledge>…</knowledge>` followed by `Question: …`). They are
   not stored in the conversation, so history and summaries stay small.
5. The selected chat provider answers — OpenAI, Qwen, Llama or any other.

If nothing matches, or the knowledge base cannot be searched (for example the embedding server is down), the model
answers without knowledge and the UI shows a notice below the answer instead of an error.

### Citations

Citations are created by the server from the chunks that were sent to the model; the model never has to invent
identifiers. They are streamed as an SSE `citations` event before the answer, returned by the non-streaming API and
stored with the message:

```json
{
  "content": "Prices are converted with daily exchange rates [1].",
  "citations": [
    { "index": 1, "sourceId": "…", "chunkId": "…", "title": "Pricing Architecture.pdf", "pageNumber": 14,
      "sectionTitle": "Currency conversion", "collectionName": "Engineering", "score": 0.91 }
  ],
  "knowledgeNotice": null
}
```

The chat shows **Sources** below the answer (`[1] Pricing Architecture.pdf — Page 14 · Currency conversion`).
Clicking a source opens the cited excerpt (`GET /api/knowledge/chunks/{chunkId}`) if the user may read it, and
administrators get a link to the source in the admin panel. Citations survive the deletion of a source (the title
remains, the excerpt is no longer available).

### Admin knowledge panel

- **`/admin/knowledge`** — statistics (sources, documents, text entries, chunks, indexed, processing, failed,
  disabled, embedding model, retrievals and latency), filters (title or file name, collection, type, status, enabled,
  tag, creation date), sorting (name, created, updated, chunk count, file size) and pagination. Per source: view,
  edit, enable/disable, reindex or retry, delete. Buttons for *Add document*, *Add text*, *Collections* (create,
  rename, enable/disable, delete; sources, chunks and storage per collection) and *Test retrieval*.
- **`/admin/knowledge/{id}`** — tabs *Overview* (all metadata), *Extracted Text* (with character count and estimated
  tokens), *Chunks* (list `# · Section · Page · Tokens` and a viewer with *Previous*/*Next*), *Metadata* (checksum,
  versions, document properties, outline) and *Indexing* (status, embedding provider and model, chunks vs. stored
  embeddings, timings of the last run, last error). Actions: edit, replace file, enable/disable, reindex/retry,
  re-extract, delete.
- **`/admin/knowledge/test`** — the RAG test console. *Search* runs retrieval only (no LLM) with collections, top K,
  threshold, mode and tags, and lists rank, source, page, section, similarity / keyword / rerank scores and the chunk
  text. *Generate answer* runs the whole pipeline with a chosen provider and model and shows the answer, its
  citations, latency, tokens, cost, context size and the exact prompt.

### Reindexing

**Reindex** (per source; *Retry* for failed ones; **Reindex all** for sources embedded with another model) chunks the
stored text again, embeds it and replaces chunks and vectors in one transaction. The old vectors stay searchable until
the new ones are written, and duplicates cannot occur. **Re-extract** reads the original file again first. Editing a
text entry or replacing a file removes the old vectors at once and reindexes. Reindex after changing the chunking
settings or the embedding model.

### Deleting knowledge

Deleting asks for confirmation (*"This will permanently remove: 84 chunks, 84 embeddings, …"*). The source is then
marked `DELETING` (running indexing jobs discard their results) and its record, original file, extracted text, chunks
and vectors are removed in one transaction, so no orphaned vectors remain. Deleting a collection removes its sources
the same way; the default collection cannot be deleted. **Disable** keeps everything but removes a source or a whole
collection from retrieval immediately.

### RAG configuration

```yaml
rag:
  enabled: true                       # RAG_ENABLED
  chunking:
    strategy: recursive
    chunk-size: 800
    overlap: 150
  retrieval:
    mode: VECTOR                      # or HYBRID
    top-k: 8
    similarity-threshold: 0.70
  context:
    max-tokens: 6000
    max-chunks: 8
  embedding:
    provider: openai
    model: text-embedding-3-small
  ingestion:
    max-file-size: 50MB
    max-extracted-characters: 5000000
    max-text-entry-characters: 200000
    concurrency: 2
    extraction-timeout: PT5M
  reranking:
    enabled: false
  vector:
    store: pgvector
    create-index: true
    ef-search: 100
```

Every value can be overridden with an environment variable (`RAG_*`, see [`.env.example`](.env.example) and
`application.yml`).

### RAG security and observability

- Only administrators can upload, add text, edit metadata, delete, reindex, manage collections, use the test console
  and see ingestion errors (`/api/admin/knowledge/**`). Users can use the knowledge base in chat and open cited
  excerpts of the collections they may read.
- Collection-level permissions: by default every user may read every enabled collection. Registering a
  `KnowledgeAccessPolicy` bean (for example by role or group) restricts chat retrieval, the collection picker and
  citation excerpts without other changes.
- Metrics: `rag_ingestion_total`, `rag_ingestion_failed_total`, `rag_ingestion_duration_seconds`, `rag_documents` and
  `rag_chunks` (gauges; OpenMetrics reserves the `_total` suffix for counters), `rag_embedding_requests_total`,
  `rag_embedding_duration_seconds`, `rag_retrieval_requests_total`, `rag_retrieval_duration_seconds`,
  `rag_retrieved_chunks` — labelled `embedding_provider`, `embedding_model`, `collection` and `status`.
- Logs contain ids, counts and durations — never document text.

### RAG extension points

| Feature | Where it plugs in |
|---------|-------------------|
| Web pages, Google Drive, SharePoint, Confluence, GitHub, Notion, S3, OneDrive, scheduled synchronisation | a connector that produces `NewContent` for `KnowledgeSourceWriter` (source type `URL` is reserved; checksums detect changes) |
| OCR, image understanding | another `DocumentExtractor`, ordered before the PDF extractor |
| Qdrant, Weaviate, Pinecone, Milvus, Elasticsearch / OpenSearch | implement `VectorStore` (and `KeywordSearcher` for hybrid search) |
| Cross-encoder, Cohere or LLM reranking | implement `RerankingService`, select it with `rag.reranking.provider` |
| Query rewriting, multi-query retrieval, HyDE | in `RagContextService`, before `RetrievalService` |
| Parent-child retrieval, richer metadata filters | `ChunkingStrategy` (parent ids in chunk metadata) and `VectorFilter` |
| Knowledge graph / GraphRAG | an additional retriever whose results are fused like the keyword results |
| Document versioning | `version`, `parentSourceId` and checksums already exist; `NEW_VERSION` creates linked versions |

### RAG troubleshooting

| Symptom | Fix |
|---------|-----|
| Source *Failed* with an embedding error (`401`, unreachable, model not found) | Check `RAG_EMBEDDING_*` (key, base URL, model pulled on the server), then **Retry**. |
| Upload rejected with `413` | Raise `RAG_MAX_FILE_SIZE`, `RAG_MAX_REQUEST_SIZE` and `NGINX_MAX_UPLOAD_SIZE` together. |
| *Unsupported encryption* | The PDF is password protected; remove the protection and upload again. |
| *No text could be extracted* | Scanned document without a text layer; OCR is not supported yet. |
| The test console finds nothing | Lower the threshold (0 shows all scores), check that collections and sources are enabled and *Indexed*, and that no source shows *needs reindex*. |
| Answers do not use the knowledge base | Turn on **Knowledge** in the composer and read the notice below the answer; small local models sometimes ignore context, so try a larger one. |
| *needs reindex* after changing `RAG_EMBEDDING_MODEL` | Click **Reindex all** in Admin → Knowledge base. |
| `type "vector" does not exist` / `extension "vector" is not available` | The database lacks pgvector: use the Compose image `pgvector/pgvector:pg17-trixie` or install the extension. |
| An embedding model cannot be pulled (for example a corporate proxy blocks the download) | Use `RAG_EMBEDDING_PROVIDER=hashing` until the model is available, then switch and **Reindex all**. |

---

## Running without Docker

The quickest way is `scripts/run-dev.sh start`. It starts PostgreSQL in a container, builds and runs the backend jar
(http://localhost:8088) and the Vite dev server (http://localhost:5173) using the values from `.env`, and translates
container host names such as `host.docker.internal` to `localhost`. Stop everything with `scripts/run-dev.sh stop`.

To do the same by hand:

Prerequisites: **JDK 21+**, **Node.js 20+** (tested with 24), **PostgreSQL 15+** (or `docker compose up -d postgres`),
and optionally Ollama or another OpenAI-compatible server.

```bash
# 1. Database
docker compose up -d postgres            # PostgreSQL on localhost:5434 (aichat/aichat)

# 2. Backend (http://localhost:8088)
cd backend
export ADMIN_USERNAME=admin ADMIN_PASSWORD='change-me-please' JWT_SECRET="$(openssl rand -base64 48)"
export QWEN_BASE_URL=http://localhost:11434/v1 QWEN_MODEL=qwen3:8b QWEN_ENGINE=ollama
export LLAMA_BASE_URL=http://localhost:11434/v1 LLAMA_MODEL=llama3.1:8b LLAMA_ENGINE=ollama
export OPENAI_API_KEY=sk-...             # optional
./mvnw spring-boot:run

# 3. Frontend (http://localhost:5173, proxies /api to :8088)
cd frontend
npm install
npm run dev
```

Set `VITE_PROXY_TARGET` if the backend runs elsewhere.

---

## API

Interactive documentation: **http://localhost:8088/swagger-ui.html**. All endpoints except auth/health require
`Authorization: Bearer <token>`; errors are RFC 9457 problem details with a stable `code`.

```bash
API=http://localhost:8088
TOKEN=$(curl -s -X POST $API/api/auth/login -H 'Content-Type: application/json' \
  -d '{"login":"admin","password":"change-me-please"}' | jq -r .accessToken)
AUTH="Authorization: Bearer $TOKEN"

# Providers, models, health (drives the UI selector)
curl -s $API/api/providers -H "$AUTH" | jq
curl -s $API/api/providers/qwen/models -H "$AUTH" | jq
curl -s "$API/api/providers/llama/health?refresh=true" -H "$AUTH" | jq

# Stream an answer (Server-Sent Events)
curl -N -X POST $API/api/chat/stream -H "$AUTH" -H 'Content-Type: application/json' \
  -H 'Accept: text/event-stream' \
  -d '{"message":"Explain virtual threads in two sentences","provider":"qwen","model":"qwen3:8b"}'

# Continue the same conversation with another provider
curl -N -X POST $API/api/chat/stream -H "$AUTH" -H 'Content-Type: application/json' \
  -H 'Accept: text/event-stream' \
  -d '{"conversationId":"<id from the start event>","message":"Now give an example","provider":"llama"}'

# Non-streaming variant
curl -s -X POST $API/api/chat -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"message":"Hello","provider":"llama"}' | jq

# Regenerate the last answer, stop a running generation
curl -N -X POST $API/api/chat/regenerate -H "$AUTH" -H 'Content-Type: application/json' \
  -H 'Accept: text/event-stream' -d '{"conversationId":"<id>","provider":"openai"}'
curl -s -X POST $API/api/chat/generations/<assistantMessageId>/cancel -H "$AUTH"

# Compare providers side by side
curl -N -X POST $API/api/compare/stream -H "$AUTH" -H 'Content-Type: application/json' \
  -H 'Accept: text/event-stream' \
  -d '{"prompt":"Capital of France?","targets":[{"provider":"openai"},{"provider":"qwen"},{"provider":"llama"}]}'

# Conversations
curl -s $API/api/conversations -H "$AUTH" | jq
curl -s $API/api/conversations/<id> -H "$AUTH" | jq
curl -s -X PATCH $API/api/conversations/<id> -H "$AUTH" -H 'Content-Type: application/json' -d '{"title":"Renamed"}'
curl -s -X DELETE $API/api/conversations/<id> -H "$AUTH"

# Administration
curl -s "$API/api/admin/usage?days=7&tz=Europe/Berlin" -H "$AUTH" | jq
curl -s -X PUT $API/api/admin/providers/llama/settings -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"enabled":true,"defaultModel":"llama3.1:8b","timeoutSeconds":120,"temperature":0.5,"maxTokens":2048}'

# Knowledge base (administrators): upload, add text, follow indexing, inspect, test
curl -s -X POST $API/api/admin/knowledge/files -H "$AUTH" -F file=@Architecture.pdf -F collectionId=<id> -F tags=pricing
curl -s -X POST $API/api/admin/knowledge/text -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"title":"Pricing rules","content":"# Pricing\n\nEvery market has one currency.","tags":["pricing"]}'
curl -s $API/api/admin/knowledge/<sourceId>/status -H "$AUTH" | jq
curl -s "$API/api/admin/knowledge?collectionId=<id>&status=INDEXED&sort=UPDATED_AT&direction=desc" -H "$AUTH" | jq
curl -s $API/api/admin/knowledge/<sourceId>/chunks -H "$AUTH" | jq
curl -s -X POST $API/api/admin/knowledge/search -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"query":"How is currency converted?","topK":5,"similarityThreshold":0.3}' | jq
curl -s -X POST $API/api/admin/knowledge/test-answer -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"question":"How is currency converted?","provider":"qwen"}' | jq

# Chat with the knowledge base (all available collections, or "collectionIds":[...])
curl -N -X POST $API/api/chat/stream -H "$AUTH" -H 'Content-Type: application/json' \
  -H 'Accept: text/event-stream' \
  -d '{"message":"How is currency converted?","provider":"llama","knowledge":{"enabled":true,"collectionIds":[]}}'
```

Knowledge-base endpoints (all under `/api/admin/knowledge`, `ADMIN` only): `POST /files`, `POST /text`, `GET /`
(filter, sort, page), `GET /stats`, `GET|PUT|DELETE /{id}`, `GET /{id}/status`, `GET /{id}/content`,
`GET /{id}/chunks`, `POST /{id}/reindex[?reextract=true]`, `POST /reindex-stale`, `POST /{id}/enable|disable`,
`GET|POST /collections`, `PUT|DELETE /collections/{id}`, `POST /search`, `POST /test-answer`. For every user:
`GET /api/knowledge/collections` and `GET /api/knowledge/chunks/{chunkId}` (cited excerpt).

`GET /api/providers` example:

```json
[
  { "id": "openai", "name": "OpenAI",      "kind": "CLOUD", "enabled": true, "available": false,
    "status": "DOWN", "statusMessage": "API key not configured (set OPENAI_API_KEY)", "defaultModel": "gpt-5.6-sol" },
  { "id": "qwen",   "name": "Qwen Local",  "kind": "LOCAL", "enabled": true, "available": true,
    "status": "UP",   "statusMessage": null, "defaultModel": "qwen3:8b" },
  { "id": "llama",  "name": "Llama Local", "kind": "LOCAL", "enabled": true, "available": true,
    "status": "UP",   "statusMessage": null, "defaultModel": "llama3.1:8b" }
]
```

Chat stream events:

```text
event: start      {"conversationId","userMessageId","assistantMessageId","provider","providerName","model","newConversation",...}
event: citations  {"citations":[{"index":1,"title":"…","pageNumber":14,...}],"retrievedCount","notice"}   only with knowledge enabled
event: reasoning  {"text":"..."}                  only for models exposing their thinking
event: delta      {"text":"..."}                  answer tokens
event: done       {"status":"COMPLETE|CANCELLED","finishReason","inputTokens","outputTokens","latencyMs","timeToFirstTokenMs","estimatedCost","local"}
event: error      {"code":"PROVIDER_UNAVAILABLE","message":"Qwen Local is currently unavailable.","alternatives":[{"id":"openai","name":"OpenAI"}],...}
event: title      {"conversationId","title"}      generated title of a new conversation
```

---

## Observability

- **Health:** `/actuator/health` (+ `/liveness`, `/readiness`), provider health via `/api/providers/*/health`
- **Metrics:** `/actuator/prometheus` — `llm_requests_total`, `llm_request_duration_seconds` (histogram),
  `llm_input_tokens`, `llm_output_tokens`, `llm_errors_total` (labels `provider`, `model`, `status`, `code`),
  `llm_provider_up`, `llm_active_generations`, `resilience4j_circuitbreaker_*`, `http_server_requests_*`, JVM metrics,
  and the knowledge-base metrics `rag_*` ([list](#rag-security-and-observability)).
  `docker compose --profile monitoring up -d` starts Prometheus with a ready scrape config.
- **Logs:** ECS JSON in Docker (`LOG_FORMAT`), every line carries the request `correlationId` (also returned as
  `X-Correlation-Id` and forwarded to providers as `X-Request-Id` / `X-Client-Request-Id`). API keys, `Authorization`
  headers, passwords and prompt contents are never logged.

## Security notes

- Passwords: BCrypt; login lockout after 5 failures (15 min, or reset by an administrator)
- JWT (HS256) with issuer validation; disabling or deleting a user revokes access immediately
- Users can only see their own conversations (other ids return 404)
- Provider keys stay on the server; the admin UI only shows a masked hint (`sk-••••abcd`)
- Rate limits per IP (auth) and per user (chat, compare); max. 3 concurrent generations per user
- nginx adds CSP, `X-Frame-Options`, `nosniff` and `Referrer-Policy`; model output is rendered without raw HTML
- Knowledge base: administrator-only management, validated uploads (names, types, content sniffing, size and
  decompression limits), retrieved text treated as untrusted data in prompts — see
  [RAG security](#rag-security-and-observability)
- In production: set `JWT_SECRET`, a strong `POSTGRES_PASSWORD`, terminate TLS in front of nginx, restrict
  `/actuator/prometheus` at the network level and set `springdoc.api-docs.enabled=false` if the API docs should not be public

---

## Testing

```bash
scripts/test.sh                 # frontend checks, then all backend tests (container engine auto-configured)
cd backend && ./mvnw verify     # 154 tests: unit, WireMock adapter tests, Testcontainers integration tests
cd frontend && npm test         # 47 Vitest + React Testing Library tests
```

No test calls a real LLM or embedding API: OpenAI, Qwen and Llama are simulated with WireMock (including SSE streams,
errors and slow streams for cancellation), embeddings come from a deterministic `FakeEmbeddingProvider`, and
PostgreSQL with pgvector runs in Testcontainers (`pgvector/pgvector:pg17-trixie`). Coverage includes provider
resolution, the OpenAI / Qwen / Llama adapters, unavailable local providers, switching providers mid-conversation,
conversation persistence, authorization, streaming and cancellation, context management and summarisation,
resilience (retry safety, circuit breaker, timeouts) and metrics — and for the knowledge base: extraction of every
file type, chunking, embeddings, vector storage and semantic search, ingestion of files and text, duplicates and
versions, enable/disable, collection filtering, reindexing, deletion of chunks and vectors, context building, citations
in chat and admin-only access. The frontend tests cover the knowledge list and filters, uploads with duplicate
handling, text entries, the detail tabs and chunk viewer, delete confirmation, reindexing, the test console and
citations.

> Testcontainers with Podman: export `DOCKER_HOST` (Podman socket) and, for a rootful Podman machine,
> `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/run/podman/podman.sock` (or `TESTCONTAINERS_RYUK_DISABLED=true`).

---

## Adding a new provider

### Any OpenAI-compatible server — configuration only

Mistral or DeepSeek via vLLM, LM Studio, LocalAI, a hosted OpenAI-compatible gateway…:

```yaml
llm:
  providers:
    mistral:
      type: openai-compatible
      engine: vllm
      display-name: Mistral Local
      kind: LOCAL
      base-url: ${MISTRAL_BASE_URL:http://localhost:8003/v1}
      default-model: mistralai/Mistral-7B-Instruct-v0.3
```

Restart the backend — the provider appears in the selector, compare mode, health checks, metrics and usage
statistics. No code changes.

### A different API (Anthropic, Gemini, Bedrock, Azure OpenAI, …) — one adapter

Only infrastructure code is added; chat logic, registry, API and frontend stay untouched.

1. **Adapter** — implement the port in `infrastructure/llm/anthropic`:

   ```java
   public class AnthropicLlmProvider implements LlmProvider {
       public ProviderDescriptor descriptor() { ... }                      // id, "Claude", CLOUD, capabilities, defaults
       public Mono<LlmResponse> chat(LlmRequest request) { ... }          // POST /v1/messages
       public Flux<LlmChunk> stream(LlmRequest request) { ... }           // map content_block_delta -> TextDelta,
                                                                          // message_delta usage -> Completed
       public List<LlmModel> availableModels() { ... }                    // GET /v1/models
       public ProviderHealth health() { ... }                             // cheap call, no inference
   }
   ```

   Translate vendor errors to the provider-neutral exceptions (unreachable → `LlmProviderUnavailableException`
   with `retryable=true` only if the request was not processed; 401 → `LlmAuthenticationException`; 429 →
   `LlmRateLimitException`; …). `LlmWebClientFactory`, `LlmErrorTranslator` and `JsonValues` can be reused.

2. **Factory** — one Spring bean:

   ```java
   @Component
   public class AnthropicProviderFactory implements LlmProviderFactory {
       public String type() { return "anthropic"; }
       public LlmProvider create(String id, ProviderProperties properties) {
           return new AnthropicLlmProvider(id, properties, support);
       }
   }
   ```

3. **Configuration** — `llm.providers.claude.type: anthropic` with `api-key`, `base-url`, `default-model`, `kind: CLOUD`
   and optional `pricing`.

Resilience, metrics, health monitoring, cost estimation, usage tracking, the provider selector and compare mode work
automatically. Add a WireMock test like `OpenAiLlmProviderTest`.

### Further extension points

| Feature | Where it plugs in |
|---------|-------------------|
| Tool / function calling, MCP servers | new `LlmChunk` subtype (`ToolCall`), tool definitions on `LlmRequest`, `LlmCapability.TOOL_CALLING` gating, an application-level tool executor (MCP client as an outbound port) |
| Multimodal input, file uploads | content parts on `LlmMessage` (adapters already ignore unknown parts), `LlmCapability.VISION`, a storage port |
| RAG extensions (connectors, OCR, other vector stores, rerankers, query rewriting, GraphRAG) | see [RAG extension points](#rag-extension-points) |
| Web search | another `KnowledgeAugmenter`-style port used by `ChatService`, or a retriever fused with the knowledge results |
| Speech-to-text / text-to-speech | separate ports (`TranscriptionProvider`, `SpeechProvider`) with the same registry/factory pattern |
| Agentic workflows | orchestrate multiple `LlmProvider.chat/stream` calls in an application service; streaming events already support new event types |
| Tokenizer-accurate budgets | implement `TokenEstimator` (e.g. per model family) — the context manager is unchanged |

---

## Documentation

| Document | Contents |
|----------|----------|
| [docs/architecture.md](docs/architecture.md) | Architecture with diagrams: context, containers, hexagonal layers, provider abstraction, streaming / failover / cancel / compare / health / auth flows, knowledge base (ingestion, retrieval, RAG chat), data model, deployment, cross-cutting concerns, limitations |
| [docs/adr/](docs/adr/README.md) | Architecture decision records (hexagonal design, provider registry, OpenAI-compatible adapter, SSE, virtual threads, context strategy, retry safety, JWT, RAG module with pgvector, asynchronous ingestion) |
| [AGENTS.md](AGENTS.md) | Guide for AI coding agents and contributors: repository map, commands, architecture invariants, testing rules, definition of done |
| [backend/AGENTS.md](backend/AGENTS.md), [frontend/AGENTS.md](frontend/AGENTS.md) | Package and component layout, conventions and gotchas per side |
| [.github/skills/](.github/skills) | Step-by-step playbooks (`SKILL.md`): [local-stack](.github/skills/local-stack/SKILL.md), [add-llm-provider](.github/skills/add-llm-provider/SKILL.md), [run-tests](.github/skills/run-tests/SKILL.md), [database-migration](.github/skills/database-migration/SKILL.md), [knowledge-base](.github/skills/knowledge-base/SKILL.md) |

---

## Troubleshooting

Start with `scripts/status.sh`: it shows which component is down, and probes the model servers directly from your
machine.

| Symptom | Fix |
|---------|-----|
| *OpenAI — Offline: API key not configured* | Set `OPENAI_API_KEY` and restart the backend (`docker compose up -d backend`). |
| *Qwen Local — Offline* | Check the server: `curl $QWEN_BASE_URL/models`. From Docker use `host.docker.internal` (not `localhost`) for servers on the host; on Linux the compose file maps it via `host-gateway`. |
| Provider *Degraded: default model not found* | The server does not serve `QWEN_MODEL`/`LLAMA_MODEL`; pull it (`ollama pull qwen3:8b`) or change the default model in Admin → Providers. |
| First answer of a local model is slow / times out | The model is loading into memory. Raise `QWEN_TIMEOUT`/`LLAMA_TIMEOUT` or the timeout in Admin → Providers. |
| Titles stay "provisional" with reasoning models | Set the matching `*_ENGINE` so thinking can be disabled for titles, or use `CHAT_TITLE_PROVIDER=llama`. |
| Tokens arrive in bursts behind a custom proxy | Disable response buffering for `/api` (`proxy_buffering off;` in nginx) — the backend also sends `X-Accel-Buffering: no`. |
| `401` after restarting the backend | No `JWT_SECRET` was set, so tokens from the previous run are invalid. Set a fixed secret. |
| Port already in use | Change `FRONTEND_PORT`, `BACKEND_PORT` or `POSTGRES_PORT` in `.env`. |
| `docker: unknown command: compose` | Use the standalone `docker-compose` binary or `podman compose`. |
| Integration tests cannot start containers | Ensure Docker/Podman is running; with Podman see the note in [Testing](#testing). |
| Locked out after failed logins | Wait 15 minutes or let an administrator *Reset status* in Admin → Users. |
| Knowledge base problems (indexing failures, no retrieval results, uploads rejected) | See [RAG troubleshooting](#rag-troubleshooting). |
