#!/usr/bin/env bash
set -euo pipefail

# Adaptive launcher for llama.cpp's OpenAI-compatible llama-server.
# Defaults are optimized for this project: one request at a time via Spring AI.
#
# Overrides:
#   LLAMA_SERVER_BIN=llama-server
#   LLAMA_MODEL="AtomicChat/ornith-9b-GGUF:UD-Q4_K_XL"
#   LLAMA_HOST=127.0.0.1
#   LLAMA_PORT=8080
#   LLAMA_CONTEXT=8192
#   LLAMA_NGL=99
#   LLAMA_BATCH_SIZE=512
#   LLAMA_UBATCH_SIZE=512
#   LLAMA_FLASH_ATTENTION=on|off|auto
#   LLAMA_CACHE_TYPE_K=f16|q8_0|q4_0
#   LLAMA_CACHE_TYPE_V=f16|q8_0|q4_0
#   LLAMA_NO_UI=true|false
#   LLAMA_EXTRA_ARGS="--verbose"

LLAMA_SERVER_BIN="${LLAMA_SERVER_BIN:-llama-server}"
LLAMA_MODEL="${LLAMA_MODEL:-AtomicChat/ornith-9b-GGUF:UD-Q4_K_XL}"
LLAMA_HOST="${LLAMA_HOST:-127.0.0.1}"
LLAMA_PORT="${LLAMA_PORT:-8080}"
LLAMA_CONTEXT="${LLAMA_CONTEXT:-8192}"
LLAMA_PARALLEL="${LLAMA_PARALLEL:-1}"
LLAMA_NO_UI="${LLAMA_NO_UI:-true}"

if ! command -v "$LLAMA_SERVER_BIN" >/dev/null 2>&1; then
  echo "error: '$LLAMA_SERVER_BIN' not found in PATH" >&2
  echo "Install llama.cpp or set LLAMA_SERVER_BIN=/path/to/llama-server" >&2
  exit 127
fi

GPU_KIND="cpu"
GPU_MEMORY_MB="0"

if command -v nvidia-smi >/dev/null 2>&1 && nvidia-smi -L >/dev/null 2>&1; then
  GPU_KIND="nvidia"
  GPU_MEMORY_MB="$(nvidia-smi --query-gpu=memory.total --format=csv,noheader,nounits | head -n 1 | tr -d ' ')"
elif [[ "$(uname -s)" == "Darwin" ]]; then
  # llama.cpp builds on Apple Silicon usually use Metal. Unified memory is shared,
  # so keep conservative batch defaults unless overridden.
  if sysctl -n hw.optional.arm64 2>/dev/null | grep -q '^1$'; then
    GPU_KIND="metal"
  fi
fi

case "$GPU_KIND" in
  nvidia)
    DEFAULT_FLASH_ATTENTION="on"
    if (( GPU_MEMORY_MB >= 24576 )); then
      DEFAULT_NGL="99"
      DEFAULT_BATCH_SIZE="2048"
      DEFAULT_UBATCH_SIZE="1024"
      DEFAULT_CACHE_TYPE_K="f16"
      DEFAULT_CACHE_TYPE_V="f16"
    elif (( GPU_MEMORY_MB >= 16384 )); then
      DEFAULT_NGL="99"
      DEFAULT_BATCH_SIZE="1024"
      DEFAULT_UBATCH_SIZE="1024"
      DEFAULT_CACHE_TYPE_K="f16"
      DEFAULT_CACHE_TYPE_V="f16"
    elif (( GPU_MEMORY_MB >= 10240 )); then
      DEFAULT_NGL="99"
      DEFAULT_BATCH_SIZE="512"
      DEFAULT_UBATCH_SIZE="512"
      DEFAULT_CACHE_TYPE_K="q8_0"
      DEFAULT_CACHE_TYPE_V="q8_0"
    elif (( GPU_MEMORY_MB >= 8192 )); then
      DEFAULT_NGL="60"
      DEFAULT_BATCH_SIZE="512"
      DEFAULT_UBATCH_SIZE="256"
      DEFAULT_CACHE_TYPE_K="q8_0"
      DEFAULT_CACHE_TYPE_V="q8_0"
    else
      DEFAULT_NGL="35"
      DEFAULT_BATCH_SIZE="256"
      DEFAULT_UBATCH_SIZE="256"
      DEFAULT_CACHE_TYPE_K="q8_0"
      DEFAULT_CACHE_TYPE_V="q8_0"
    fi
    ;;
  metal)
    DEFAULT_FLASH_ATTENTION="on"
    DEFAULT_NGL="99"
    DEFAULT_BATCH_SIZE="512"
    DEFAULT_UBATCH_SIZE="512"
    DEFAULT_CACHE_TYPE_K="f16"
    DEFAULT_CACHE_TYPE_V="f16"
    ;;
  *)
    DEFAULT_FLASH_ATTENTION="off"
    DEFAULT_NGL="0"
    DEFAULT_BATCH_SIZE="256"
    DEFAULT_UBATCH_SIZE="256"
    DEFAULT_CACHE_TYPE_K="f16"
    DEFAULT_CACHE_TYPE_V="f16"
    ;;
