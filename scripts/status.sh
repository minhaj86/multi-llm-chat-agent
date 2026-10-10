#!/usr/bin/env bash
# Health overview of the AI Workspace: containers, dev processes, endpoints, database, LLM providers and model servers.
set -uo pipefail
# shellcheck source=lib/common.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib/common.sh"

usage() {
  cat <<'EOF'
Usage: scripts/status.sh [options]

Options
  --brief           Only endpoints and providers
  --json            Machine-readable output
  --watch [SEC]     Refresh every SEC seconds (default 5) until Ctrl+C
  -h, --help        Show this help

Exit code 0 when the application is usable (backend, database and a frontend are up), 1 otherwise.
Provider details use ADMIN_USERNAME/ADMIN_PASSWORD from .env when available, otherwise the public metrics.
EOF
}

BRIEF=0
JSON=0
WATCH=0
INTERVAL=5
while [ $# -gt 0 ]; do
  case "$1" in
    --brief) BRIEF=1; shift ;;
    --json) JSON=1; shift ;;
    --watch)
      WATCH=1
      if [ $# -ge 2 ] && [[ "$2" =~ ^[0-9]+$ ]]; then INTERVAL="$2"; shift; fi
      shift ;;
    -h|--help) usage; exit 0 ;;
    *) die "Unknown option: $1 (see --help)" ;;
  esac
done

load_env
BACKEND_URL="http://localhost:${BACKEND_PORT:-8088}"
FRONTEND_URL="http://localhost:${FRONTEND_PORT:-3080}"
DEV_FRONTEND_URL="http://localhost:5173"
TOKEN=""
RESULTS=()
CORE_BACKEND=0
CORE_DATABASE=0
CORE_FRONTEND=0

add() {
  local detail="${4//|//}"
  RESULTS+=("$1|$2|$3|$detail")
}

pid_alive() {
  local file="$RUN_DIR/$1.pid"
  [ -f "$file" ] && kill -0 "$(cat "$file")" 2>/dev/null
}

check_platform() {
  local flags=() profile line service state health
  if ! detect_container_runtime optional; then
    add "Platform" "Container engine" warn "not available (Docker/Podman not running)"
    return
  fi
  add "Platform" "Container engine" ok "$(runtime_description)"
  for profile in "${ALL_PROFILES[@]}"; do flags+=(--profile "$profile"); done
  local states
  states="$(compose "${flags[@]}" ps --all --format json 2>/dev/null | python3 -c '
import json, sys
raw = sys.stdin.read().strip()
items = json.loads(raw) if raw.startswith("[") else [json.loads(l) for l in raw.splitlines() if l.strip()]
for c in items:
    print(c.get("Service", "?"), c.get("State", "?"), c.get("Health") or "-")
' 2>/dev/null)"
  if [ -z "$states" ]; then
    add "Containers" "ai-workspace" info "no containers (start with scripts/deploy.sh)"
    return
  fi
  while IFS=' ' read -r service state health; do
    [ -n "$service" ] || continue
    if [ "$state" = running ] && [ "$health" = "-" ]; then
      add "Containers" "$service" ok "running"
    elif [ "$state" = running ] && [ "$health" = healthy ]; then
      add "Containers" "$service" ok "running (healthy)"
    elif [ "$state" = running ]; then
      add "Containers" "$service" warn "running ($health)"
    elif pid_alive "$service"; then
      add "Containers" "$service" info "$state (replaced by the dev process)"
    else
      add "Containers" "$service" fail "$state"
    fi
  done <<< "$states"
}

check_dev_processes() {
  local name
  for name in backend frontend; do
    if [ -f "$RUN_DIR/$name.pid" ]; then
      if pid_alive "$name"; then
        add "Dev processes" "$name" ok "pid $(cat "$RUN_DIR/$name.pid") (logs: .run/$name.log)"
      else
        add "Dev processes" "$name" fail "not running (stale pid file, see .run/$name.log)"
      fi
    fi
  done
}

