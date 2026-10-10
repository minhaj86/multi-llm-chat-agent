#!/usr/bin/env bash
# Deploys the AI Workspace with Docker Compose (Docker or Podman are detected automatically).
set -euo pipefail
# shellcheck source=lib/common.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib/common.sh"

usage() {
  cat <<'EOF'
Usage: scripts/deploy.sh [command] [options]

Commands
  up         Build and start the stack, wait until it is healthy (default)
  down       Stop the stack (data is kept unless --volumes)
  restart    Restart backend and frontend
  logs       Follow logs, e.g. "scripts/deploy.sh logs backend"
  ps         Show container status

Options (up)
  --profile NAME   Also start an optional profile: qwen, llama (NVIDIA GPU), ollama, monitoring. Repeatable.
  --no-build       Start existing images without rebuilding
  --pull           Pull newer base images while building
  --fresh          Delete the database volume first (all users and conversations are lost)
  --timeout SEC    Seconds to wait for the backend to become ready (default 240)
  -y, --yes        Do not ask for confirmation

Options (down)
  --volumes        Also delete the database volume

On the first run a .env file is created from .env.example with generated secrets. If Ollama runs on this
machine, Qwen and Llama are pointed at it automatically.
EOF
}

COMMAND=up
if [ $# -gt 0 ]; then
  case "$1" in
    up|down|restart|logs|ps) COMMAND="$1"; shift ;;
    -h|--help|help) usage; exit 0 ;;
  esac
fi

PROFILES=()
BUILD=1
PULL=0
FRESH=0
VOLUMES=0
TIMEOUT=240
ARGS=()
while [ $# -gt 0 ]; do
  case "$1" in
    --profile) [ $# -ge 2 ] || die "--profile needs a value"; PROFILES+=("$2"); shift 2 ;;
    --profile=*) PROFILES+=("${1#*=}"); shift ;;
    --no-build) BUILD=0; shift ;;
    --pull) PULL=1; shift ;;
    --fresh) FRESH=1; shift ;;
    --volumes|-v) VOLUMES=1; shift ;;
    --timeout) [ $# -ge 2 ] || die "--timeout needs a value"; TIMEOUT="$2"; shift 2 ;;
    -y|--yes) ASSUME_YES=1; shift ;;
    -h|--help) usage; exit 0 ;;
    -*) die "Unknown option: $1 (see --help)" ;;
    *) [ "$COMMAND" = logs ] || die "Unexpected argument: $1 (see --help)"; ARGS+=("$1"); shift ;;
  esac
done

PROFILE_FLAGS=()
for profile in ${PROFILES[@]+"${PROFILES[@]}"}; do
  case "$profile" in
    qwen|llama|ollama|monitoring) PROFILE_FLAGS+=(--profile "$profile") ;;
    *) die "Unknown profile '$profile' (expected qwen, llama, ollama or monitoring)" ;;
  esac
done
# "down" and "ps" must also see containers of optional profiles.
ALL_PROFILE_FLAGS=()
for profile in "${ALL_PROFILES[@]}"; do ALL_PROFILE_FLAGS+=(--profile "$profile"); done

ensure_env_file() {
  local env_file="$ROOT_DIR/.env" models qwen llama
  [ -f "$env_file" ] && return 0
  require_command openssl "used to generate secrets"
  info "Creating .env from .env.example"
  cp "$ROOT_DIR/.env.example" "$env_file"
  chmod 600 "$env_file"
  set_env_value "$env_file" JWT_SECRET "$(random_secret 48)"
  set_env_value "$env_file" POSTGRES_PASSWORD "$(random_password 12)"
  set_env_value "$env_file" ADMIN_PASSWORD "Admin-$(random_password 6)"

  models="$(list_models http://127.0.0.1:11434/v1)"
  if [ -n "$models" ]; then
    qwen="$(printf '%s\n' "$models" | grep -i '^qwen' | head -n 1 || true)"
    llama="$(printf '%s\n' "$models" | grep -i 'llama' | head -n 1 || true)"
    if [ -n "$qwen" ]; then
      set_env_value "$env_file" QWEN_BASE_URL "http://host.docker.internal:11434/v1"
      set_env_value "$env_file" QWEN_MODEL "$qwen"
      set_env_value "$env_file" QWEN_ENGINE "ollama"
      set_env_value "$env_file" QWEN_MODELS "$qwen"
    fi
    if [ -n "$llama" ]; then
      set_env_value "$env_file" LLAMA_BASE_URL "http://host.docker.internal:11434/v1"
      set_env_value "$env_file" LLAMA_MODEL "$llama"
      set_env_value "$env_file" LLAMA_ENGINE "ollama"
      set_env_value "$env_file" LLAMA_MODELS "$llama"
    fi
    success "Found Ollama on this machine (Qwen: ${qwen:-none}, Llama: ${llama:-none})"
  fi
  success ".env created. Administrator: ${ADMIN_USERNAME:-admin}, password: see ADMIN_PASSWORD in .env"
}

