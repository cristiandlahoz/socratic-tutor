#!/usr/bin/env bash
set -euo pipefail

# RunPod-flavored foreground runner. For first-time setup or detached mode, use
# bootstrap-inference_engine-runpod.sh instead.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=runpod.env
source "$ROOT_DIR/runpod.env"

mkdir -p "$BIN_DIR" "$LLAMA_CACHE" "$(dirname "$LOG_FILE")" "$STATE_DIR"

exec "$ROOT_DIR/run-inference-engine.sh" "$@"
