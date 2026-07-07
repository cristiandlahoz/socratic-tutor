#!/usr/bin/env bash
set -euo pipefail

# RunPod-flavored bootstrap for the inference engine.
#
# It keeps installed binaries, llama.cpp sources/build output, downloaded GGUF
# models, logs, PID files, and monitor state under /workspace so they survive
# pod replacement.
#
# Usage:
#   ./bootstrap-inference_engine-runpod.sh      # foreground
#   ./bootstrap-inference_engine-runpod.sh -d   # detached/background

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=runpod.env
source "$ROOT_DIR/runpod.env"

mkdir -p \
  "$BIN_DIR" \
  "$LLAMA_CACHE" \
  "$(dirname "$LOG_FILE")" \
  "$STATE_DIR" \
  "$HF_HOME" \
  "$HUGGINGFACE_HUB_CACHE" \
  "$XDG_CACHE_HOME"

exec "$ROOT_DIR/bootstrap-inference_engine.sh" "$@"
