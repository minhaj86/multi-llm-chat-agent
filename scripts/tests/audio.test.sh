#!/usr/bin/env bash
# Startup routing checks only: no containers, downloads, credentials or inference.
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/lib/common.sh"

assert_equal() {
  [ "$1" = "$2" ] || { printf 'Expected <%s>, got <%s>\n' "$2" "$1" >&2; exit 1; }
}

unset AUDIO_TRANSCRIPTION_ENABLED AUDIO_TRANSCRIPTION_BASE_URL FASTER_WHISPER_PORT FASTER_WHISPER_STARTUP_TIMEOUT
if uses_bundled_audio; then exit 1; fi
AUDIO_TRANSCRIPTION_ENABLED=true
AUDIO_TRANSCRIPTION_BASE_URL=https://speech.example/v1
if uses_bundled_audio; then exit 1; fi
assert_equal "$(host_url "$AUDIO_TRANSCRIPTION_BASE_URL")" https://speech.example/v1

AUDIO_TRANSCRIPTION_BASE_URL=http://faster-whisper:8000/v1
uses_bundled_audio
assert_equal "$(host_url "$AUDIO_TRANSCRIPTION_BASE_URL")" http://localhost:8003/v1
FASTER_WHISPER_PORT=18003
FASTER_WHISPER_STARTUP_TIMEOUT=42
assert_equal "$(host_url "$AUDIO_TRANSCRIPTION_BASE_URL")" http://localhost:18003/v1

CALLS=()
detect_container_runtime() { :; }
compose() { CALLS+=("compose $*"); }
wait_for_http() { CALLS+=("wait $*"); }
start_audio_service
assert_equal "${#CALLS[@]}" 2
assert_equal "${CALLS[0]}" 'compose --profile audio up -d faster-whisper'
assert_equal "${CALLS[1]}" 'wait http://localhost:18003/health faster-whisper 42'

# An external service or disabled capture must never start the bundled model server.
CALLS=()
AUDIO_TRANSCRIPTION_BASE_URL=http://faster-whisper:80001/v1
assert_equal "$(host_url "$AUDIO_TRANSCRIPTION_BASE_URL")" "$AUDIO_TRANSCRIPTION_BASE_URL"
start_audio_service
AUDIO_TRANSCRIPTION_BASE_URL=http://faster-whisper:8000/v1
AUDIO_TRANSCRIPTION_ENABLED=false
start_audio_service
assert_equal "${#CALLS[@]}" 0

AUDIO_TRANSCRIPTION_ENABLED=true
wait_for_http() { return 1; }
if (wait_for_audio_service >/dev/null 2>&1); then
  printf 'A failed readiness check must fail startup\n' >&2
  exit 1
fi
printf 'Audio startup and URL routing checks passed\n'