validate_env() {
  load_env
  local secret="${JWT_SECRET:-}"
  if [ -z "$secret" ]; then
    warn "JWT_SECRET is empty: users are logged out whenever the backend restarts"
  elif [ "${#secret}" -lt 32 ]; then
    die "JWT_SECRET must be at least 32 characters (generate one with: openssl rand -base64 48)"
  fi
  [ -n "${OPENAI_API_KEY:-}" ] || warn "OPENAI_API_KEY is not set: OpenAI will be shown as offline"
  [ -n "${ADMIN_PASSWORD:-}" ] || warn "ADMIN_PASSWORD is not set: the first user who registers becomes administrator"
  return 0
}

check_ports() {
  local spec service port variable
  for spec in "frontend:${FRONTEND_PORT:-3080}" "backend:${BACKEND_PORT:-8088}" "postgres:${POSTGRES_PORT:-5434}"; do
    service="${spec%%:*}"
    port="${spec##*:}"
    if port_in_use "$port" && ! compose_service_running "$service"; then
      variable="$(printf '%s' "$service" | tr '[:lower:]' '[:upper:]')_PORT"
      if [ -f "$RUN_DIR/backend.pid" ] && [ "$service" = backend ]; then
        die "Port $port is used by the local dev backend. Stop it first: scripts/run-dev.sh stop"
      fi
      die "Port $port ($service) is already in use by another program. Free it or set $variable in .env"
    fi
  done
}

cmd_up() {
  detect_container_runtime
  ensure_env_file
  validate_env
  info "Container engine: $(runtime_description)"

  if [ "$FRESH" = 1 ]; then
    confirm "--fresh deletes the database volume (all users and conversations). Continue?" || die "Aborted"
    compose "${ALL_PROFILE_FLAGS[@]}" down --volumes --remove-orphans
  fi
  check_ports

  if [ "$BUILD" = 1 ]; then
    info "Building images (first build downloads dependencies and takes a few minutes)"
    if [ "$PULL" = 1 ]; then
      compose ${PROFILE_FLAGS[@]+"${PROFILE_FLAGS[@]}"} build --pull
    else
      compose ${PROFILE_FLAGS[@]+"${PROFILE_FLAGS[@]}"} build
    fi
  fi
  info "Starting containers${PROFILES[*]:+ (profiles: ${PROFILES[*]})}"
  compose ${PROFILE_FLAGS[@]+"${PROFILE_FLAGS[@]}"} up -d --remove-orphans
  wait_for_stack
  echo
  "$SCRIPT_DIR/status.sh" --brief || true
  printf 'Log in as %s (password: ADMIN_PASSWORD in .env)\n' "${ADMIN_USERNAME:-admin}"
}

wait_for_stack() {
  wait_for_http "http://localhost:${BACKEND_PORT:-8088}/actuator/health/readiness" "Backend" "$TIMEOUT" \
    || die "The backend is not ready. Inspect it with: scripts/deploy.sh logs backend"
  wait_for_http "http://localhost:${FRONTEND_PORT:-3080}/" "Frontend" 60 \
    || die "The frontend is not ready. Inspect it with: scripts/deploy.sh logs frontend"
}

cmd_down() {
  detect_container_runtime
  load_env
  if [ "$VOLUMES" = 1 ]; then
    confirm "--volumes deletes the database volume (all users and conversations). Continue?" || die "Aborted"
    compose "${ALL_PROFILE_FLAGS[@]}" down --volumes --remove-orphans
  else
    compose "${ALL_PROFILE_FLAGS[@]}" down --remove-orphans
  fi
  success "Stack stopped"
}

cmd_restart() {
  detect_container_runtime
  load_env
  compose restart backend frontend
  wait_for_stack
}

cmd_logs() {
  detect_container_runtime
  compose "${ALL_PROFILE_FLAGS[@]}" logs -f --tail=200 ${ARGS[@]+"${ARGS[@]}"}
}

cmd_ps() {
  detect_container_runtime
  compose "${ALL_PROFILE_FLAGS[@]}" ps
}

cd "$ROOT_DIR"
case "$COMMAND" in
  up) cmd_up ;;
  down) cmd_down ;;
  restart) cmd_restart ;;
  logs) cmd_logs ;;
  ps) cmd_ps ;;
esac
