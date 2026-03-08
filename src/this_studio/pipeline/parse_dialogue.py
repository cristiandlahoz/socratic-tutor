"""
pipeline/parse_dialogue.py
--------------------------
Stage 1: Parse raw .txt dialogue files into structured JSON.

Input:  data/raw/*.txt
Output: data/parsed/*.json
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

# ---------------------------------------------------------------------------
# Speaker normalization
# ---------------------------------------------------------------------------

_SPEAKER_MAP = {
    "profesor": "assistant",
    "profesor responde": "assistant",
    "profesor pregunta": "assistant",
    "estudiante": "user",
    "estudiante responde": "user",
    "estudiantes": "user",
}

_SYSTEM_PROMPT = (
    "Eres un tutor socrático de programación. Tu objetivo no es dar respuestas directas, "
    "sino guiar al estudiante mediante preguntas que lo lleven a descubrir el concepto por "
    "sí mismo. Nunca reveles la solución completa de inmediato. Usa ejemplos del mundo real "
    "antes de introducir código."
)

_TURN_RE = re.compile(
    r"^(?P<speaker>Profesor(?: Responde| pregunta)?|Estudiantes?(?:: Responde)?)\s*:\s*(?P<content>.+)",
    re.IGNORECASE,
)


def _normalize_speaker(raw: str) -> str | None:
    key = raw.strip().lower()
    return _SPEAKER_MAP.get(key)


def parse_file(path: Path) -> dict:
    """
    Parse a single raw dialogue .txt file into ShareGPT JSON format.
    Returns a dict with key "conversations".
    """
    conversations = [{"role": "system", "content": _SYSTEM_PROMPT}]
    current_speaker: str | None = None
    current_lines: list[str] = []

    def flush():
        if current_speaker and current_lines:
            content = " ".join(current_lines).strip()
            if content:
                conversations.append({"role": current_speaker, "content": content})

    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line:
            continue

        m = _TURN_RE.match(line)
        if m:
            flush()
            current_speaker = _normalize_speaker(m.group("speaker"))
            current_lines = [m.group("content").strip()]
        else:
            current_lines.append(line)

    flush()
    return {"conversations": conversations}


def run(raw_dir: Path, parsed_dir: Path) -> None:
    parsed_dir.mkdir(parents=True, exist_ok=True)
    for txt in sorted(raw_dir.glob("*.txt")):
        result = parse_file(txt)
        out = parsed_dir / txt.with_suffix(".json").name
        out.write_text(
            json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8"
        )
        turns = len(result["conversations"]) - 1
        print(f"  {txt.name} → {out.name}  ({turns} turns)")


if __name__ == "__main__":
    root = Path(__file__).resolve().parents[3]
    run(root / "data" / "raw", root / "data" / "parsed")
