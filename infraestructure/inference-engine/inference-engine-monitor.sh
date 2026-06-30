#!/usr/bin/env bash
set -euo pipefail

# Promotes CPU-started llama-server backends to GPU when VRAM becomes available.
# llama.cpp cannot move layers from CPU to GPU in a running process, so promotion
# means unloading the model from llama-swap and sending a tiny request to make
# llama-swap start it again. start-llama-server.sh then re-checks VRAM and picks
# -ngl 99 or -ngl 0.

LLAMA_SWAP_URL="${LLAMA_SWAP_URL:-http://127.0.0.1:8080}"
CHECK_INTERVAL_SECONDS="${CHECK_INTERVAL_SECONDS:-60}"
PROMOTION_COOLDOWN_SECONDS="${PROMOTION_COOLDOWN_SECONDS:-300}"
ORNITH_MODEL="${ORNITH_MODEL:-AtomicChat/ornith-9b-GGUF:UD-Q4_K_XL}"
ORNITH_MODEL_URL_ENCODED="AtomicChat%2Fornith-9b-GGUF%3AUD-Q4_K_XL"
ORNITH_MIN_FREE_VRAM_MB="${ORNITH_MIN_FREE_VRAM_MB:-7000}"
LAST_PROMOTION_AT=0

log() {
  printf '[monitor] %s %s\n' "$(date -Is)" "$*"
}

free_vram_mb() {
  if ! command -v nvidia-smi >/dev/null 2>&1; then
    echo 0
    return
  fi
  nvidia-smi --query-gpu=memory.free --format=csv,noheader,nounits 2>/dev/null \
    | head -n 1 \
    | tr -d ' ' \
    || echo 0
}

ornith_pid() {
  pgrep -u "${USER:-$(id -un)}" -f "llama-server .*${ORNITH_MODEL}" | head -n 1 || true
}

ornith_running_on_cpu() {
  local pid="$1"
  [[ -n "$pid" ]] || return 1
  tr '\0' ' ' < "/proc/$pid/cmdline" 2>/dev/null | grep -q -- '-ngl 0'
}

unload_ornith() {
  curl -fsS -X POST "$LLAMA_SWAP_URL/api/models/unload/$ORNITH_MODEL_URL_ENCODED" >/dev/null
}

warm_ornith() {
  curl -fsS -m 120 "$LLAMA_SWAP_URL/v1/chat/completions" \
    -H 'Content-Type: application/json' \
    -d "{\"model\":\"$ORNITH_MODEL\",\"messages\":[{\"role\":\"user\",\"content\":\"Reply OK.\"}],\"max_tokens\":1,\"stream\":false}" \
    >/dev/null
}

log "started; checking every ${CHECK_INTERVAL_SECONDS}s; Ornith GPU threshold=${ORNITH_MIN_FREE_VRAM_MB}MB"

while true; do
  pid="$(ornith_pid)"
  free_mb="$(free_vram_mb)"
  now="$(date +%s)"

  if ornith_running_on_cpu "$pid" && [[ "$free_mb" =~ ^[0-9]+$ ]] && (( free_mb >= ORNITH_MIN_FREE_VRAM_MB )); then
    if (( now - LAST_PROMOTION_AT >= PROMOTION_COOLDOWN_SECONDS )); then
      log "promoting Ornith to GPU; pid=$pid free_vram=${free_mb}MB"
      if unload_ornith; then
        sleep 2
        if warm_ornith; then
          LAST_PROMOTION_AT="$now"
          log "promotion requested successfully"
        else
          log "warm request failed after unload; llama-swap may retry on next real request"
        fi
      else
        log "unload request failed"
      fi
    fi
  fi

  sleep "$CHECK_INTERVAL_SECONDS"
done
