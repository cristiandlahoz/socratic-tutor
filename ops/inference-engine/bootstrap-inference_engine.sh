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
LLAMA_CPP_BACKEND="${LLAMA_CPP_BACKEND:-auto}" # auto|cuda|metal|cpu
LLAMA_CPP_REF="${LLAMA_CPP_REF:-}"
LLAMA_SWAP_VERSION="${LLAMA_SWAP_VERSION:-233}"
LLAMA_SWAP_BIN="${LLAMA_SWAP_BIN:-$BIN_DIR/llama-swap}"
LLAMA_SERVER_BIN="${LLAMA_SERVER_BIN:-$BIN_DIR/llama-server}"
LLAMA_CACHE="${LLAMA_CACHE:-$HOME/.cache/llama.cpp}"
LOG_FILE="${LOG_FILE:-$ROOT_DIR/inference-engine.log}"
PID_FILE="${PID_FILE:-$ROOT_DIR/inference-engine.pid}"
MONITOR_LOG_FILE="${MONITOR_LOG_FILE:-$ROOT_DIR/inference-engine-monitor.log}"
MONITOR_PID_FILE="${MONITOR_PID_FILE:-$ROOT_DIR/inference-engine-monitor.pid}"
BOOTSTRAP_STARTUP_CHECK_SECONDS="${BOOTSTRAP_STARTUP_CHECK_SECONDS:-3}"

mkdir -p "$BIN_DIR" "$LLAMA_CACHE"

log() {
  printf '[bootstrap] %s\n' "$*"
}

fail() {
  printf '[bootstrap] error: %s\n' "$*" >&2
  exit 1
}