check_endpoints() {
  local body status readiness
  body="$(curl -s --max-time 5 "$BACKEND_URL/actuator/health" 2>/dev/null || true)"
  status="$(printf '%s' "$body" | python3 -c 'import json,sys
try: print(json.load(sys.stdin).get("status", "?"))
except Exception: print("unreachable")' 2>/dev/null)"
  readiness="$(http_status "$BACKEND_URL/actuator/health/readiness" 5)"
  if [ "$status" = UP ] && [ "$readiness" = 200 ]; then
    add "Endpoints" "Backend" ok "$BACKEND_URL (health UP, ready)"
    CORE_BACKEND=1
  elif [ "$status" = UP ]; then
    add "Endpoints" "Backend" warn "$BACKEND_URL (health UP, readiness HTTP $readiness)"
  else
    add "Endpoints" "Backend" fail "$BACKEND_URL ($status)"
  fi

  if [ "$(http_status "$FRONTEND_URL/" 5)" = 200 ]; then
    if [ "$(http_status "$FRONTEND_URL/api/auth/config" 5)" = 200 ]; then
      add "Endpoints" "Frontend" ok "$FRONTEND_URL (nginx, /api proxy OK)"
      CORE_FRONTEND=1
    else
      add "Endpoints" "Frontend" warn "$FRONTEND_URL (page OK, /api proxy failing)"
    fi
  elif ! pid_alive frontend; then
    add "Endpoints" "Frontend" fail "$FRONTEND_URL not reachable"
  fi
  if pid_alive frontend || port_in_use 5173; then
    if [ "$(http_status "$DEV_FRONTEND_URL/" 5)" = 200 ]; then
      add "Endpoints" "Frontend (dev)" ok "$DEV_FRONTEND_URL (Vite)"
      CORE_FRONTEND=1
    else
      add "Endpoints" "Frontend (dev)" fail "$DEV_FRONTEND_URL not reachable"
    fi
  fi

  if port_in_use "${POSTGRES_PORT:-5434}"; then
    add "Endpoints" "PostgreSQL" ok "localhost:${POSTGRES_PORT:-5434}"
    CORE_DATABASE=1
  else
    add "Endpoints" "PostgreSQL" fail "localhost:${POSTGRES_PORT:-5434} not reachable"
  fi
}

login() {
  [ -n "${ADMIN_PASSWORD:-}" ] || return 1
  local payload
  payload="$(python3 -c 'import json, os
print(json.dumps({"login": os.environ.get("ADMIN_USERNAME") or "admin", "password": os.environ["ADMIN_PASSWORD"]}))')"
  curl -s --max-time 5 -X POST "$BACKEND_URL/api/auth/login" -H 'Content-Type: application/json' -d "$payload" \
    | python3 -c 'import json,sys
try: print(json.load(sys.stdin).get("accessToken", ""))
except Exception: print("")' 2>/dev/null
}

check_providers() {
  [ "$CORE_BACKEND" = 1 ] || { add "LLM providers" "providers" warn "backend not reachable"; return; }
  [ -n "$TOKEN" ] || TOKEN="$(login || true)"
  local lines id name enabled available status message model kind
  # Fields are separated by the ASCII unit separator: unlike tabs it is not whitespace, so empty fields survive "read".
  if [ -n "$TOKEN" ]; then
    lines="$(curl -s --max-time 8 -H "Authorization: Bearer $TOKEN" "$BACKEND_URL/api/providers" | python3 -c '
import json, sys
for p in json.load(sys.stdin):
    fields = [p["id"], p["name"], str(p["enabled"]).lower(), str(p["available"]).lower(), p["status"],
              p.get("statusMessage") or "", p.get("defaultModel") or "", p.get("kind") or ""]
    print("\x1f".join(f.replace("\x1f", " ").replace("\n", " ") for f in fields))
' 2>/dev/null)"
    if [ -n "$lines" ]; then
      while IFS=$'\x1f' read -r id name enabled available status message model kind; do
        [ -n "$id" ] || continue
        if [ "$enabled" != true ]; then
          add "LLM providers" "$name" info "disabled"
        elif [ "$available" = true ] && [ "$status" = UP ]; then
          add "LLM providers" "$name" ok "Online · $model · $(printf '%s' "$kind" | tr '[:upper:]' '[:lower:]')"
        elif [ "$available" = true ]; then
          add "LLM providers" "$name" warn "$status · $model · $message"
        else
          add "LLM providers" "$name" warn "Offline · ${message:-$status}"
        fi
      done <<< "$lines"
      return
    fi
    TOKEN=""
  fi
  lines="$(curl -s --max-time 5 "$BACKEND_URL/actuator/prometheus" 2>/dev/null | grep '^llm_provider_up{' || true)"
  if [ -z "$lines" ]; then
    add "LLM providers" "providers" warn "no data (set ADMIN_PASSWORD in .env for details)"
    return
  fi
  while IFS= read -r line; do
    id="$(printf '%s' "$line" | sed -E 's/.*provider="([^"]+)".*/\1/')"
    case "$line" in
      *" 1.0"|*" 1") add "LLM providers" "$id" ok "Online" ;;
      *) add "LLM providers" "$id" warn "Offline" ;;
    esac
  done <<< "$lines"
}

