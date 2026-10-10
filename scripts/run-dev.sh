#!/usr/bin/env bash
# Development mode: PostgreSQL in a container, Spring Boot backend and Vite dev server (hot reload) on this machine.
set -euo pipefail
# shellcheck source=lib/common.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib/common.sh"

usage() {
  cat <<'EOF'
Usage: scripts/run-dev.sh [command] [options]

Commands
  start      Start PostgreSQL (container), backend (java -jar) and frontend (Vite) (default)
  stop       Stop backend and frontend
  restart    Stop, rebuild and start again
  status     Show the status of all components
  logs       Follow logs: "logs" (both), "logs backend" or "logs frontend"

Options
  --skip-build    Reuse the existing backend jar instead of rebuilding it
  --with-db       (stop) Also stop the PostgreSQL container
  --timeout SEC   Seconds to wait for the backend (default 180)
  -y, --yes       Do not ask before stopping the backend/frontend containers of scripts/deploy.sh

URLs: frontend http://localhost:5173 (proxies /api to the backend), backend http://localhost:8088.
Configuration comes from .env; container hostnames (host.docker.internal, qwen, llama) are translated to localhost.
Process ids and logs are kept in .run/.
EOF
}

COMMAND=start
if [ $# -gt 0 ]; then
  case "$1" in
    start|stop|restart|status|logs) COMMAND="$1"; shift ;;
    -h|--help|help) usage; exit 0 ;;
  esac
fi

SKIP_BUILD=0
WITH_DB=0
TIMEOUT=180
ARGS=()
while [ $# -gt 0 ]; do
  case "$1" in
    --skip-build) SKIP_BUILD=1; shift ;;
    --with-db) WITH_DB=1; shift ;;
    --timeout) [ $# -ge 2 ] || die "--timeout needs a value"; TIMEOUT="$2"; shift 2 ;;
    -y|--yes) ASSUME_YES=1; shift ;;
    -h|--help) usage; exit 0 ;;
    -*) die "Unknown option: $1 (see --help)" ;;
    *) [ "$COMMAND" = logs ] || die "Unexpected argument: $1 (see --help)"; ARGS+=("$1"); shift ;;
  esac
done

BACKEND_JAR="$ROOT_DIR/backend/target/ai-chat-backend.jar"
DEV_BACKEND_PORT="${DEV_BACKEND_PORT:-8088}"
DEV_FRONTEND_PORT="${DEV_FRONTEND_PORT:-5173}"

pid_of() {
  local file="$RUN_DIR/$1.pid" pid
  [ -f "$file" ] || return 1
  pid="$(cat "$file")"
  kill -0 "$pid" 2>/dev/null || return 1
  printf '%s' "$pid"
}

java_command() {
  if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    printf '%s' "$JAVA_HOME/bin/java"
  else
    command -v java || die "Java 21+ is required (set JAVA_HOME or install a JDK)"
  fi
}

check_java() {
  local java version major
  java="$(java_command)"
  version="$("$java" -version 2>&1 | awk -F'"' '/version/ { print $2; exit }')"
  major="${version%%.*}"
  [ "$major" = 1 ] && major="$(printf '%s' "$version" | cut -d. -f2)"
  [[ "$major" =~ ^[0-9]+$ ]] && [ "$major" -ge 21 ] || die "Java 21+ is required, found '${version:-unknown}' ($java)"
}

# The Docker backend and the dev backend share port 8088, so the backend/frontend containers must be stopped.
stop_conflicting_containers() {
  detect_container_runtime
  if compose_service_running backend || compose_service_running frontend; then
    confirm "The Docker backend/frontend containers are running and use the same ports. Stop them (data is kept)?" \
      || die "Aborted. Stop them with: scripts/deploy.sh down"
    compose stop backend frontend
  fi
}

start_database() {
  detect_container_runtime
  if compose_service_running postgres; then
    success "PostgreSQL is running (localhost:${POSTGRES_PORT:-5434})"
    return
  fi
  if port_in_use "${POSTGRES_PORT:-5434}"; then
    die "Port ${POSTGRES_PORT:-5434} is used by another program. Set POSTGRES_PORT in .env"
  fi
  info "Starting PostgreSQL container"
  compose up -d postgres
  local deadline=$(( $(date +%s) + 60 ))
  until compose exec -T postgres pg_isready -q -U "${POSTGRES_USER:-aichat}" -d "${POSTGRES_DB:-aichat}" 2>/dev/null; do
    [ "$(date +%s)" -lt "$deadline" ] || die "PostgreSQL did not become ready within 60s (scripts/deploy.sh logs postgres)"
    sleep 2
  done
  success "PostgreSQL is ready (localhost:${POSTGRES_PORT:-5434})"
}

build_backend() {
  if [ "$SKIP_BUILD" = 1 ] && [ -f "$BACKEND_JAR" ]; then
    info "Reusing $(basename "$BACKEND_JAR") (--skip-build)"
    return
  fi
  info "Building backend (tests skipped; run scripts/test.sh for them)"
  (cd "$ROOT_DIR/backend" && ./mvnw -B -q -DskipTests package)
  success "Backend built"
}