resolve_executable() {
  local candidate="$1"
  if [[ "$candidate" == */* ]]; then
    [[ -x "$candidate" ]] && printf '%s\n' "$candidate"
  else
    command -v "$candidate" 2>/dev/null || true
  fi
}

require_command() {
  local command_name="$1"
  if ! command -v "$command_name" >/dev/null 2>&1; then
    fail "'$command_name' is required but was not found in PATH"
  fi
}

llama_server_supports_hf() {
  local candidate="$1"
  local help
  help="$($candidate --help 2>&1 || true)"
  grep -Eq -- '(^|[[:space:]])-hf([,[:space:]]|$)|--hf-repo|--hf-file' <<< "$help"
}

install_llama_swap() {
  local found
  found="$(resolve_executable "$LLAMA_SWAP_BIN")"
  if [[ -z "$found" ]]; then
    found="$(command -v llama-swap 2>/dev/null || true)"
  fi

  if [[ -n "$found" ]]; then
    LLAMA_SWAP_BIN="$found"
    log "llama-swap found: $LLAMA_SWAP_BIN"
  else
    require_command curl
    require_command tar

    log "llama-swap not found; downloading v$LLAMA_SWAP_VERSION"
    local os arch archive tmp url
    os="$(uname -s | tr '[:upper:]' '[:lower:]')"
    arch="$(uname -m)"
    case "$arch" in
      x86_64|amd64) arch="amd64" ;;
      aarch64|arm64) arch="arm64" ;;
      *) fail "unsupported architecture for llama-swap: $arch" ;;
    esac

    archive="llama-swap_${LLAMA_SWAP_VERSION}_${os}_${arch}.tar.gz"
    url="https://github.com/mostlygeek/llama-swap/releases/download/v${LLAMA_SWAP_VERSION}/${archive}"
    tmp="$(mktemp -d)"
    curl -L --fail --retry 3 --connect-timeout 15 -o "$tmp/$archive" "$url"
    tar -xzf "$tmp/$archive" -C "$tmp"
    install -m 755 "$tmp/llama-swap" "$LLAMA_SWAP_BIN"
    rm -rf "$tmp"
  fi

  "$LLAMA_SWAP_BIN" -version >/dev/null
  log "llama-swap check passed"
}

detect_llama_cpp_backend() {
  case "$LLAMA_CPP_BACKEND" in
    cuda|metal|cpu)
      printf '%s\n' "$LLAMA_CPP_BACKEND"
      return
      ;;
    auto)
      ;;
    *)
      fail "unsupported LLAMA_CPP_BACKEND='$LLAMA_CPP_BACKEND'; use auto, cuda, metal, or cpu"
      ;;
  esac

  if [[ "$(uname -s)" == "Darwin" ]] && sysctl -n hw.optional.arm64 2>/dev/null | grep -q '^1$'; then
    printf 'metal\n'
  elif command -v nvidia-smi >/dev/null 2>&1 && nvidia-smi -L >/dev/null 2>&1; then
    printf 'cuda\n'
  else
    printf 'cpu\n'
  fi
}

install_llama_cpp() {
  local found
  found="$(resolve_executable "$LLAMA_SERVER_BIN")"
  if [[ -z "$found" ]]; then
    found="$(command -v llama-server 2>/dev/null || true)"
  fi

  if [[ -n "$found" ]] && llama_server_supports_hf "$found"; then
    LLAMA_SERVER_BIN="$found"
    log "llama-server found with Hugging Face support: $LLAMA_SERVER_BIN"
    return
  elif [[ -n "$found" ]]; then
    log "llama-server found but it does not advertise -hf/Hugging Face model loading; rebuilding llama.cpp"
  fi

  local backend
  backend="$(detect_llama_cpp_backend)"
  log "llama-server not usable; building llama.cpp backend=$backend with CURL support"

  for cmd in git cmake curl; do
    require_command "$cmd"
  done

  if [[ "$backend" == "cuda" ]] && ! command -v nvcc >/dev/null 2>&1; then
    log "CUDA backend selected but nvcc was not found; CMake may still find CUDA if your toolkit is configured"
  fi

  if [[ ! -d "$LLAMA_CPP_DIR/.git" ]]; then
    git clone https://github.com/ggml-org/llama.cpp.git "$LLAMA_CPP_DIR"
  fi

  if [[ -n "$LLAMA_CPP_REF" ]]; then
    git -C "$LLAMA_CPP_DIR" fetch --tags --depth 1 origin "$LLAMA_CPP_REF" || true
    git -C "$LLAMA_CPP_DIR" checkout "$LLAMA_CPP_REF"
  fi

  local cmake_args=(
    -DCMAKE_BUILD_TYPE=Release
    -DLLAMA_CURL=ON
  )

  case "$backend" in
    cuda) cmake_args+=(-DGGML_CUDA=ON) ;;
    metal) cmake_args+=(-DGGML_METAL=ON) ;;
    cpu) ;;
  esac

  cmake -S "$LLAMA_CPP_DIR" -B "$LLAMA_CPP_DIR/build" "${cmake_args[@]}"
  cmake --build "$LLAMA_CPP_DIR/build" --config Release -j "$(nproc 2>/dev/null || sysctl -n hw.ncpu 2>/dev/null || echo 4)"

  local built_bin
  built_bin="$(find "$LLAMA_CPP_DIR/build" -type f -name llama-server -perm -111 | head -n 1)"
  if [[ -z "$built_bin" ]]; then
    fail "llama-server build finished but binary was not found"
  fi
  if ! llama_server_supports_hf "$built_bin"; then
    fail "built llama-server does not advertise Hugging Face loading; check LLAMA_CURL/CMake output"
  fi

  ln -sf "$built_bin" "$LLAMA_SERVER_BIN"
  log "llama-server check passed: $LLAMA_SERVER_BIN"
}

running_from_pid_file() {
  local file="$1"
  [[ -f "$file" ]] || return 1
  local pid
  pid="$(cat "$file" 2>/dev/null || true)"
  if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
    return 0
  fi
  rm -f "$file"
  return 1
}

start_monitor_detached() {
  if running_from_pid_file "$MONITOR_PID_FILE"; then
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
  export LLAMA_CACHE
  export INFERENCE_ENGINE_ROOT="$ROOT_DIR"

  if running_from_pid_file "$PID_FILE"; then
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
    sleep "$BOOTSTRAP_STARTUP_CHECK_SECONDS"
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

require_command python3
install_llama_swap
install_llama_cpp
start_engine
