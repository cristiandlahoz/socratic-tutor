"""
pipeline/to_jsonl.py
--------------------
Stage 3: Convert all structured JSON conversations (real + synthetic)
into a single Unsloth-ready JSONL file.

Input:  data/parsed/*.json  +  data/synthetic/*.json
Output: data/dataset.jsonl
"""

from __future__ import annotations

import json
from pathlib import Path


def convert(parsed_dir: Path, synthetic_dir: Path, output_path: Path) -> None:
    sources = sorted(parsed_dir.glob("*.json")) + sorted(synthetic_dir.glob("*.json"))
    total_turns = 0
    output_path.parent.mkdir(parents=True, exist_ok=True)

    with output_path.open("w", encoding="utf-8") as out:
        for src in sources:
            data = json.loads(src.read_text(encoding="utf-8"))
            # Accept both {"conversations": [...]} and bare list
            if isinstance(data, dict) and "conversations" in data:
                record = data
            elif isinstance(data, list):
                record = {"conversations": data}
            else:
                print(f"  SKIP {src.name}: unexpected format")
                continue
            out.write(json.dumps(record, ensure_ascii=False) + "\n")
            total_turns += len(record["conversations"])

    total_convos = len(sources)
    print(f"Exported {total_convos} conversations, {total_turns} turns → {output_path}")


if __name__ == "__main__":
    root = Path(__file__).resolve().parents[3]
    convert(
        parsed_dir=root / "data" / "parsed",
        synthetic_dir=root / "data" / "synthetic",
        output_path=root / "data" / "dataset.jsonl",
    )
