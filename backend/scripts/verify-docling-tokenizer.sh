#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOCK_FILE="${1:-"$ROOT_DIR/docker/docling/tokenizer-lock.json"}"
TOKENIZER_DIR="${2:-"$ROOT_DIR/docker/docling/tokenizers/qwen3-0.6b-tokenizer"}"
BOOTSTRAP_DEPS="${TOKENIZER_VERIFY_BOOTSTRAP_DEPS:-true}"

if ! command -v ollama >/dev/null 2>&1; then
  echo "ollama is required to verify the local embedding model metadata." >&2
  exit 1
fi

PYTHON_BIN="${PYTHON_BIN:-python3}"
if ! command -v "$PYTHON_BIN" >/dev/null 2>&1; then
  echo "python3 is required to verify tokenizer files." >&2
  exit 1
fi

TMP_DIR=""
cleanup() {
  if [[ -n "$TMP_DIR" && -d "$TMP_DIR" ]]; then
    rm -rf "$TMP_DIR"
  fi
}
trap cleanup EXIT

if ! "$PYTHON_BIN" - <<'PY' >/dev/null 2>&1
import transformers  # noqa: F401
PY
then
  if [[ "$BOOTSTRAP_DEPS" != "true" ]]; then
    echo "Python package transformers is missing. Set TOKENIZER_VERIFY_BOOTSTRAP_DEPS=true or install transformers." >&2
    exit 1
  fi
  TMP_DIR="$(mktemp -d)"
  "$PYTHON_BIN" -m venv "$TMP_DIR/venv"
  "$TMP_DIR/venv/bin/pip" install -q "transformers>=4.51,<5" "tokenizers>=0.21,<1"
  PYTHON_BIN="$TMP_DIR/venv/bin/python"
fi

export LOCK_FILE
export TOKENIZER_DIR
export OLLAMA_VERBOSE="$(ollama show qwen3-embedding:0.6b --verbose)"
export OLLAMA_MODELFILE="$(ollama show qwen3-embedding:0.6b --modelfile)"
export HF_HUB_OFFLINE=1
export TRANSFORMERS_OFFLINE=1

"$PYTHON_BIN" - <<'PY'
import hashlib
import json
import os
import pathlib
import re
import sys

from transformers import AutoTokenizer

lock_path = pathlib.Path(os.environ["LOCK_FILE"])
tokenizer_dir = pathlib.Path(os.environ["TOKENIZER_DIR"])
verbose = os.environ["OLLAMA_VERBOSE"]
modelfile = os.environ["OLLAMA_MODELFILE"]

lock = json.loads(lock_path.read_text())
errors = []

def require_contains(label, expected):
    if str(expected) not in verbose:
        errors.append(f"ollama metadata missing {label}={expected}")

if lock["ollamaBlobDigest"] not in modelfile:
    errors.append(f"ollama blob digest mismatch: expected {lock['ollamaBlobDigest']}")

for key, expected in lock["ollamaMetadata"].items():
    require_contains(key, expected)

for filename, expected_hash in lock["tokenizer"]["files"].items():
    path = tokenizer_dir / filename
    if not path.exists():
        errors.append(f"missing tokenizer file: {path}")
        continue
    actual = hashlib.sha256(path.read_bytes()).hexdigest()
    if actual != expected_hash:
        errors.append(f"sha256 mismatch for {filename}: expected {expected_hash}, got {actual}")

tokenizer_json_path = tokenizer_dir / "tokenizer.json"
if tokenizer_json_path.exists():
    tokenizer_json = json.loads(tokenizer_json_path.read_text())
    model = tokenizer_json.get("model", {})
    added_tokens = tokenizer_json.get("added_tokens", [])
    vocab_size = len(model.get("vocab", {}))
    merges = len(model.get("merges", []))
    total = vocab_size + len(added_tokens)
    max_token_id = max(
        [*model.get("vocab", {}).values(), *[token["id"] for token in added_tokens]]
    )
    expected = lock["tokenizer"]
    if vocab_size != expected["vocabSize"]:
        errors.append(f"vocab size mismatch: expected {expected['vocabSize']}, got {vocab_size}")
    if merges != expected["merges"]:
        errors.append(f"merge count mismatch: expected {expected['merges']}, got {merges}")
    if total != expected["totalTokens"]:
        errors.append(f"token count mismatch: expected {expected['totalTokens']}, got {total}")
    if max_token_id != expected["maxTokenId"]:
        errors.append(f"max token id mismatch: expected {expected['maxTokenId']}, got {max_token_id}")

tokenizer = AutoTokenizer.from_pretrained(
    tokenizer_dir,
    local_files_only=True,
    trust_remote_code=False,
)
for name, fixture in lock["fixtures"].items():
    actual = tokenizer.encode(fixture["text"], add_special_tokens=False)
    if actual != fixture["tokenIds"]:
        errors.append(f"fixture token ids mismatch for {name}: expected {fixture['tokenIds']}, got {actual}")

if errors:
    print("Docling tokenizer verification failed:", file=sys.stderr)
    for error in errors:
        print(f"- {error}", file=sys.stderr)
    sys.exit(1)

print(
    "Docling tokenizer verified: "
    f"{lock['tokenizer']['sourceRepo']}@{lock['tokenizer']['sourceRevision']} "
    f"matches {lock['ollamaModel']} metadata and fixture tokenization."
)
PY
