---
name: run-tests
description: Run, select, debug and write automated tests for AI Workspace, covering backend JUnit 5 with Mockito, Testcontainers PostgreSQL and WireMock, plus frontend type check, oxlint, Vitest and React Testing Library. Use this when asked to run tests, verify a change, investigate a failing test or add test coverage.
---

# Run and write tests

No test may reach a real LLM. OpenAI, Qwen and Llama are simulated with WireMock, so no API key or model server is
needed.

## Run

```bash
scripts/test.sh                       # frontend checks, then all backend tests
scripts/test.sh frontend              # tsc type check + oxlint + vitest
bash scripts/tests/audio.test.sh      # script startup routing; fake container/health functions, no inference
scripts/test.sh backend               # ./mvnw -B verify (unit + integration)
scripts/test.sh backend --test QwenLlmProviderTest            # one class (comma-separated list or pattern)
scripts/test.sh backend --test 'ChatFlowIntegrationTest#*cancel*'   # matching methods
scripts/test.sh backend --offline     # Maven offline mode once dependencies are cached
```

Equivalent direct commands:

```bash
cd frontend && npm run typecheck && npm run lint && npm test
cd frontend && npx vitest run src/api/sse.test.ts          # one file
cd backend && ./mvnw -B verify                              # needs a container engine reachable via DOCKER_HOST
```

`scripts/test.sh` finds the container engine for Testcontainers. With a Podman machine it exports `DOCKER_HOST`
plus `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/run/podman/podman.sock` (rootful) or `TESTCONTAINERS_RYUK_DISABLED=true`
(rootless). Set the same variables yourself when running Maven directly.

## What covers what

| Concern | Tests |
|---------|-------|
| Provider resolution, defaults, disabled/unknown providers | `LlmProviderRegistryTest`, `ProviderApiIntegrationTest` |
| OpenAI adapter (Responses API, streaming, errors) | `OpenAiLlmProviderTest` |
| OpenAI-compatible adapter, Qwen reasoning switches, Llama | `OpenAiCompatibleLlmProviderTest`, `QwenLlmProviderTest`, `LlamaLlmProviderTest`, `ThinkTagParserTest` |
| Retry, circuit breaker, timeouts | `ResilientLlmProviderTest` |
| Local provider unavailable, health monitor | `ProviderHealthMonitorTest`, `ChatFlowIntegrationTest` (offline provider), `ProviderApiIntegrationTest` |
| Streaming, switching providers, regenerate, cancel, persistence | `ChatFlowIntegrationTest` |
| Context strategies and summaries | `ConversationContextManagerTest`, `ConversationSummaryIntegrationTest` |
| Authorization and user isolation | `AuthorizationIntegrationTest` |
| Titles, usage and cost, rate limiting | `TitleServiceTest`, `UsageRecorderTest`, `ConfiguredPricingCostEstimatorTest`, `TokenBucketRateLimiterTest` |
| Frontend SSE parsing, selector, markdown, auth pages, errors | `src/**/*.test.ts(x)` |

## Write tests

**Backend integration test** (real HTTP, real PostgreSQL, simulated providers):

```java
class MyFeatureIntegrationTest extends AbstractIntegrationTest {

    @Test
    void streamsAnswerFromQwen() {
        String token = register("alice");
        stubStream("qwen", null, "Hello", " world");          // WireMock SSE: deltas + usage + [DONE]
        List<SseEvent> events = streamEvents("/api/chat/stream",
                Map.of("message", "Hi", "provider", "qwen", "model", "qwen3:8b"), token);
        assertThat(events).extracting(SseEvent::event).contains("start", "delta", "done");
    }
}
```

- The base class starts one PostgreSQL 17 container and one WireMock server for the whole run. Providers `openai`,
  `qwen` and `llama` point at WireMock; `offline` points at a closed port.
- `@BeforeEach` resets WireMock and the `provider_settings` table, and installs default model and stream stubs.
  Override stubs inside the test.
- Use unique usernames per test (data persists across tests in the shared database).
- Send `Accept: text/event-stream` for SSE endpoints (`callStream` / `streamEvents` do it).

**Backend unit test**: plain JUnit 5 with Mockito, or a WireMock server for adapters (see
`OpenAiCompatibleLlmProviderTest`). Use `StepVerifier` for `Flux`/`Mono` and `support/LlmTestSupport` for properties,
requests and SSE bodies.

**Frontend test**: Vitest and React Testing Library, colocated `*.test.tsx`. Stub the network with
`vi.stubGlobal('fetch', ...)`, wrap query hooks in a fresh `QueryClientProvider`, and query by role or label.

## Debug failures

1. Read the first failure: `backend/target/surefire-reports/<TestClass>.txt` (and `-output.txt` for logs).
2. `Could not find a valid Docker environment`: start Docker or Podman, then use `scripts/test.sh` (it sets the
   variables).
3. Ryuk or socket errors on Podman: rootful needs `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/run/podman/podman.sock`,
   rootless needs `TESTCONTAINERS_RYUK_DISABLED=true`.
4. Flaky timing in streaming tests: wait on events (`streamEvents` collects until the stream ends) instead of sleeping.
5. A test depends on another test's admin changes: make sure it does not rely on `provider_settings` (reset per test)
   or on data created by other tests.
6. Frontend `act(...)` warnings or timeouts: `await` `findBy*` queries and user events.
