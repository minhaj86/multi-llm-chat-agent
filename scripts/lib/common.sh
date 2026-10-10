#!/usr/bin/env bash
# Shared helpers for the AI Workspace scripts.
# Compatible with bash 3.2 (the default shell on macOS): no associative arrays, no ${var,,}, guarded empty arrays.
# Variables defined here are used by the scripts that source this file.
# shellcheck disable=SC2034

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SCRIPT_DIR="$ROOT_DIR/scripts"
RUN_DIR="$ROOT_DIR/.run"
COMPOSE_FILE="$ROOT_DIR/docker-compose.yml"
ALL_PROFILES=(qwen llama ollama monitoring)

if [ -t 1 ] && [ -z "${NO_COLOR:-}" ]; then
  C_RESET=$'\033[0m'; C_BOLD=$'\033[1m'; C_DIM=$'\033[2m'
  C_RED=$'\033[31m'; C_GREEN=$'\033[32m'; C_YELLOW=$'\033[33m'; C_BLUE=$'\033[34m'
else
  C_RESET=''; C_BOLD=''; C_DIM=''; C_RED=''; C_GREEN=''; C_YELLOW=''; C_BLUE=''
fi

info()    { printf '%s==>%s %s\n' "$C_BLUE" "$C_RESET" "$*"; }
success() { printf '%s✔%s %s\n' "$C_GREEN" "$C_RESET" "$*"; }
warn()    { printf '%s!%s %s\n' "$C_YELLOW" "$C_RESET" "$*" >&2; }
error()   { printf '%s✖%s %s\n' "$C_RED" "$C_RESET" "$*" >&2; }
die()     { error "$*"; exit 1; }

# Asks a yes/no question; honours ASSUME_YES=1 and refuses to guess in non-interactive shells.
confirm() {
  [ "${ASSUME_YES:-0}" = 1 ] && return 0
  [ -t 0 ] || die "Confirmation required (re-run with --yes): $1"
  local answer
  read -r -p "$1 [y/N] " answer
  case "$answer" in y|Y|yes|YES) return 0 ;; *) return 1 ;; esac
}