check_model_servers() {
  local entry label url models count
  for entry in "Qwen|${QWEN_BASE_URL:-}" "Llama|${LLAMA_BASE_URL:-}"; do
    label="${entry%%|*}"
    url="${entry#*|}"
    [ -n "$url" ] || continue
    url="$(host_url "$url")"
    models="$(list_models "$url" 3)"
    if [ -n "$models" ]; then
      count="$(printf '%s\n' "$models" | grep -c .)"
      add "Model servers" "$label" ok "$url · $count model(s): $(printf '%s' "$models" | head -n 4 | paste -sd ',' - | sed 's/,/, /g')"
    else
      add "Model servers" "$label" warn "$url not reachable from this machine"
    fi
  done
  if [ -n "${OPENAI_API_KEY:-}" ]; then
    add "Model servers" "OpenAI" ok "API key configured"
  else
    add "Model servers" "OpenAI" info "no OPENAI_API_KEY in .env"
  fi
}

run_checks() {
  RESULTS=()
  CORE_BACKEND=0
  CORE_DATABASE=0
  CORE_FRONTEND=0
  if [ "$BRIEF" = 0 ]; then
    check_platform
    check_dev_processes
  fi
  check_endpoints
  check_providers
  [ "$BRIEF" = 0 ] && check_model_servers
  return 0
}

healthy() {
  [ "$CORE_BACKEND" = 1 ] && [ "$CORE_DATABASE" = 1 ] && [ "$CORE_FRONTEND" = 1 ]
}

render_text() {
  local current="" entry group name state detail symbol color url
  for entry in "${RESULTS[@]}"; do
    IFS='|' read -r group name state detail <<< "$entry"
    if [ "$group" != "$current" ]; then
      printf '%s%s%s\n' "$C_BOLD" "$group" "$C_RESET"
      current="$group"
    fi
    case "$state" in
      ok) symbol="✔"; color="$C_GREEN" ;;
      warn) symbol="!"; color="$C_YELLOW" ;;
      fail) symbol="✖"; color="$C_RED" ;;
      *) symbol="•"; color="$C_DIM" ;;
    esac
    printf '  %s%s%s %-18s %s\n' "$color" "$symbol" "$C_RESET" "$name" "$detail"
  done
  url="$FRONTEND_URL"
  if [ "$(http_status "$FRONTEND_URL/" 3)" != 200 ] && pid_alive frontend; then url="$DEV_FRONTEND_URL"; fi
  if healthy; then
    printf '\n%s✔ AI Workspace is up:%s %s\n' "$C_GREEN" "$C_RESET" "$url"
  else
    printf '\n%s✖ AI Workspace is not fully available%s (start it with scripts/deploy.sh or scripts/run-dev.sh start)\n' \
      "$C_RED" "$C_RESET"
  fi
}

render_json() {
  local is_healthy=false
  healthy && is_healthy=true
  printf '%s\n' ${RESULTS[@]+"${RESULTS[@]}"} | IS_HEALTHY="$is_healthy" python3 -c '
import json, os, sys
checks = []
for line in sys.stdin.read().splitlines():
    if line:
        group, name, state, detail = (line.split("|", 3) + ["", "", "", ""])[:4]
        checks.append({"group": group, "name": name, "state": state, "detail": detail})
print(json.dumps({"healthy": os.environ["IS_HEALTHY"] == "true", "checks": checks}, indent=2))
'
}

render() {
  if [ "$JSON" = 1 ]; then render_json; else render_text; fi
}

if [ "$WATCH" = 1 ]; then
  trap 'exit 0' INT TERM
  while :; do
    run_checks
    output="$(render)"
    clear 2>/dev/null || true
    printf '%sAI Workspace status%s  %s(every %ss, Ctrl+C to stop)%s\n\n%s\n' "$C_BOLD" "$C_RESET" "$C_DIM" "$INTERVAL" \
      "$C_RESET" "$output"
    sleep "$INTERVAL"
  done
fi

run_checks
render
healthy
