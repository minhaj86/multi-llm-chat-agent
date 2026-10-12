# AGENTS.md

Guidance for AI coding agents (and humans) working on **AI Workspace**: a full-stack chat application with switchable
LLM providers. Read this file first, then the guide for the area you are changing:

| Area | Guide |
|------|-------|
| Spring Boot backend | [backend/AGENTS.md](backend/AGENTS.md) |
| React frontend | [frontend/AGENTS.md](frontend/AGENTS.md) |
| Architecture and diagrams | [docs/architecture.md](docs/architecture.md) |
| Architecture decisions | [docs/adr/](docs/adr/README.md) |
| Step-by-step playbooks | [.github/skills/](.github/skills) (`local-stack`, `add-llm-provider`, `run-tests`, `database-migration`) |

## Project at a glance

- **Backend**: Java 21, Spring Boot 3.5 (servlet stack with virtual threads), Spring Security with JWT, Spring Data JPA,
  PostgreSQL 17, Flyway, WebClient/Reactor for LLM calls, Resilience4j, Micrometer/Prometheus, SSE streaming.
- **Frontend**: React 19, TypeScript, Vite, Tailwind CSS 4, TanStack Query, zustand, react-markdown.
- **LLM providers**: OpenAI (Responses API), plus Qwen and Llama through any OpenAI-compatible server (Ollama, vLLM,
  llama.cpp, LM Studio, LocalAI). A user can switch provider and model per message without restarts.
- **Runtime**: Docker Compose (`postgres`, `backend`, `frontend`; optional profiles `qwen`, `llama`, `ollama`,
  `monitoring`). Works with Docker or Podman.

## Repository map

```text
backend/              Spring Boot API: api / application / domain / infrastructure (hexagonal)
frontend/             React SPA (served by nginx in Docker, Vite in development)
scripts/              deploy.sh, run-dev.sh, status.sh, test.sh and lib/common.sh
docs/                 architecture.md and adr/ (architecture decision records)
deploy/prometheus/    Prometheus scrape configuration (monitoring profile)
.github/skills/       Task playbooks (SKILL.md) for agents
docker-compose.yml    Full stack; optional model servers behind profiles
.env.example          Configuration template. Copy to .env; .env is git-ignored and must never be committed
Makefile              Shortcuts for the scripts (make help)
```

## Commands

| Task | Command |
|------|---------|
| Start the whole stack in containers | `scripts/deploy.sh` (alias `make up`) |
| Stop it (data is kept) | `scripts/deploy.sh down` |
| Health of containers, endpoints, DB and providers | `scripts/status.sh` (`--brief`, `--json`, `--watch 5`) |
| Development mode with hot reload | `scripts/run-dev.sh start` / `stop` / `logs` |
| All tests | `scripts/test.sh` |
| Backend tests (optionally one class) | `scripts/test.sh backend [--test ChatFlowIntegrationTest]` |
| Frontend type check, lint and tests | `scripts/test.sh frontend` |
| Quick backend compile | `cd backend && ./mvnw -B -q -DskipTests compile` |
| Quick frontend type check | `cd frontend && npm run typecheck` |

URLs: UI http://localhost:3080 (Docker) or http://localhost:5173 (dev mode), API http://localhost:8088,
Swagger UI http://localhost:8088/swagger-ui.html, metrics http://localhost:8088/actuator/prometheus.
The administrator account comes from `ADMIN_USERNAME`/`ADMIN_PASSWORD` in `.env`.

Always use `./mvnw` (not a global `mvn`): the wrapper pins Maven and uses `JAVA_HOME` (JDK 21 or newer).

## Architecture invariants

These rules keep the system provider-neutral and extensible. Do not break them; if a change seems to require it,
write an ADR first.

1. **Dependencies point inward.** `api` and `infrastructure` depend on `application`, which depends on `domain`.
   `domain` uses only the JDK and Reactor types. `application` and `domain` never import from `infrastructure` or `api`.
2. **No vendor knowledge outside adapters.** Application and domain code work with neutral types (`LlmRequest`,
   `LlmChunk`, `LlmResponse`, `LlmModel`, `ProviderHealth`). Vendor protocols, DTOs and quirks live only in
   `infrastructure/llm/<vendor>`. Never branch on provider ids (`if ("openai".equals(...))`, `switch (provider)`).
