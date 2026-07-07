#!/usr/bin/env bash
set -euo pipefail

# Bootstrap and run the inference engine.
#
# Usage:
#   ./bootstrap-inference_engine.sh      # foreground
#   ./bootstrap-inference_engine.sh -d   # detached/background; survives terminal close
#
# Main jobs:
#   1. Install Debian build/runtime dependencies when apt-get is available.
#   2. Make companion scripts executable.
#   3. Ensure llama-swap exists; download it if missing.
#   4. Ensure llama-server exists with Hugging Face and HTTPS support; build llama.cpp if needed.
#   5. Start run-inference-engine.sh, optionally detached.
#
# Useful overrides:
#   AUTO_INSTALL_DEBIAN_DEPS=true|false|auto   default: auto
#   FORCE_LLAMA_CPP_REBUILD=true|false         default: false
#   LLAMA_CPP_BUILD_JOBS=4                     default: cuda=>4, otherwise nproc
#   LLAMA_CPP_BACKEND=auto|cuda|metal|cpu      default: auto
#   LLAMA_SWAP_VERSION=233                     default: 233

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
LLAMA_CPP_BUILD_JOBS="${LLAMA_CPP_BUILD_JOBS:-}"
FORCE_LLAMA_CPP_REBUILD="${FORCE_LLAMA_CPP_REBUILD:-false}"
AUTO_INSTALL_DEBIAN_DEPS="${AUTO_INSTALL_DEBIAN_DEPS:-auto}" # auto|true|false
LLAMA_SWAP_VERSION="${LLAMA_SWAP_VERSION:-233}"
LLAMA_SWAP_BIN="${LLAMA_SWAP_BIN:-$BIN_DIR/llama-swap}"
LLAMA_SERVER_BIN="${LLAMA_SERVER_BIN:-$BIN_DIR/llama-server}"
LLAMA_CACHE="${LLAMA_CACHE:-$HOME/.cache/llama.cpp}"
LOG_FILE="${LOG_FILE:-$ROOT_DIR/inference-engine.log}"
PID_FILE="${PID_FILE:-$ROOT_DIR/inference-engine.pid}"
MONITOR_LOG_FILE="${MONITOR_LOG_FILE:-$ROOT_DIR/inference-engine-monitor.log}"
MONITOR_PID_FILE="${MONITOR_PID_FILE:-$ROOT_DIR/inference-engine-monitor.pid}"
BOOTSTRAP_STARTUP_CHECK_SECONDS="${BOOTSTRAP_STARTUP_CHECK_SECONDS:-3}"
HF_HTTPS_TEST_MODEL="${HF_HTTPS_TEST_MODEL:-this-repo/should-not-exist-for-https-test:q4_0}"

mkdir -p "$BIN_DIR" "$LLAMA_CACHE"
export PATH="$BIN_DIR:$PATH"

log() {
  printf '[bootstrap] %s\n' "$*"
}

fail() {
  printf '[bootstrap] error: %s\n' "$*" >&2
  exit 1
}

is_truthy() {
  case "${1:-}" in
    true|TRUE|1|yes|YES|y|Y|on|ON) return 0 ;;
    *) return 1 ;;
  esac
}

