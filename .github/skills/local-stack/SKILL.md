---
name: local-stack
description: Start, stop, inspect and troubleshoot AI Workspace on a developer machine, either with Docker/Podman Compose or in dev mode with hot reload, including the optional local Qwen/Llama model servers. Use this when asked to run the app, check whether it is healthy, read logs or fix startup problems.
---

# Local stack

All operations go through the scripts in `scripts/`. They work with Docker or Podman (the Podman socket is found
automatically) and with macOS's default bash 3.2.

## 1. Check what is running

```bash
scripts/status.sh            # containers, dev processes, endpoints, DB, LLM providers, model servers
scripts/status.sh --json     # machine-readable; exit code 0 = usable (backend, database and a frontend are up)
```

## 2. Choose a mode

| Goal | Command | UI |
|------|---------|----|
| Production-like stack (nginx, backend and postgres containers) | `scripts/deploy.sh` | http://localhost:3080 |
| Hot reload while coding (postgres container, backend jar and Vite on the host) | `scripts/run-dev.sh start` | http://localhost:5173 |

Both modes use backend port 8088, so only one can run at a time. `run-dev.sh start` offers to stop the backend and
frontend containers (`--yes` skips the question). `deploy.sh` refuses to start while the dev backend runs.

## 3. Start

```bash
scripts/deploy.sh                       # build images, start, wait for readiness, print status
scripts/deploy.sh up --no-build         # start without rebuilding (fast)
scripts/deploy.sh up --profile ollama   # also run Ollama in a container (CPU; port 11435)
scripts/deploy.sh up --profile monitoring   # also run Prometheus on :9090
scripts/deploy.sh up --fresh --yes      # wipe the database volume first (destroys users and conversations)
```

On the first run `.env` is created from `.env.example`, with a generated `JWT_SECRET`, `POSTGRES_PASSWORD` and
`ADMIN_PASSWORD`. If Ollama runs on the host (port 11434), Qwen and Llama are pointed at it automatically.
Log in with `ADMIN_USERNAME` / `ADMIN_PASSWORD` from `.env`; never print or commit these values.

## 4. Logs, restart, stop

```bash
scripts/deploy.sh logs backend          # follow container logs (any service name)
scripts/deploy.sh restart               # restart backend and frontend containers
scripts/deploy.sh down                  # stop; data is kept (add --volumes to delete it)
scripts/run-dev.sh logs backend         # dev mode logs (.run/backend.log, .run/frontend.log)
scripts/run-dev.sh stop [--with-db]     # stop dev processes (and the postgres container)
```

Configuration changes in `.env` take effect with `scripts/deploy.sh up --no-build` (Compose recreates containers whose
environment changed). Provider settings (enabled, default model, timeout, temperature, max tokens) can also be
changed at runtime in **Admin → Providers**, without a restart.

## Local model servers (Qwen, Llama)

They are optional. The application keeps working when they are offline; the selector shows "Qwen Local — Offline".

- **macOS (recommended)**: run Ollama natively for Metal GPU acceleration (Docker on macOS has no GPU access):
  ```bash
  ollama serve &                          # if the Ollama app is not already running
  ollama pull qwen3:8b && ollama pull llama3.1:8b
  ```
  `.env` (for containers; dev mode translates `host.docker.internal` to `localhost` automatically):
  ```dotenv
  QWEN_BASE_URL=http://host.docker.internal:11434/v1
  QWEN_ENGINE=ollama
  QWEN_MODEL=qwen3:8b
  QWEN_MODELS=qwen3:8b
  LLAMA_BASE_URL=http://host.docker.internal:11434/v1
  LLAMA_ENGINE=ollama
  LLAMA_MODEL=llama3.1:8b
  LLAMA_MODELS=llama3.1:8b
  ```
- **Linux/WSL2 with NVIDIA GPU**: `scripts/deploy.sh up --profile qwen --profile llama` starts vLLM (Qwen, :8001)
  and llama.cpp (Llama, :8002). Defaults: `QWEN_BASE_URL=http://qwen:8000/v1`, `LLAMA_BASE_URL=http://llama:8000/v1`.
  Requires the NVIDIA Container Toolkit.
- Any other OpenAI-compatible server (LM Studio, LocalAI, SGLang) works by setting `*_BASE_URL`, `*_ENGINE`
  and `*_MODEL`.

## Troubleshooting

| Symptom | Action |
|---------|--------|
| `No running container engine found` | Start Docker Desktop or run `podman machine start`. |
| `Port 8088 is used by the local dev backend` | `scripts/run-dev.sh stop`, then deploy again. |
| `Port N (...) is already in use by another program` | Free the port, or set `FRONTEND_PORT`, `BACKEND_PORT` or `POSTGRES_PORT` in `.env`. |
| Provider shows Offline | Check the "Model servers" section of `scripts/status.sh` (probes the base URL from the host). Then check `ollama list`, the base URL and the model names. Health is re-checked every 30 s (`LLM_HEALTH_INTERVAL`); **Admin → Providers → Check now** forces a check. |
| OpenAI Offline: "API key not configured" | Set `OPENAI_API_KEY` in `.env`, then run `scripts/deploy.sh up --no-build`. |
| Backend never becomes ready | `scripts/deploy.sh logs backend`. If `POSTGRES_PASSWORD` changed after the volume was created, restore the old value or run `scripts/deploy.sh up --fresh` (deletes data). |
| Everyone is logged out after a restart | `JWT_SECRET` is empty. Set a value of 32+ characters (`openssl rand -base64 48`). |
| `docker compose` not found | The scripts fall back to `docker-compose` or `podman compose` automatically. |