3. **Providers are resolved, not selected by hand.** Use `LlmProviderRegistry.resolve(provider, model)`. A new
   provider means a new `LlmProvider` adapter, an `LlmProviderFactory` bean and configuration under
   `llm.providers.<id>`. Chat logic, controllers and the frontend stay unchanged (skill `add-llm-provider`).
4. **The frontend discovers providers.** Providers and models come from `GET /api/providers` and
   `GET /api/providers/{id}/models`. Never hardcode provider ids, names or model names in React.
5. **Secrets never leave the backend.** API keys are not serialized (the admin API exposes only `apiKeyConfigured`
   and a masked hint), not logged and never sent to the browser. All LLM calls go through Spring Boot.
6. **Errors are translated at the edge.** Adapters map vendor failures to `LlmException` subclasses
   (`LlmProviderUnavailableException`, `LlmAuthenticationException`, `LlmRateLimitException`, `LlmTimeoutException`,
   `LlmResponseException`). Vendor exceptions never reach the application layer.
7. **Retries only when safe.** Retry only if `LlmException.isRetryable()`. Never retry after a stream has emitted
   data, never retry timeouts, never blindly retry POSTs (a completed request may already be billed). See ADR 0007.
8. **Health checks never run inference.** Probes use cheap endpoints such as `GET /v1/models`.
9. **Schema changes only through new Flyway migrations.** Never edit an applied `V*__*.sql` file
   (skill `database-migration`).
10. **No sensitive logging.** Never log API keys, `Authorization` headers, passwords or full prompts.
11. **Configuration, not constants.** URLs, model names, timeouts, token limits and prices come from `application.yml`
    with environment overrides. Runtime-editable provider settings (enabled, default model, timeout, temperature,
    max tokens) live in the `provider_settings` table and are changed from the admin UI.

## Testing rules

- Automated tests must never call real OpenAI or real local model servers. LLM servers are simulated with WireMock
  (`AbstractIntegrationTest`) or mocked with Mockito.
- Backend integration tests extend `AbstractIntegrationTest`: a PostgreSQL Testcontainer plus WireMock providers
  `openai`, `qwen`, `llama` and an always-unreachable `offline` provider.
- `scripts/test.sh` configures Testcontainers for Docker or Podman automatically. Running Maven directly with
  Podman needs `DOCKER_HOST` and either `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` (rootful) or
  `TESTCONTAINERS_RYUK_DISABLED=true` (rootless).
- Frontend tests use Vitest and React Testing Library and live next to the code as `*.test.ts(x)`.
- Add or update tests with every behaviour change; bug fixes get a regression test.

## Conventions

- Java: constructor injection, records for DTOs and value objects, no Lombok, Javadoc on ports and extension points.
  4-space indent, lines up to 120 characters.
- TypeScript: strict mode, `import type` for type-only imports (`verbatimModuleSyntax`), no enums or parameter
  properties (`erasableSyntaxOnly`), 2-space indent, single quotes, no semicolons.
- REST errors are RFC 9457 problem details with a machine-readable `code`; SSE events are `start`, `delta`,
  `reasoning`, `done`, `error` and `title`.
- Comments explain why, not what. Keep documentation (README, docs/, AGENTS.md, skills) in sync with behaviour.
- Shell scripts must run on macOS's bash 3.2: no associative arrays, no `${var,,}`, guard empty arrays with
  `${arr[@]+"${arr[@]}"}`, no GNU-only flags (`sed -i`, `timeout`). Shared helpers belong in `scripts/lib/common.sh`.

## Definition of done

1. `scripts/test.sh` passes (backend unit and integration tests, frontend type check, lint and tests).
2. New behaviour is covered by tests, and no test depends on a real LLM.
3. The architecture invariants above still hold (check imports in `application` and `domain`).
4. For runtime, Docker or configuration changes: `scripts/deploy.sh` succeeds and `scripts/status.sh` reports healthy.
5. Documentation is updated: README for user-facing changes, `docs/architecture.md` for structural changes,
   a new ADR for significant decisions, and `.env.example` for new settings.
