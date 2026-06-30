#!/usr/bin/env bash
set -euo pipefail

# Adaptive llama-server launcher used by llama-swap.
# It uses GPU offload when enough VRAM is available, otherwise falls back to CPU.
#
# Required:
#   MODEL=repo/model[:quant]
#   PORT=5800
#
# Positional usage:
#   ./start-llama-server.sh <model> <port> [min-free-vram-mb] [context-tokens]
#
# Optional:
#   MIN_FREE_VRAM_MB=4096
#   LLAMA_SERVER_BIN=llama-server
#   LLAMA_CONTEXT=8192
#   LLAMA_PARALLEL=1
#   LLAMA_BATCH_SIZE=512
#   LLAMA_UBATCH_SIZE=512
#   LLAMA_CACHE_TYPE_K=f16
#   LLAMA_CACHE_TYPE_V=f16
#   LLAMA_FLASH_ATTENTION=on|off|auto
#   LLAMA_EXTRA_ARGS="..."

MODEL="${MODEL:-${1:-}}"
PORT="${PORT:-${2:-}}"
MIN_FREE_VRAM_MB="${MIN_FREE_VRAM_MB:-${3:-4096}}"
LLAMA_CONTEXT="${LLAMA_CONTEXT:-${4:-8192}}"

if [[ -z "$MODEL" || -z "$PORT" ]]; then
  echo "usage: MODEL=<model> PORT=<port> $0" >&2
  echo "   or: $0 <model> <port> [min-free-vram-mb]" >&2
  exit 2
fi
LLAMA_SERVER_BIN="${LLAMA_SERVER_BIN:-llama-server}"
HOST="${HOST:-127.0.0.1}"
LLAMA_PARALLEL="${LLAMA_PARALLEL:-1}"
LLAMA_BATCH_SIZE="${LLAMA_BATCH_SIZE:-512}"
LLAMA_UBATCH_SIZE="${LLAMA_UBATCH_SIZE:-512}"
LLAMA_CACHE_TYPE_K="${LLAMA_CACHE_TYPE_K:-f16}"
LLAMA_CACHE_TYPE_V="${LLAMA_CACHE_TYPE_V:-f16}"
LLAMA_FLASH_ATTENTION="${LLAMA_FLASH_ATTENTION:-on}"
GPU_KIND="cpu"
FREE_VRAM_MB="0"
NGL="0"

if command -v nvidia-smi >/dev/null 2>&1 && nvidia-smi -L >/dev/null 2>&1; then
  GPU_KIND="nvidia"
  FREE_VRAM_MB="$(nvidia-smi --query-gpu=memory.free --format=csv,noheader,nounits | head -n 1 | tr -d ' ')"
  if [[ "$FREE_VRAM_MB" =~ ^[0-9]+$ ]] && (( FREE_VRAM_MB >= MIN_FREE_VRAM_MB )); then
    NGL="99"
  fi
elif [[ "$(uname -s)" == "Darwin" ]] && sysctl -n hw.optional.arm64 2>/dev/null | grep -q '^1$'; then
  GPU_KIND="metal"
  NGL="99"
fi

if [[ "$NGL" == "0" ]]; then
  LLAMA_FLASH_ATTENTION="off"
fi

ARGS=(
  -hf "$MODEL"
  --jinja
  -ngl "$NGL"
  -c "$LLAMA_CONTEXT"
  --host "$HOST"
  --port "$PORT"
  --parallel "$LLAMA_PARALLEL"
  --batch-size "$LLAMA_BATCH_SIZE"
  --ubatch-size "$LLAMA_UBATCH_SIZE"
  --cache-type-k "$LLAMA_CACHE_TYPE_K"
  --cache-type-v "$LLAMA_CACHE_TYPE_V"
  -fa "$LLAMA_FLASH_ATTENTION"
  --no-ui
)

if [[ -n "${LLAMA_EXTRA_ARGS:-}" ]]; then
  # shellcheck disable=SC2206
  EXTRA_ARGS=( ${LLAMA_EXTRA_ARGS} )
  ARGS+=("${EXTRA_ARGS[@]}")
fi

cat <<INFO
Starting llama-server backend
  model:            $MODEL
  endpoint:         http://$HOST:$PORT/v1
  gpu:              $GPU_KIND${FREE_VRAM_MB:+ (${FREE_VRAM_MB} MB free)}
  min free vram:    $MIN_FREE_VRAM_MB MB
  selected ngl:     $NGL
  flash attention:  $LLAMA_FLASH_ATTENTION
INFO

exec "$LLAMA_SERVER_BIN" "${ARGS[@]}"
