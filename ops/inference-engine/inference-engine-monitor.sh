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
WARM_UP_TIMEOUT_SECONDS="${WARM_UP_TIMEOUT_SECONDS:-180}"
WARM_UP_MAX_TOKENS="${WARM_UP_MAX_TOKENS:-1}"
STATE_DIR="${STATE_DIR:-${TMPDIR:-/tmp}/inference-engine-monitor}"

ORNITH_MODEL="${ORNITH_MODEL:-AtomicChat/ornith-9b-GGUF:UD-Q4_K_XL}"
GEMMA_MODEL="${GEMMA_MODEL:-unsloth/gemma-4-E4B-it-GGUF:IQ4_XS}"
ORNITH_MIN_FREE_VRAM_MB="${ORNITH_MIN_FREE_VRAM_MB:-7000}"
GEMMA_MIN_FREE_VRAM_MB="${GEMMA_MIN_FREE_VRAM_MB:-4000}"

# Format: one model per line as "model_id|min_free_vram_mb".
# Override this to monitor a different set without editing the script.
MONITORED_MODELS="${MONITORED_MODELS:-${ORNITH_MODEL}|${ORNITH_MIN_FREE_VRAM_MB}
${GEMMA_MODEL}|${GEMMA_MIN_FREE_VRAM_MB}}"

log() {
  printf '[monitor] %s %s\n' "$(date -Is)" "$*"
}

if ! command -v nvidia-smi >/dev/null 2>&1; then
  log "nvidia-smi not found; CPU-to-GPU promotion monitor is only needed on NVIDIA hosts"
  exit 0
fi

for cmd in curl python3 pgrep; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    log "required command not found: $cmd"
    exit 127
  fi
done

mkdir -p "$STATE_DIR"

free_vram_mb() {
  nvidia-smi --query-gpu=memory.free --format=csv,noheader,nounits 2>/dev/null \
    | head -n 1 \
    | tr -d ' ' \
    || echo 0
}

model_pid() {
  local model="$1"
  pgrep -u "${USER:-$(id -un)}" -f "llama-server .*${model}" | head -n 1 || true
}

model_running_on_cpu() {
  local pid="$1"
  [[ -n "$pid" ]] || return 1
  tr '\0' ' ' < "/proc/$pid/cmdline" 2>/dev/null | grep -q -- '-ngl 0'
}

model_key() {
  python3 -c 'import hashlib,sys; print(hashlib.sha256(sys.argv[1].encode()).hexdigest())' "$1"
}

last_promotion_file() {
  printf '%s/%s.last\n' "$STATE_DIR" "$(model_key "$1")"
}

last_promotion_at() {
  local file
  file="$(last_promotion_file "$1")"
  if [[ -f "$file" ]]; then
    cat "$file"
  else
    echo 0
  fi
}

set_last_promotion_at() {
  local model="$1"
  local timestamp="$2"
  printf '%s\n' "$timestamp" > "$(last_promotion_file "$model")"
}

url_encode() {
  python3 -c 'import urllib.parse,sys; print(urllib.parse.quote(sys.argv[1], safe=""))' "$1"
}

json_escape() {
  python3 -c 'import json,sys; print(json.dumps(sys.argv[1]))' "$1"
}

unload_model() {
  local model="$1"
  local encoded
  encoded="$(url_encode "$model")"
  curl -fsS --max-time 30 -X POST "$LLAMA_SWAP_URL/api/models/unload/$encoded" >/dev/null
}

warm_model() {
  local model="$1"
  local model_json
  model_json="$(json_escape "$model")"

  curl -fsS -m "$WARM_UP_TIMEOUT_SECONDS" "$LLAMA_SWAP_URL/v1/chat/completions" \
    -H 'Content-Type: application/json' \
    -d "{\"model\":${model_json},\"messages\":[{\"role\":\"user\",\"content\":\"Reply OK.\"}],\"max_tokens\":${WARM_UP_MAX_TOKENS},\"stream\":false}" \
    >/dev/null
}

promote_if_needed() {
  local model="$1"
  local min_free_mb="$2"
  local free_mb="$3"
  local now="$4"
  local pid
  pid="$(model_pid "$model")"

  if ! model_running_on_cpu "$pid"; then
    return 0
  fi

  if ! [[ "$free_mb" =~ ^[0-9]+$ ]] || (( free_mb < min_free_mb )); then
    log "${model} is on CPU; waiting for ${min_free_mb}MB free VRAM (currently ${free_mb}MB)"
    return 0
  fi

  local last
  last="$(last_promotion_at "$model")"
  if (( now - last < PROMOTION_COOLDOWN_SECONDS )); then
    return 0
  fi

  log "promoting model to GPU; model=${model} pid=${pid} free_vram=${free_mb}MB threshold=${min_free_mb}MB"
  if unload_model "$model"; then
    sleep 2
    if warm_model "$model"; then
      set_last_promotion_at "$model" "$now"
      log "promotion requested successfully; model=${model}"
    else
      log "warm request failed after unload; model=${model}; llama-swap may retry on next real request"
    fi
  else
    log "unload request failed; model=${model}"
  fi
}

log "started; checking every ${CHECK_INTERVAL_SECONDS}s; monitored models: $(echo "$MONITORED_MODELS" | tr '\n' '; ')"

while true; do
  free_mb="$(free_vram_mb)"
  now="$(date +%s)"

  while IFS='|' read -r model min_free_mb; do
    [[ -n "${model:-}" ]] || continue
    promote_if_needed "$model" "$min_free_mb" "$free_mb" "$now"
  done <<< "$MONITORED_MODELS"

  sleep "$CHECK_INTERVAL_SECONDS"
done
