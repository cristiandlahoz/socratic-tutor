#!/usr/bin/env bash
set -euo pipefail

# RunPod-flavored bootstrap for the inference engine.
#
# It keeps installed binaries, llama.cpp sources/build output, downloaded GGUF
# models, logs, PID files, and monitor state under /workspace by default so
# they survive pod replacement.
#
# Usage:
#   ./bootstrap-inference_engine-runpod.sh                    # foreground
#   ./bootstrap-inference_engine-runpod.sh -d                 # detached/background
#   ./bootstrap-inference_engine-runpod.sh --home -d          # store everything under $HOME
#   ./bootstrap-inference_engine-runpod.sh --workspace /path  # store everything under /path

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

pass_args=()
while (($#)); do
  case "$1" in
    --home)
      RUNPOD_WORKSPACE="$HOME"
      shift
      ;;
    --workspace)
      if [[ $# -lt 2 || -z "${2:-}" ]]; then
        echo "usage: $0 [--home | --workspace DIR] [-d]" >&2
        exit 2
      fi
      RUNPOD_WORKSPACE="$2"
      shift 2
      ;;
    --workspace=*)
      RUNPOD_WORKSPACE="${1#--workspace=}"
      shift
      ;;
    *)
      pass_args+=("$1")
      shift
      ;;
  esac
done
export RUNPOD_WORKSPACE

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

exec "$ROOT_DIR/bootstrap-inference_engine.sh" "${pass_args[@]}"