esac

LLAMA_NGL="${LLAMA_NGL:-$DEFAULT_NGL}"
LLAMA_BATCH_SIZE="${LLAMA_BATCH_SIZE:-$DEFAULT_BATCH_SIZE}"
LLAMA_UBATCH_SIZE="${LLAMA_UBATCH_SIZE:-$DEFAULT_UBATCH_SIZE}"
LLAMA_CACHE_TYPE_K="${LLAMA_CACHE_TYPE_K:-$DEFAULT_CACHE_TYPE_K}"
LLAMA_CACHE_TYPE_V="${LLAMA_CACHE_TYPE_V:-$DEFAULT_CACHE_TYPE_V}"
LLAMA_FLASH_ATTENTION="${LLAMA_FLASH_ATTENTION:-$DEFAULT_FLASH_ATTENTION}"

ARGS=(
  -hf "$LLAMA_MODEL"
  --jinja
  -ngl "$LLAMA_NGL"
  -c "$LLAMA_CONTEXT"
  --host "$LLAMA_HOST"
  --port "$LLAMA_PORT"
  --parallel "$LLAMA_PARALLEL"
  --batch-size "$LLAMA_BATCH_SIZE"
  --ubatch-size "$LLAMA_UBATCH_SIZE"
  --cache-type-k "$LLAMA_CACHE_TYPE_K"
  --cache-type-v "$LLAMA_CACHE_TYPE_V"
)

case "$LLAMA_FLASH_ATTENTION" in
  on|off|auto)
    ARGS+=(-fa "$LLAMA_FLASH_ATTENTION")
    ;;
  true|1)
    ARGS+=(-fa on)
    ;;
  false|0)
    ARGS+=(-fa off)
    ;;
  *)
    echo "error: LLAMA_FLASH_ATTENTION must be one of: on, off, auto, true, false, 1, 0" >&2
    exit 2
    ;;
esac

if [[ "$LLAMA_NO_UI" == "true" || "$LLAMA_NO_UI" == "1" || "$LLAMA_NO_UI" == "yes" ]]; then
  ARGS+=(--no-ui)
else
  ARGS+=(--ui)
fi

if [[ -n "${LLAMA_EXTRA_ARGS:-}" ]]; then
  # shellcheck disable=SC2206
  EXTRA_ARGS=( ${LLAMA_EXTRA_ARGS} )
  ARGS+=("${EXTRA_ARGS[@]}")
fi

cat <<INFO
Starting llama-server for Spring AI OpenAI client
  model:       $LLAMA_MODEL
  endpoint:    http://$LLAMA_HOST:$LLAMA_PORT/v1
  gpu:         $GPU_KIND${GPU_MEMORY_MB:+ (${GPU_MEMORY_MB} MB)}
  ngl:         $LLAMA_NGL
  context:     $LLAMA_CONTEXT
  parallel:    $LLAMA_PARALLEL
  batch:       $LLAMA_BATCH_SIZE
  ubatch:      $LLAMA_UBATCH_SIZE
  k/v cache:   $LLAMA_CACHE_TYPE_K / $LLAMA_CACHE_TYPE_V
  flash attn:  $LLAMA_FLASH_ATTENTION
  web ui:      $([[ "$LLAMA_NO_UI" == "true" || "$LLAMA_NO_UI" == "1" || "$LLAMA_NO_UI" == "yes" ]] && echo disabled || echo enabled)

Spring AI env:
  OPENAI_BASE_URL=http://$LLAMA_HOST:$LLAMA_PORT/v1
  CHAT_MODEL=$LLAMA_MODEL
INFO

exec "$LLAMA_SERVER_BIN" "${ARGS[@]}"