# Loads KEY=VALUE pairs from .env without executing it as shell code.
# Variables already present in the environment win (same precedence as Docker Compose).
load_env() {
  local file="${1:-$ROOT_DIR/.env}" line key value
  [ -f "$file" ] || return 0
  while IFS= read -r line || [ -n "$line" ]; do
    line="${line%$'\r'}"
    case "$line" in ''|'#'*) continue ;; *=*) ;; *) continue ;; esac
    key="${line%%=*}"
    value="${line#*=}"
    key="${key#export }"
    key="$(printf '%s' "$key" | tr -d '[:space:]')"
    [[ "$key" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || continue
    case "$value" in
      \"*\") value="${value#\"}"; value="${value%\"}" ;;
      \'*\') value="${value#\'}"; value="${value%\'}" ;;
    esac
    if [ -z "${!key+x}" ]; then
      export "$key=$value"
    fi
  done < "$file"
}

# Sets or replaces KEY=VALUE in an env file (portable: no sed -i).
set_env_value() {
  local file="$1" key="$2" value="$3" tmp
  tmp="$(mktemp "${file}.XXXXXX")"
  awk -v k="$key" -v v="$value" '
    BEGIN { done = 0 }
    index($0, k "=") == 1 { if (!done) { print k "=" v; done = 1 }; next }
    { print }
    END { if (!done) print k "=" v }
  ' "$file" > "$tmp"
  mv "$tmp" "$file"
}

random_secret()   { openssl rand -base64 "${1:-48}" | tr -d '\n'; }
random_password() { openssl rand -hex "${1:-8}"; }

# ---------------------------------------------------------------------------------------------------------------------
# Container engine (Docker or Podman) and Compose detection
# ---------------------------------------------------------------------------------------------------------------------
CONTAINER_CLI=""
COMPOSE_CMD=()

# Prints the API socket of a running Podman machine (macOS) or Podman service (Linux).
podman_socket() {
  local candidates=() path temp_dir
  if command -v podman >/dev/null 2>&1; then
    path="$(podman machine inspect --format '{{.ConnectionInfo.PodmanSocket.Path}}' 2>/dev/null | head -n 1 || true)"
    if [ -n "$path" ]; then
      candidates+=("$path")
      # podman derives the path from $TMPDIR; fall back to the real per-user temp dir if TMPDIR is overridden.
      temp_dir="$(getconf DARWIN_USER_TEMP_DIR 2>/dev/null || true)"
      [ -n "$temp_dir" ] && candidates+=("${temp_dir%/}/podman/$(basename "$path")")
    fi
  fi
  [ -n "${XDG_RUNTIME_DIR:-}" ] && candidates+=("$XDG_RUNTIME_DIR/podman/podman.sock")
  candidates+=("/run/podman/podman.sock")
  for path in "${candidates[@]}"; do
    if [ -S "$path" ]; then
      printf '%s' "$path"
      return 0
    fi
  done
  return 1
}

podman_machine_rootful() {
  command -v podman >/dev/null 2>&1 && [ "$(podman machine inspect --format '{{.Rootful}}' 2>/dev/null | head -n 1)" = "true" ]
}

# Finds a working container engine and compose command. Sets CONTAINER_CLI and COMPOSE_CMD.
# With "optional" as first argument it returns 1 instead of exiting when nothing is available.
detect_container_runtime() {
  local mode="${1:-required}" socket
  if [ -n "$CONTAINER_CLI" ]; then
    return 0
  fi
  if command -v docker >/dev/null 2>&1; then
    if docker info >/dev/null 2>&1; then
      CONTAINER_CLI=docker
    elif socket="$(podman_socket)"; then
      export DOCKER_HOST="unix://$socket"
      docker info >/dev/null 2>&1 && CONTAINER_CLI=docker
    fi
  fi
  if [ -z "$CONTAINER_CLI" ] && command -v podman >/dev/null 2>&1 && podman info >/dev/null 2>&1; then
    CONTAINER_CLI=podman
  fi
  if [ -z "$CONTAINER_CLI" ]; then
    [ "$mode" = optional ] && return 1
    die "No running container engine found. Start Docker Desktop, or run 'podman machine start'."
  fi
  if [ "$CONTAINER_CLI" = docker ] && docker compose version >/dev/null 2>&1; then
    COMPOSE_CMD=(docker compose)
  elif command -v docker-compose >/dev/null 2>&1; then
    COMPOSE_CMD=(docker-compose)
  elif command -v podman >/dev/null 2>&1 && podman compose version >/dev/null 2>&1; then
    COMPOSE_CMD=(podman compose)
  else
    [ "$mode" = optional ] && return 1
    die "Docker Compose not found. Install the compose plugin, the standalone docker-compose binary or podman-compose."
  fi
  return 0
}

compose() {
  "${COMPOSE_CMD[@]}" -f "$COMPOSE_FILE" "$@"
}

compose_service_running() {
  compose ps --status running --services 2>/dev/null | grep -qx "$1"
}

runtime_description() {
  printf '%s via %s' "$CONTAINER_CLI" "${COMPOSE_CMD[*]}"
  [ -n "${DOCKER_HOST:-}" ] && printf ' (DOCKER_HOST=%s)' "$DOCKER_HOST"
  return 0
}

# ---------------------------------------------------------------------------------------------------------------------
# Network helpers
# ---------------------------------------------------------------------------------------------------------------------

# Prints the HTTP status of a URL ("000" when unreachable).
http_status() {
  curl -s -o /dev/null -w '%{http_code}' --max-time "${2:-5}" "$1" 2>/dev/null || true
}

port_in_use() {
  (exec 3<>"/dev/tcp/127.0.0.1/$1") 2>/dev/null
}

wait_for_http() {
  local url="$1" name="$2" timeout="${3:-120}" deadline status
  deadline=$(( $(date +%s) + timeout ))
  while :; do
    status="$(http_status "$url" 3)"
    case "$status" in
      2??) success "$name is up ($url)"; return 0 ;;
    esac
    if [ "$(date +%s)" -ge "$deadline" ]; then
      error "$name did not become ready within ${timeout}s ($url, last HTTP status $status)"
      return 1
    fi
    sleep 2
  done
}

# Translates a base URL as seen from inside the compose network into one usable from this machine.
host_url() {
  local url="$1"
  case "$url" in
    *://host.docker.internal*) printf '%s' "${url/host.docker.internal/localhost}" ;;
    *://host.containers.internal*) printf '%s' "${url/host.containers.internal/localhost}" ;;
    http://qwen:8000*) printf '%s' "${url/qwen:8000/localhost:8001}" ;;
    http://llama:8000*) printf '%s' "${url/llama:8000/localhost:8002}" ;;
    http://ollama:11434*) printf '%s' "${url/ollama:11434/localhost:${OLLAMA_PORT:-11435}}" ;;
    *) printf '%s' "$url" ;;
  esac
}

# Lists model ids of an OpenAI-compatible server (one per line).
list_models() {
  curl -s --max-time "${2:-3}" "${1%/}/models" 2>/dev/null \
    | python3 -c 'import json,sys
try:
    print("\n".join(m.get("id", "") for m in json.load(sys.stdin).get("data", [])))
except Exception:
    pass' 2>/dev/null || true
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || die "'$1' is required${2:+ ($2)}"
}
