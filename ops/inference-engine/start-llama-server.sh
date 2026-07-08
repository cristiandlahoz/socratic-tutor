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
#   GEMMA_CONTEXT=20000
#   LLAMA_CACHE=$HOME/.cache/llama.cpp
#   LLAMA_PARALLEL=1
#   LLAMA_BATCH_SIZE=1024
#   LLAMA_UBATCH_SIZE=512
#   LLAMA_CACHE_TYPE_K=q8_0
#   LLAMA_CACHE_TYPE_V=q8_0
#   LLAMA_THREADS=auto
#   LLAMA_THREADS_BATCH=auto
#   LLAMA_FLASH_ATTENTION=on|off|auto
#   LLAMA_EXTRA_ARGS="..."

MODEL="${MODEL:-${1:-}}"
PORT="${PORT:-${2:-}}"
MIN_FREE_VRAM_MB="${MIN_FREE_VRAM_MB:-${3:-4096}}"
REQUESTED_CONTEXT="${LLAMA_CONTEXT:-${4:-}}"

if [[ -z "$MODEL" || -z "$PORT" ]]; then
  echo "usage: MODEL=<model> PORT=<port> $0" >&2
  echo "   or: $0 <model> <port> [min-free-vram-mb] [context-tokens]" >&2
  exit 2
fi

case "$MODEL" in
  gemma|gemma3|gemma-4)
    MODEL="${GEMMA_MODEL:-unsloth/gemma-4-E4B-it-GGUF:IQ4_XS}"
    REQUESTED_CONTEXT="${REQUESTED_CONTEXT:-${GEMMA_CONTEXT:-20000}}"
    ;;
  ornith)
    MODEL="${ORNITH_MODEL:-AtomicChat/ornith-9b-GGUF:UD-Q4_K_XL}"
    ;;
esac

LLAMA_CONTEXT="${REQUESTED_CONTEXT:-8192}"

if [[ "$MODEL" != */* ]]; then
  echo "error: MODEL must be a Hugging Face repo reference like repo/model[:quant], or a known alias such as gemma/ornith" >&2
  exit 2
fi

LLAMA_SERVER_BIN="${LLAMA_SERVER_BIN:-llama-server}"
HOST="${HOST:-127.0.0.1}"
LLAMA_CACHE="${LLAMA_CACHE:-$HOME/.cache/llama.cpp}"
LLAMA_PARALLEL="${LLAMA_PARALLEL:-1}"
LLAMA_BATCH_SIZE="${LLAMA_BATCH_SIZE:-1024}"
LLAMA_UBATCH_SIZE="${LLAMA_UBATCH_SIZE:-512}"
LLAMA_CACHE_TYPE_K="${LLAMA_CACHE_TYPE_K:-q8_0}"
LLAMA_CACHE_TYPE_V="${LLAMA_CACHE_TYPE_V:-q8_0}"
LLAMA_THREADS="${LLAMA_THREADS:-auto}"
LLAMA_THREADS_BATCH="${LLAMA_THREADS_BATCH:-auto}"
LLAMA_FLASH_ATTENTION="${LLAMA_FLASH_ATTENTION:-on}"
GPU_KIND="cpu"
FREE_VRAM_MB="0"
NGL="0"
export LLAMA_CACHE
mkdir -p "$LLAMA_CACHE"

resolve_executable() {
  local candidate="$1"

  # Must not return 1 just because the candidate is missing. This function is
  # used in command substitution while set -e is enabled.
  if [[ "$candidate" == */* ]]; then
    if [[ -x "$candidate" ]]; then
      printf '%s\n' "$candidate"
    fi
    return 0
  fi

  command -v "$candidate" 2>/dev/null || true
  return 0
}

llama_server_supports_hf() {
  local candidate="$1"
  local help
  help="$($candidate --help 2>&1 || true)"
  grep -Eq -- '(^|[[:space:]])-hf([,[:space:]]|$)|--hf-repo|--hf-file' <<< "$help"
}