start_backend() {
  local pid
  if pid="$(pid_of backend)"; then
    success "Backend already running (pid $pid)"
    return
  fi
  port_in_use "$DEV_BACKEND_PORT" && die "Port $DEV_BACKEND_PORT is already in use. Set DEV_BACKEND_PORT to use another port"
  check_java
  build_backend
  info "Starting backend on http://localhost:$DEV_BACKEND_PORT (log: .run/backend.log)"
  local java_opts=()
  read -r -a java_opts <<< "${BACKEND_JAVA_DEV_OPTS:-}"
  # Variables from .env are exported by load_env; only values that differ outside Docker are overridden here.
  DB_URL="jdbc:postgresql://localhost:${POSTGRES_PORT:-5434}/${POSTGRES_DB:-aichat}" \
  DB_USERNAME="${POSTGRES_USER:-aichat}" \
  DB_PASSWORD="${POSTGRES_PASSWORD:-aichat}" \
  SERVER_PORT="$DEV_BACKEND_PORT" \
  SERVER_ADDRESS=127.0.0.1 \
  QWEN_BASE_URL="$(host_url "${QWEN_BASE_URL:-http://localhost:8001/v1}")" \
  LLAMA_BASE_URL="$(host_url "${LLAMA_BASE_URL:-http://localhost:8002/v1}")" \
    nohup "$(java_command)" ${java_opts[@]+"${java_opts[@]}"} -jar "$BACKEND_JAR" > "$RUN_DIR/backend.log" 2>&1 &
  echo $! > "$RUN_DIR/backend.pid"
}

start_frontend() {
  local pid
  if pid="$(pid_of frontend)"; then
    success "Frontend already running (pid $pid)"
    return
  fi
  port_in_use "$DEV_FRONTEND_PORT" && die "Port $DEV_FRONTEND_PORT is already in use. Set DEV_FRONTEND_PORT to use another port"
  require_command node "install Node.js 20.19+ or 22.12+"
  if [ ! -d "$ROOT_DIR/frontend/node_modules" ]; then
    info "Installing frontend dependencies"
    (cd "$ROOT_DIR/frontend" && npm ci --no-audit --no-fund)
  fi
  info "Starting frontend on http://localhost:$DEV_FRONTEND_PORT (log: .run/frontend.log)"
  cd "$ROOT_DIR/frontend"
  VITE_PROXY_TARGET="http://localhost:$DEV_BACKEND_PORT" \
    nohup node node_modules/vite/bin/vite.js --port "$DEV_FRONTEND_PORT" --strictPort > "$RUN_DIR/frontend.log" 2>&1 &
  echo $! > "$RUN_DIR/frontend.pid"
  cd "$ROOT_DIR"
}

wait_or_show_log() {
  local name="$1" url="$2" timeout="$3"
  if ! wait_for_http "$url" "$name" "$timeout"; then
    if ! pid_of "$(printf '%s' "$name" | tr '[:upper:]' '[:lower:]')" >/dev/null; then
      error "$name exited. Last log lines:"
    fi
    tail -n 30 "$RUN_DIR/$(printf '%s' "$name" | tr '[:upper:]' '[:lower:]').log" >&2 || true
    return 1
  fi
}

cmd_start() {
  mkdir -p "$RUN_DIR"
  load_env
  if [ -z "$(pid_of backend || true)" ] || [ -z "$(pid_of frontend || true)" ]; then
    stop_conflicting_containers
  fi
  start_database
  start_backend
  start_frontend
  wait_or_show_log Backend "http://localhost:$DEV_BACKEND_PORT/actuator/health/readiness" "$TIMEOUT"
  wait_or_show_log Frontend "http://localhost:$DEV_FRONTEND_PORT/" 60
  printf '\n%sDevelopment stack is running:%s http://localhost:%s\n' "$C_BOLD" "$C_RESET" "$DEV_FRONTEND_PORT"
  printf 'Logs: scripts/run-dev.sh logs   Stop: scripts/run-dev.sh stop\n'
}

stop_process() {
  local name="$1" file="$RUN_DIR/$1.pid" pid waited=0
  if ! pid="$(pid_of "$name")"; then
    rm -f "$file"
    info "$name is not running"
    return 0
  fi
  kill "$pid" 2>/dev/null || true
  while kill -0 "$pid" 2>/dev/null; do
    if [ "$waited" -ge 30 ]; then
      warn "$name (pid $pid) did not stop within 15s, forcing"
      kill -9 "$pid" 2>/dev/null || true
      break
    fi
    sleep 0.5
    waited=$((waited + 1))
  done
  rm -f "$file"
  success "$name stopped"
}

cmd_stop() {
  stop_process frontend
  stop_process backend
  if [ "$WITH_DB" = 1 ]; then
    detect_container_runtime
    load_env
    compose stop postgres
    success "PostgreSQL stopped"
  else
    info "PostgreSQL keeps running (stop it with --with-db or scripts/deploy.sh down)"
  fi
}

cmd_logs() {
  local files=() name
  if [ ${#ARGS[@]} -eq 0 ]; then ARGS=(backend frontend); fi
  for name in "${ARGS[@]}"; do
    case "$name" in
      backend|frontend) [ -f "$RUN_DIR/$name.log" ] && files+=("$RUN_DIR/$name.log") ;;
      *) die "Unknown log '$name' (expected backend or frontend)" ;;
    esac
  done
  [ ${#files[@]} -gt 0 ] || die "No logs yet. Start the stack with scripts/run-dev.sh start"
  tail -n 100 -f "${files[@]}"
}

cd "$ROOT_DIR"
case "$COMMAND" in
  start) cmd_start ;;
  stop) cmd_stop ;;
  restart) cmd_stop; cmd_start ;;
  status) exec "$SCRIPT_DIR/status.sh" ;;
  logs) cmd_logs ;;
esac
