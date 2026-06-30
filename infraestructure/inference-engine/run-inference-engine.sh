#!/usr/bin/env bash
set -euo pipefail

# Starts llama-swap as the single OpenAI-compatible inference endpoint.
# Spring AI should point to this endpoint and choose the backend via CHAT_MODEL.
#
# Overrides:
#   LLAMA_SWAP_BIN=llama-swap
#   LLAMA_SWAP_HOST=127.0.0.1
#   LLAMA_SWAP_PORT=8080
#   LLAMA_SWAP_EXTRA_ARGS="--log-level debug"

LLAMA_SWAP_BIN="${LLAMA_SWAP_BIN:-${HOME:-}/bin/llama-swap}"
if [[ ! -x "$LLAMA_SWAP_BIN" ]]; then
  LLAMA_SWAP_BIN="llama-swap"
fi
LLAMA_SWAP_HOST="${LLAMA_SWAP_HOST:-127.0.0.1}"
LLAMA_SWAP_PORT="${LLAMA_SWAP_PORT:-8080}"
CONFIG_FILE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/config.yaml"

if ! command -v "$LLAMA_SWAP_BIN" >/dev/null 2>&1; then
  echo "error: '$LLAMA_SWAP_BIN' not found in PATH" >&2
  echo "Install llama-swap on the inference server, then re-run this script." >&2
  exit 127
fi

if ! command -v llama-server >/dev/null 2>&1; then
  echo "error: 'llama-server' not found in PATH" >&2
  echo "Install llama.cpp on the inference server, then re-run this script." >&2
  exit 127
fi

ARGS=(
  --config "$CONFIG_FILE"
  --listen "$LLAMA_SWAP_HOST:$LLAMA_SWAP_PORT"
)

if [[ -n "${LLAMA_SWAP_EXTRA_ARGS:-}" ]]; then
  # shellcheck disable=SC2206
  EXTRA_ARGS=( ${LLAMA_SWAP_EXTRA_ARGS} )
  ARGS+=("${EXTRA_ARGS[@]}")
fi

cat <<INFO
Starting inference engine
  config:   $CONFIG_FILE
  endpoint: http://$LLAMA_SWAP_HOST:$LLAMA_SWAP_PORT/v1

Spring AI env examples:
  OPENAI_BASE_URL=http://$LLAMA_SWAP_HOST:$LLAMA_SWAP_PORT/v1
  CHAT_MODEL=AtomicChat/ornith-9b-GGUF:UD-Q4_K_XL

  OPENAI_BASE_URL=http://$LLAMA_SWAP_HOST:$LLAMA_SWAP_PORT/v1
  CHAT_MODEL=unsloth/gemma-4-E4B-it-GGUF:IQ4_XS
INFO

exec "$LLAMA_SWAP_BIN" "${ARGS[@]}"