RESOLVED_LLAMA_SERVER_BIN="$(resolve_executable "$LLAMA_SERVER_BIN")"
if [[ -z "$RESOLVED_LLAMA_SERVER_BIN" ]]; then
  echo "error: llama-server not found: $LLAMA_SERVER_BIN" >&2
  exit 127
fi
if ! llama_server_supports_hf "$RESOLVED_LLAMA_SERVER_BIN"; then
  echo "error: llama-server does not advertise -hf/Hugging Face model loading" >&2
  echo "Rebuild llama.cpp with CURL support, or run ./bootstrap-inference_engine.sh" >&2
  exit 1
fi
LLAMA_SERVER_BIN="$RESOLVED_LLAMA_SERVER_BIN"

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

case "$LLAMA_FLASH_ATTENTION" in
  auto)
    if [[ "$NGL" == "0" ]]; then
      LLAMA_FLASH_ATTENTION="off"
    else
      LLAMA_FLASH_ATTENTION="on"
    fi
    ;;
  on|off)
    ;;
  *)
    echo "error: LLAMA_FLASH_ATTENTION must be on, off, or auto" >&2
    exit 2
    ;;
esac

if [[ "$NGL" == "0" ]]; then
  LLAMA_FLASH_ATTENTION="off"
fi

cpu_count() {
  if command -v nproc >/dev/null 2>&1; then
    nproc
  elif [[ "$(uname -s)" == "Darwin" ]]; then
    sysctl -n hw.ncpu 2>/dev/null || echo 1
  else
    getconf _NPROCESSORS_ONLN 2>/dev/null || echo 1
  fi
}

resolve_threads() {
  local requested="$1"
  local cpus="$2"
  local gpu_offload="$3"

  if [[ "$requested" != "auto" ]]; then
    echo "$requested"
    return
  fi

  if (( cpus <= 2 )); then
    echo "$cpus"
  elif [[ "$gpu_offload" == "1" ]]; then
    # With GPU offload, avoid over-subscribing CPU when multiple backends run.
    # Half the machine, capped at 8, leaves cores for the second model and OS.
    local half=$(( (cpus + 1) / 2 ))
    (( half > 8 )) && half=8
    echo "$half"
  else
    # CPU fallback needs all cores it can get, but keep one core free on larger hosts.
    if (( cpus > 4 )); then
      echo $(( cpus - 1 ))
    else
      echo "$cpus"
    fi
  fi
}

CPU_COUNT="$(cpu_count)"
GPU_OFFLOAD="0"
[[ "$NGL" != "0" ]] && GPU_OFFLOAD="1"
LLAMA_THREADS="$(resolve_threads "$LLAMA_THREADS" "$CPU_COUNT" "$GPU_OFFLOAD")"
LLAMA_THREADS_BATCH="$(resolve_threads "$LLAMA_THREADS_BATCH" "$CPU_COUNT" "$GPU_OFFLOAD")"

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
  --threads "$LLAMA_THREADS"
  --threads-batch "$LLAMA_THREADS_BATCH"
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
  cache:            $LLAMA_CACHE
  gpu:              $GPU_KIND${FREE_VRAM_MB:+ (${FREE_VRAM_MB} MB free)}
  min free vram:    $MIN_FREE_VRAM_MB MB
  selected ngl:     $NGL
  context:          $LLAMA_CONTEXT
  batch/ubatch:     $LLAMA_BATCH_SIZE/$LLAMA_UBATCH_SIZE
  kv cache:         K=$LLAMA_CACHE_TYPE_K V=$LLAMA_CACHE_TYPE_V
  cpu threads:      $LLAMA_THREADS generation, $LLAMA_THREADS_BATCH batch (host CPUs: $CPU_COUNT)
  flash attention:  $LLAMA_FLASH_ATTENTION
INFO

exec "$LLAMA_SERVER_BIN" "${ARGS[@]}"
