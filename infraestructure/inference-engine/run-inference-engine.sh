#!/usr/bin/env bash
set -euo pipefail

# Starts llama-swap as the single OpenAI-compatible inference endpoint.
# Spring AI should point to this endpoint and choose the backend via CHAT_MODEL.
#
# Overrides:
#   LLAMA_SWAP_BIN=llama-swap
#   LLAMA_SERVER_BIN=llama-server
#   LLAMA_SWAP_HOST=127.0.0.1
#   LLAMA_SWAP_PORT=8080
#   LLAMA_SWAP_EXTRA_ARGS="--log-level debug"
#   WARM_UP_MODELS="model-a model-b"   # set empty to disable
#   WARM_UP_TIMEOUT_SECONDS=900

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

LLAMA_SWAP_BIN="${LLAMA_SWAP_BIN:-${HOME:-}/bin/llama-swap}"
LLAMA_SERVER_BIN="${LLAMA_SERVER_BIN:-${HOME:-}/bin/llama-server}"
LLAMA_CACHE="${LLAMA_CACHE:-$HOME/.cache/llama.cpp}"
LLAMA_SWAP_HOST="${LLAMA_SWAP_HOST:-127.0.0.1}"
LLAMA_SWAP_PORT="${LLAMA_SWAP_PORT:-8080}"
CONFIG_FILE="$ROOT_DIR/config.yaml"
export INFERENCE_ENGINE_ROOT="$ROOT_DIR"
export LLAMA_SERVER_BIN
export LLAMA_CACHE

resolve_executable() {
  local candidate="$1"
  if [[ "$candidate" == */* ]]; then
    [[ -x "$candidate" ]] && printf '%s\n' "$candidate"
  else
    command -v "$candidate" 2>/dev/null || true
  fi
}

llama_server_supports_hf() {
  local candidate="$1"
  local help
  help="$($candidate --help 2>&1 || true)"
  grep -Eq -- '(^|[[:space:]])-hf([,[:space:]]|$)|--hf-repo|--hf-file' <<< "$help"
}

for cmd in curl python3; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "error: '$cmd' is required but was not found in PATH" >&2
    exit 127
  fi
done

RESOLVED_LLAMA_SWAP_BIN="$(resolve_executable "$LLAMA_SWAP_BIN")"
if [[ -z "$RESOLVED_LLAMA_SWAP_BIN" ]]; then
  RESOLVED_LLAMA_SWAP_BIN="$(command -v llama-swap 2>/dev/null || true)"
fi
if [[ -z "$RESOLVED_LLAMA_SWAP_BIN" ]]; then
  echo "error: llama-swap not found; run ./bootstrap-inference_engine.sh first" >&2
  exit 127
fi
LLAMA_SWAP_BIN="$RESOLVED_LLAMA_SWAP_BIN"

RESOLVED_LLAMA_SERVER_BIN="$(resolve_executable "$LLAMA_SERVER_BIN")"
if [[ -z "$RESOLVED_LLAMA_SERVER_BIN" ]]; then
  RESOLVED_LLAMA_SERVER_BIN="$(command -v llama-server 2>/dev/null || true)"
fi
if [[ -z "$RESOLVED_LLAMA_SERVER_BIN" ]]; then
  echo "error: llama-server not found; run ./bootstrap-inference_engine.sh first" >&2
  exit 127
fi
if ! llama_server_supports_hf "$RESOLVED_LLAMA_SERVER_BIN"; then
  echo "error: llama-server exists but does not advertise -hf/Hugging Face model loading" >&2
  echo "Rebuild llama.cpp with CURL support by running ./bootstrap-inference_engine.sh" >&2
  exit 1
fi
LLAMA_SERVER_BIN="$RESOLVED_LLAMA_SERVER_BIN"
export LLAMA_SERVER_BIN

ARGS=(
  --config "$CONFIG_FILE"
  --listen "$LLAMA_SWAP_HOST:$LLAMA_SWAP_PORT"
)

if [[ -n "${LLAMA_SWAP_EXTRA_ARGS:-}" ]]; then
  # shellcheck disable=SC2206
  EXTRA_ARGS=( ${LLAMA_SWAP_EXTRA_ARGS} )
  ARGS+=("${EXTRA_ARGS[@]}")
fi

WARM_UP_MODELS="${WARM_UP_MODELS:-AtomicChat/ornith-9b-GGUF:UD-Q4_K_XL unsloth/gemma-4-E4B-it-GGUF:IQ4_XS}"
WARM_UP_TIMEOUT_SECONDS="${WARM_UP_TIMEOUT_SECONDS:-900}"
WARM_UP_MAX_TOKENS="${WARM_UP_MAX_TOKENS:-1}"

wait_for_health() {
  local base_url="$1"
  local timeout_seconds="$2"
  local start
  start="$(date +%s)"

  until curl -fsS "$base_url/health" >/dev/null 2>&1; do
    if (( $(date +%s) - start >= timeout_seconds )); then
      echo "error: llama-swap did not become healthy within ${timeout_seconds}s" >&2
      return 1
    fi
    sleep 1
  done
}

json_escape() {
  python3 -c 'import json,sys; print(json.dumps(sys.argv[1]))' "$1"
}

warm_up_model() {
  local base_url="$1"
  local model="$2"
  local model_json
  model_json="$(json_escape "$model")"

  echo "[warm-up] loading/downloading if missing: $model"
  curl -fsS --max-time "$WARM_UP_TIMEOUT_SECONDS" \
    "$base_url/v1/chat/completions" \
    -H 'Content-Type: application/json' \
    -d "{\"model\":${model_json},\"messages\":[{\"role\":\"user\",\"content\":\"Reply OK.\"}],\"max_tokens\":${WARM_UP_MAX_TOKENS},\"stream\":false}" \
    >/dev/null
  echo "[warm-up] ready: $model"
}

run_warm_up() {
  local base_url="http://$LLAMA_SWAP_HOST:$LLAMA_SWAP_PORT"
  [[ -n "$WARM_UP_MODELS" ]] || return 0

  echo "[warm-up] waiting for llama-swap health"
  wait_for_health "$base_url" "$WARM_UP_TIMEOUT_SECONDS"

  local model
  # shellcheck disable=SC2206
  local models=( ${WARM_UP_MODELS} )
  for model in "${models[@]}"; do
    warm_up_model "$base_url" "$model"
  done
}

cat <<INFO
Starting inference engine
  config:   $CONFIG_FILE
  endpoint: http://$LLAMA_SWAP_HOST:$LLAMA_SWAP_PORT/v1
  cache:    $LLAMA_CACHE
  swap:     $LLAMA_SWAP_BIN
  server:   $LLAMA_SERVER_BIN

Spring AI env examples:
  OPENAI_BASE_URL=http://$LLAMA_SWAP_HOST:$LLAMA_SWAP_PORT/v1
  CHAT_MODEL=AtomicChat/ornith-9b-GGUF:UD-Q4_K_XL

  OPENAI_BASE_URL=http://$LLAMA_SWAP_HOST:$LLAMA_SWAP_PORT/v1
  CHAT_MODEL=unsloth/gemma-4-E4B-it-GGUF:IQ4_XS
INFO

"$LLAMA_SWAP_BIN" "${ARGS[@]}" &
LLAMA_SWAP_PID=$!

cleanup() {
  kill "$LLAMA_SWAP_PID" 2>/dev/null || true
  wait "$LLAMA_SWAP_PID" 2>/dev/null || true
}
trap cleanup INT TERM

if ! run_warm_up; then
  cleanup
  exit 1
fi

wait "$LLAMA_SWAP_PID"
