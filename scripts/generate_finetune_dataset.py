from __future__ import annotations

import argparse
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
SOURCE_DIR = ROOT / "data" / "finetune" / "synthetic"
OUTPUT_FILE = ROOT / "data" / "finetune" / "dataset.jsonl"
ALLOWED_ROLES = {"system", "user", "assistant"}


def load_conversations(path: Path) -> list[dict[str, str]]:
    with path.open("r", encoding="utf-8") as handle:
        payload = json.load(handle)

    conversations = payload.get("conversations")
    if not isinstance(conversations, list) or not conversations:
        raise ValueError(f"{path} is missing a non-empty 'conversations' list")

    normalized: list[dict[str, str]] = []
    for index, message in enumerate(conversations, start=1):
        if not isinstance(message, dict):
            raise ValueError(f"{path} message #{index} is not an object")

        role = message.get("role")
        content = message.get("content")

        if role not in ALLOWED_ROLES:
            raise ValueError(f"{path} message #{index} has invalid role: {role!r}")
        if not isinstance(content, str) or not content.strip():
            raise ValueError(f"{path} message #{index} has empty or invalid content")

        normalized.append({"role": role, "content": content})

    return normalized


def iter_source_files() -> list[Path]:
    return sorted(SOURCE_DIR.glob("*.json"))


def build_dataset(write_output: bool) -> int:
    source_files = iter_source_files()
    if not source_files:
        raise FileNotFoundError(f"No JSON files found in {SOURCE_DIR}")

    records = [{"conversations": load_conversations(path)} for path in source_files]

    if write_output:
        OUTPUT_FILE.parent.mkdir(parents=True, exist_ok=True)
        with OUTPUT_FILE.open("w", encoding="utf-8", newline="\n") as handle:
            for record in records:
                handle.write(json.dumps(record, ensure_ascii=False))
                handle.write("\n")

    return len(source_files)


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Build data/finetune/dataset.jsonl from synthetic conversation sources."
    )
    parser.add_argument(
        "--check",
        action="store_true",
        help="Validate source files without writing dataset.jsonl",
    )
    args = parser.parse_args()

    count = build_dataset(write_output=not args.check)
    if args.check:
        print(f"Validated {count} conversations from {SOURCE_DIR}")
    else:
        print(f"Wrote {count} conversations to {OUTPUT_FILE}")


if __name__ == "__main__":
    main()
