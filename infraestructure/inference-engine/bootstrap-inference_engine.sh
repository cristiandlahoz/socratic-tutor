#!/usr/bin/env bash
set -euo pipefail

# Bootstrap and run the inference engine.
#
# Usage:
#   ./bootstrap-inference_engine.sh      # foreground
#   ./bootstrap-inference_engine.sh -d   # detached/background; survives terminal close

DETACHED=false
while getopts ":d" opt; do
  case "$opt" in
    d) DETACHED=true ;;
    *) echo "usage: $0 [-d]" >&2; exit 2 ;;
  esac
done
shift $((OPTIND - 1))

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BIN_DIR="${BIN_DIR:-$HOME/bin}"
LLAMA_CPP_DIR="${LLAMA_CPP_DIR:-$HOME/llama.cpp}"
LLAMA_SWAP_VERSION="${LLAMA_SWAP_VERSION:-233}"
LLAMA_SWAP_BIN="${LLAMA_SWAP_BIN:-$BIN_DIR/llama-swap}"
LLAMA_SERVER_BIN="${LLAMA_SERVER_BIN:-$BIN_DIR/llama-server}"
LOG_FILE="${LOG_FILE:-$ROOT_DIR/inference-engine.log}"
PID_FILE="${PID_FILE:-$ROOT_DIR/inference-engine.pid}"
MONITOR_LOG_FILE="${MONITOR_LOG_FILE:-$ROOT_DIR/inference-engine-monitor.log}"
MONITOR_PID_FILE="${MONITOR_PID_FILE:-$ROOT_DIR/inference-engine-monitor.pid}"

mkdir -p "$BIN_DIR"

log() {
  printf '[bootstrap] %s\n' "$*"
}

install_llama_swap() {
  if command -v llama-swap >/dev/null 2>&1; then
    LLAMA_SWAP_BIN="$(command -v llama-swap)"
    log "llama-swap found: $LLAMA_SWAP_BIN"
  elif [[ -x "$LLAMA_SWAP_BIN" ]]; then
    log "llama-swap found: $LLAMA_SWAP_BIN"
  else
    log "llama-swap not found; downloading v$LLAMA_SWAP_VERSION"
    local os arch archive tmp url
    os="$(uname -s | tr '[:upper:]' '[:lower:]')"
    arch="$(uname -m)"
    case "$arch" in
      x86_64|amd64) arch="amd64" ;;
      aarch64|arm64) arch="arm64" ;;
      *) echo "unsupported architecture for llama-swap: $arch" >&2; exit 1 ;;
    esac
    archive="llama-swap_${LLAMA_SWAP_VERSION}_${os}_${arch}.tar.gz"
    url="https://github.com/mostlygeek/llama-swap/releases/download/v${LLAMA_SWAP_VERSION}/${archive}"
    tmp="$(mktemp -d)"
    curl -L --fail -o "$tmp/$archive" "$url"
    tar -xzf "$tmp/$archive" -C "$tmp"
    install -m 755 "$tmp/llama-swap" "$LLAMA_SWAP_BIN"
    rm -rf "$tmp"
  fi

  "$LLAMA_SWAP_BIN" -version >/dev/null
  log "llama-swap check passed"
}

install_llama_cpp() {
  if command -v llama-server >/dev/null 2>&1; then
    local found
    found="$(command -v llama-server)"
    if [[ ! -x "$LLAMA_SERVER_BIN" ]]; then
      ln -sf "$found" "$LLAMA_SERVER_BIN"
    fi
    log "llama-server found: $found"
    return
  elif [[ -x "$LLAMA_SERVER_BIN" ]]; then
    log "llama-server found: $LLAMA_SERVER_BIN"
    return
  fi

  log "llama-server not found; building llama.cpp with CUDA and CURL/OpenSSL support"

  for cmd in git cmake curl; do
    if ! command -v "$cmd" >/dev/null 2>&1; then
      echo "error: '$cmd' is required to build llama.cpp" >&2
      exit 127
    fi
  done

  if [[ ! -d "$LLAMA_CPP_DIR/.git" ]]; then
    git clone https://github.com/ggml-org/llama.cpp.git "$LLAMA_CPP_DIR"
  fi

  cmake -S "$LLAMA_CPP_DIR" -B "$LLAMA_CPP_DIR/build" \
    -DCMAKE_BUILD_TYPE=Release \
    -DGGML_CUDA=ON \
    -DLLAMA_CURL=ON
  cmake --build "$LLAMA_CPP_DIR/build" --config Release -j "$(nproc 2>/dev/null || sysctl -n hw.ncpu 2>/dev/null || echo 4)"

  local built_bin
  built_bin="$(find "$LLAMA_CPP_DIR/build" -type f -name llama-server -perm -111 | head -n 1)"
  if [[ -z "$built_bin" ]]; then
    echo "error: llama-server build finished but binary was not found" >&2
    exit 1
  fi
  ln -sf "$built_bin" "$LLAMA_SERVER_BIN"
  log "llama-server check passed: $LLAMA_SERVER_BIN"
}

already_running() {
  [[ -f "$PID_FILE" ]] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null
}

monitor_running() {
  [[ -f "$MONITOR_PID_FILE" ]] && kill -0 "$(cat "$MONITOR_PID_FILE")" 2>/dev/null
}

start_monitor_detached() {
  if monitor_running; then
    log "inference monitor already running with pid $(cat "$MONITOR_PID_FILE")"
    return
  fi

  log "starting inference monitor detached; log: $MONITOR_LOG_FILE"
  nohup "$ROOT_DIR/inference-engine-monitor.sh" >> "$MONITOR_LOG_FILE" 2>&1 &
  echo $! > "$MONITOR_PID_FILE"
  disown || true
}

start_engine() {
  export PATH="$BIN_DIR:$PATH"
  export LLAMA_SWAP_BIN
  export LLAMA_SERVER_BIN

  if already_running; then
    log "inference engine already running with pid $(cat "$PID_FILE")"
    if $DETACHED; then
      start_monitor_detached
    fi
    return
  fi

  if $DETACHED; then
    log "starting inference engine detached; log: $LOG_FILE"
    nohup "$ROOT_DIR/run-inference-engine.sh" > "$LOG_FILE" 2>&1 &
    echo $! > "$PID_FILE"
    disown || true
    sleep 3
    if ! kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
      echo "error: inference engine exited during startup" >&2
      tail -120 "$LOG_FILE" >&2 || true
      exit 1
    fi
    log "inference engine started with pid $(cat "$PID_FILE")"
    start_monitor_detached
  else
    log "starting inference engine in foreground"
    exec "$ROOT_DIR/run-inference-engine.sh"
  fi
}

install_llama_swap
install_llama_cpp
start_engine