resolve_executable() {
  local candidate="$1"

  # Important: this function must never return 1 merely because the candidate
  # does not exist. It is called inside command substitution while set -e is on.
  if [[ "$candidate" == */* ]]; then
    if [[ -x "$candidate" ]]; then
      printf '%s\n' "$candidate"
    fi
    return 0
  fi

  command -v "$candidate" 2>/dev/null || true
  return 0
}

require_command() {
  local command_name="$1"
  if ! command -v "$command_name" >/dev/null 2>&1; then
    fail "'$command_name' is required but was not found in PATH"
  fi
}

apt_install_if_possible() {
  if ! command -v apt-get >/dev/null 2>&1; then
    return 1
  fi
  if [[ "$(id -u)" != "0" ]]; then
    return 1
  fi

  export DEBIAN_FRONTEND=noninteractive
  log "installing Debian packages: $*"
  apt-get update
  apt-get install -y --no-install-recommends "$@"
}

ensure_debian_dependencies() {
  case "$AUTO_INSTALL_DEBIAN_DEPS" in
    false|FALSE|0|no|NO|off|OFF)
      log "skipping Debian dependency installation because AUTO_INSTALL_DEBIAN_DEPS=$AUTO_INSTALL_DEBIAN_DEPS"
      return 0
      ;;
    true|TRUE|1|yes|YES|on|ON|auto)
      ;;
    *)
      fail "AUTO_INSTALL_DEBIAN_DEPS must be auto, true, or false"
      ;;
  esac

  # On Debian/Ubuntu RunPod images this prevents the exact broken build you saw:
  # llama-server advertised -hf, but could not use HTTPS because OpenSSL dev
  # files were missing when CMake configured llama.cpp.
  if command -v apt-get >/dev/null 2>&1 && [[ "$(id -u)" == "0" ]]; then
    apt_install_if_possible \
      ca-certificates \
      coreutils \
      build-essential \
      cmake \
      ninja-build \
      git \
      curl \
      tar \
      findutils \
      pkg-config \
      python3 \
      libcurl4-openssl-dev \
      libssl-dev
    return 0
  fi

  if [[ "$AUTO_INSTALL_DEBIAN_DEPS" != "auto" ]]; then
    fail "cannot auto-install Debian dependencies: apt-get unavailable or current user is not root"
  fi

  log "apt-get unavailable or not root; assuming dependencies are already installed"
}

ensure_companion_scripts() {
  local script
  for script in run-inference-engine.sh start-llama-server.sh inference-engine-monitor.sh; do
    if [[ ! -f "$ROOT_DIR/$script" ]]; then
      fail "required companion script not found: $ROOT_DIR/$script"
    fi
    chmod +x "$ROOT_DIR/$script"
  done
}

llama_server_supports_hf() {
  local candidate="$1"
  local help
  help="$($candidate --help 2>&1 || true)"
  grep -Eq -- '(^|[[:space:]])-hf([,[:space:]]|$)|--hf-repo|--hf-file' <<< "$help"
}

llama_server_supports_hf_https() {
  local candidate="$1"
  local out status

  if ! llama_server_supports_hf "$candidate"; then
    return 1
  fi

  # There is no stable --help flag that proves HTTPS was compiled in, so use a
  # short dry probe. The fake repo should fail quickly. The only fatal signal we
  # care about here is llama.cpp's explicit "HTTPS is not supported" message.
  if ! command -v timeout >/dev/null 2>&1; then
    log "timeout not found; cannot probe llama-server HTTPS support, accepting binary based on -hf only"
    return 0
  fi

  set +e
  out="$(timeout 20s "$candidate" \
    -hf "$HF_HTTPS_TEST_MODEL" \
    --host 127.0.0.1 \
    --port 59999 \
    --no-ui \
    -c 64 \
    -ngl 0 \
    2>&1)"
  status=$?
  set -e

  if grep -qi 'HTTPS is not supported' <<< "$out"; then
    return 1
  fi

  # timeout=124 means the process lived longer than the probe. That is good
  # enough for this check because the explicit no-HTTPS error did not appear.
  # Other non-zero statuses are also acceptable here unless they emitted the
  # no-HTTPS diagnostic above; an invalid fake repo is expected to fail.
  return 0
}

llama_swap_platform_candidates() {
  local os arch
  os="$(uname -s | tr '[:upper:]' '[:lower:]')"
  arch="$(uname -m)"

  case "$arch" in
    x86_64|amd64) arch="amd64" ;;
    aarch64|arm64) arch="arm64" ;;
    *) fail "unsupported architecture for llama-swap: $arch" ;;
  esac

  case "$os" in
    linux)
      printf 'linux:%s\n' "$arch"
      ;;
    darwin)
      printf 'darwin:%s\n' "$arch"
      printf 'osx:%s\n' "$arch"
      ;;
    *)
      fail "unsupported OS for llama-swap binary download: $os"
      ;;
  esac
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
    require_command install
    require_command find

    local tmp os arch archive url downloaded extracted_bin
    tmp="$(mktemp -d)"
    trap 'rm -rf "$tmp"' RETURN

    log "llama-swap not found; downloading v$LLAMA_SWAP_VERSION into $LLAMA_SWAP_BIN"

    downloaded=false
    while IFS=':' read -r os arch; do
      archive="llama-swap_${LLAMA_SWAP_VERSION}_${os}_${arch}.tar.gz"
      url="${LLAMA_SWAP_DOWNLOAD_URL:-https://github.com/mostlygeek/llama-swap/releases/download/v${LLAMA_SWAP_VERSION}/${archive}}"

      log "trying llama-swap archive: $archive"
      if curl -L --fail --show-error --retry 3 --connect-timeout 15 -o "$tmp/$archive" "$url"; then
        tar -xzf "$tmp/$archive" -C "$tmp"
        downloaded=true
        break
      fi
    done < <(llama_swap_platform_candidates)

    if [[ "$downloaded" != true ]]; then
      fail "could not download llama-swap v$LLAMA_SWAP_VERSION for this platform"
    fi

    extracted_bin="$(find "$tmp" -type f -name llama-swap | head -n 1)"
    if [[ -z "$extracted_bin" ]]; then
      fail "downloaded llama-swap archive did not contain a llama-swap binary"
    fi

    install -m 755 "$extracted_bin" "$LLAMA_SWAP_BIN"
    trap - RETURN
    rm -rf "$tmp"

    log "llama-swap installed: $LLAMA_SWAP_BIN"
  fi

  if ! "$LLAMA_SWAP_BIN" -version >/dev/null 2>&1; then
    fail "llama-swap exists but failed its version check: $LLAMA_SWAP_BIN"
  fi
  log "llama-swap check passed"
}

cuda_toolkit_available() {
  command -v nvcc >/dev/null 2>&1 \
    || [[ -n "${CUDAToolkit_ROOT:-}" ]] \
    || [[ -x /usr/local/cuda/bin/nvcc ]]
}

detect_llama_cpp_backend() {
  case "$LLAMA_CPP_BACKEND" in
    cuda)
      if ! cuda_toolkit_available; then
        fail "LLAMA_CPP_BACKEND=cuda was requested, but the CUDA Toolkit was not found. Use a CUDA development image, set CUDAToolkit_ROOT, or use LLAMA_CPP_BACKEND=cpu."
      fi
      printf 'cuda\n'
      return 0
      ;;
    metal|cpu)
      printf '%s\n' "$LLAMA_CPP_BACKEND"
      return 0
      ;;
    auto)
      ;;
    *)
      fail "unsupported LLAMA_CPP_BACKEND='$LLAMA_CPP_BACKEND'; use auto, cuda, metal, or cpu"
      ;;
  esac

  if [[ "$(uname -s)" == "Darwin" ]] && sysctl -n hw.optional.arm64 2>/dev/null | grep -q '^1$'; then
    printf 'metal\n'
  elif command -v nvidia-smi >/dev/null 2>&1 && nvidia-smi -L >/dev/null 2>&1 && cuda_toolkit_available; then
    printf 'cuda\n'
  else
    if command -v nvidia-smi >/dev/null 2>&1 && nvidia-smi -L >/dev/null 2>&1; then
      log "NVIDIA GPU detected, but CUDA Toolkit/nvcc was not found; building CPU backend. Use a CUDA development image or set LLAMA_CPP_BACKEND=cuda after installing the toolkit."
    fi
    printf 'cpu\n'
  fi
}

default_build_jobs() {
  local backend="$1"
  local cpus
  cpus="$(nproc 2>/dev/null || sysctl -n hw.ncpu 2>/dev/null || echo 4)"
  if ! [[ "$cpus" =~ ^[0-9]+$ ]]; then
    cpus=4
  fi

  if [[ "$backend" == "cuda" ]] && (( cpus > 4 )); then
    echo 4
  else
    echo "$cpus"
  fi
}

install_llama_cpp() {
  local found
  found="$(resolve_executable "$LLAMA_SERVER_BIN")"
  if [[ -z "$found" ]]; then
    found="$(command -v llama-server 2>/dev/null || true)"
  fi

  if [[ -n "$found" ]] && ! is_truthy "$FORCE_LLAMA_CPP_REBUILD"; then
    if llama_server_supports_hf_https "$found"; then
      LLAMA_SERVER_BIN="$found"
      log "llama-server found with Hugging Face HTTPS support: $LLAMA_SERVER_BIN"
      return 0
    fi
    log "llama-server found but it does not pass Hugging Face HTTPS support check; rebuilding llama.cpp"
  elif [[ -n "$found" ]]; then
    log "FORCE_LLAMA_CPP_REBUILD=$FORCE_LLAMA_CPP_REBUILD; rebuilding llama.cpp instead of using $found"
  fi

  local backend build_jobs
  backend="$(detect_llama_cpp_backend)"
  build_jobs="${LLAMA_CPP_BUILD_JOBS:-$(default_build_jobs "$backend")}"
  log "llama-server not usable; building llama.cpp backend=$backend with CURL + OpenSSL HTTPS support; jobs=$build_jobs"

  for cmd in git cmake curl find ln; do
    require_command "$cmd"
  done

  if [[ ! -e "$LLAMA_CPP_DIR" ]]; then
    git clone https://github.com/ggml-org/llama.cpp.git "$LLAMA_CPP_DIR"
  elif [[ ! -d "$LLAMA_CPP_DIR/.git" ]]; then
    fail "LLAMA_CPP_DIR exists but is not a git checkout: $LLAMA_CPP_DIR"
  fi

  if [[ -n "$LLAMA_CPP_REF" ]]; then
    git -C "$LLAMA_CPP_DIR" fetch --tags --depth 1 origin "$LLAMA_CPP_REF" || true
    git -C "$LLAMA_CPP_DIR" checkout "$LLAMA_CPP_REF"
  fi

  local cmake_args=(
    -DCMAKE_BUILD_TYPE=Release
    -DLLAMA_CURL=ON
    -DLLAMA_OPENSSL=ON
  )

  case "$backend" in
    cuda) cmake_args+=(-DGGML_CUDA=ON) ;;
    metal) cmake_args+=(-DGGML_METAL=ON) ;;
    cpu) ;;
  esac

  cmake -S "$LLAMA_CPP_DIR" -B "$LLAMA_CPP_DIR/build" "${cmake_args[@]}"
  cmake --build "$LLAMA_CPP_DIR/build" --config Release -j "$build_jobs"

  local built_bin
  built_bin="$(find "$LLAMA_CPP_DIR/build" -type f -name llama-server -perm -111 | head -n 1)"
  if [[ -z "$built_bin" ]]; then
    fail "llama-server build finished but binary was not found"
  fi
  if ! llama_server_supports_hf_https "$built_bin"; then
    fail "built llama-server does not pass Hugging Face HTTPS check; inspect CMake output for CURL/OpenSSL detection"
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
    return 0
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
    return 0
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

log "starting bootstrap"
log "root: $ROOT_DIR"
log "bin dir: $BIN_DIR"
log "llama cache: $LLAMA_CACHE"
log "detached: $DETACHED"
log "auto install Debian deps: $AUTO_INSTALL_DEBIAN_DEPS"

ensure_debian_dependencies
require_command python3
ensure_companion_scripts
install_llama_swap
install_llama_cpp
start_engine
