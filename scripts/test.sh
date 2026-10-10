#!/usr/bin/env bash
# Runs the automated test suites. Backend integration tests use Testcontainers (Docker or Podman).
set -euo pipefail
# shellcheck source=lib/common.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib/common.sh"

usage() {
  cat <<'EOF'
Usage: scripts/test.sh [all|backend|frontend] [options]

Suites
  all        Frontend checks, then backend tests (default)
  backend    Maven verify: unit + integration tests (PostgreSQL Testcontainer, WireMock LLM servers)
  frontend   Type check, lint and Vitest

Options
  --test PATTERN   Only run matching backend test classes, e.g. --test 'ChatStreamingIT'
  --offline        Run Maven offline
  -h, --help       Show this help

No real LLM provider or API key is needed: providers are simulated with WireMock.
EOF
}

SUITE=all
if [ $# -gt 0 ]; then
  case "$1" in
    all|backend|frontend) SUITE="$1"; shift ;;
    -h|--help|help) usage; exit 0 ;;
  esac
fi

MAVEN_ARGS=(-B)
while [ $# -gt 0 ]; do
  case "$1" in
    --test) [ $# -ge 2 ] || die "--test needs a value"; MAVEN_ARGS+=("-Dtest=$2" -Dsurefire.failIfNoSpecifiedTests=false); shift 2 ;;
    --offline) MAVEN_ARGS+=(-o); shift ;;
    -h|--help) usage; exit 0 ;;
    *) die "Unknown option: $1 (see --help)" ;;
  esac
done

# Points Testcontainers at the detected engine. A Podman machine needs extra settings for Ryuk (the cleanup container).
configure_testcontainers() {
  local socket
  if ! detect_container_runtime optional; then
    warn "No container engine found: integration tests that need PostgreSQL will fail"
    return 0
  fi
  if [ -z "${DOCKER_HOST:-}" ] && [ "$CONTAINER_CLI" = podman ] && socket="$(podman_socket)"; then
    export DOCKER_HOST="unix://$socket"
  fi
  case "${DOCKER_HOST:-}" in
    *podman*)
      if podman_machine_rootful; then
        export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE="${TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE:-/run/podman/podman.sock}"
      else
        export TESTCONTAINERS_RYUK_DISABLED="${TESTCONTAINERS_RYUK_DISABLED:-true}"
      fi
      ;;
  esac
  info "Testcontainers engine: $(runtime_description)"
}

run_frontend() {
  info "Frontend: type check, lint, unit tests"
  require_command npm "install Node.js 20.19+ or 22.12+"
  cd "$ROOT_DIR/frontend"
  [ -d node_modules ] || npm ci --no-audit --no-fund
  npm run --silent typecheck
  npm run --silent lint
  npm run --silent test
  cd "$ROOT_DIR"
  success "Frontend checks passed"
}

run_backend() {
  info "Backend: mvnw verify"
  configure_testcontainers
  cd "$ROOT_DIR/backend"
  ./mvnw "${MAVEN_ARGS[@]}" verify
  cd "$ROOT_DIR"
  success "Backend tests passed (reports: backend/target/surefire-reports)"
}

case "$SUITE" in
  frontend) run_frontend ;;
  backend) run_backend ;;
  all) run_frontend; run_backend ;;
esac
