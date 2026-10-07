# AI Workspace

A production-grade, full-stack AI chat application with **switchable LLM providers**. Chat with **OpenAI** (cloud),
**Qwen** and **Llama** (local, OpenAI-compatible servers such as Ollama, vLLM or llama.cpp) — and switch provider or
model at any time, even in the middle of a conversation, without restarting anything.

- **Backend:** Java 21 · Spring Boot 3.5 · Spring MVC + WebClient · Spring Security (JWT) · JPA/PostgreSQL · Flyway ·
  Resilience4j · Micrometer/Prometheus
- **Frontend:** React 19 · TypeScript · Vite · Tailwind CSS · TanStack Query · Zustand
- **Infrastructure:** Docker Compose (frontend + backend + PostgreSQL, optional GPU model servers, Prometheus)

[Features](#features) · [Quick start](#quick-start-docker-compose) · [Architecture](#architecture) ·
[Configuration](#configuration) · [API](#api) · [Adding a provider](#adding-a-new-provider) ·
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

**Providers**
- Provider/model selector built **entirely from the backend** (`/api/providers`); offline providers are shown as
  `Qwen Local — Offline` and cannot be selected
- Periodic, cheap health checks (`GET /v1/models`, never inference) plus passive health from real traffic
- Friendly errors with one-click alternatives: *“Qwen Local is currently unavailable. Switch to: [OpenAI] [Llama Local]”*
- **Compare mode**: one prompt, up to 4 providers side by side with latency, input/output tokens and cost (`Local` for
  local models)

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

## Quick start (Docker Compose)

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

> **Podman users:** `docker compose` works with Podman when `DOCKER_HOST` points at the Podman socket
> (`podman machine inspect --format '{{.ConnectionInfo.PodmanSocket.Path}}'`). The standalone `docker-compose`
> binary or `podman compose` behave the same way.

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

## Running without Docker

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
```

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
  `llm_provider_up`, `llm_active_generations`, `resilience4j_circuitbreaker_*`, `http_server_requests_*`, JVM metrics.
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
- In production: set `JWT_SECRET`, a strong `POSTGRES_PASSWORD`, terminate TLS in front of nginx, restrict
  `/actuator/prometheus` at the network level and set `springdoc.api-docs.enabled=false` if the API docs should not be public

---

## Testing

```bash
cd backend && ./mvnw verify     # 95 tests: unit, WireMock adapter tests, Testcontainers integration tests
cd frontend && npm test         # Vitest + React Testing Library
```

No test calls a real LLM: OpenAI, Qwen and Llama are simulated with WireMock (including SSE streams, errors and slow
streams for cancellation), PostgreSQL runs in Testcontainers. Coverage includes provider resolution, the OpenAI /
Qwen / Llama adapters, unavailable local providers, switching providers mid-conversation, conversation persistence,
authorization, streaming and cancellation, context management and summarisation, resilience (retry safety, circuit
breaker, timeouts) and metrics.

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
| RAG, web search | a `RetrievalPort` used by `ConversationContextManager` to add grounded context before the history |
| Speech-to-text / text-to-speech | separate ports (`TranscriptionProvider`, `SpeechProvider`) with the same registry/factory pattern |
| Agentic workflows | orchestrate multiple `LlmProvider.chat/stream` calls in an application service; streaming events already support new event types |
| Tokenizer-accurate budgets | implement `TokenEstimator` (e.g. per model family) — the context manager is unchanged |

---

## Troubleshooting

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
