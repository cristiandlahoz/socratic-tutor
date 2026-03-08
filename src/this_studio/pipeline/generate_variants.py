"""
pipeline/generate_variants.py
------------------------------
Stage 2: Generate synthetic dialogue variants via an LLM.

Reads the meta-prompt template and calls Ollama (or an OpenAI-compatible
endpoint) to produce N synthetic conversations per topic/domain combination.

Usage:
    python -m this_studio.pipeline.generate_variants \\
        --topic "for loop" \\
        --domain "ventas" \\
        --error "wrong initialization" \\
        --n 3
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

import ollama

# ---------------------------------------------------------------------------
# Meta-prompt template (matches planning/02.synthetic-data-generation.md)
# ---------------------------------------------------------------------------

META_PROMPT = """Eres un generador de datos de entrenamiento para un tutor socrático de programación C.

Genera un diálogo en español entre un profesor y un estudiante sobre el tema "{topic}" en C,
usando "{domain}" como analogía del mundo real.

Reglas:
- Sigue este arco: analogía del mundo real → pasos repetidos → mapear a código → introducir sintaxis → ejercicio final
- Preserva este registro informal: "Nítido!", "Exactoo", "Muy bien", "Profe"
- El profesor nunca da la respuesta directa sin antes hacer una pregunta guía
- Incluye al menos un error de tipo "{error}" que el profesor redirija sin dar la respuesta
- Formato de salida: JSON con clave "conversations" y roles: system, user, assistant

Tema: {topic}
Dominio: {domain}
Error del estudiante: {error}

Responde ÚNICAMENTE con el JSON, sin ningún texto adicional."""


def generate_variant(
    topic: str,
    domain: str,
    error: str,
    model: str = "llama3.1:8b",
) -> dict:
    prompt = META_PROMPT.format(topic=topic, domain=domain, error=error)
    response = ollama.chat(
        model=model,
        messages=[{"role": "user", "content": prompt}],
        options={"temperature": 0.8},
    )
    raw = response["message"]["content"].strip()
    # Strip markdown fences if present
    if raw.startswith("```"):
        raw = raw.split("\n", 1)[1].rsplit("```", 1)[0].strip()
    return json.loads(raw)


def run(
    topic: str,
    domain: str,
    error: str,
    n: int,
    output_dir: Path,
    model: str = "llama3.1:8b",
) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    slug = f"{topic.replace(' ', '_')}_{domain.replace(' ', '_')}"

    for i in range(n):
        print(f"  Generating {slug} #{i + 1}/{n}...")
        try:
            data = generate_variant(topic, domain, error, model)
        except (json.JSONDecodeError, KeyError) as e:
            print(f"    FAILED: {e}")
            continue
        out = output_dir / f"{slug}_{i + 1:02d}.json"
        out.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
        turns = len(data.get("conversations", []))
        print(f"    → {out.name}  ({turns} turns)")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="Generate synthetic Socratic dialogues"
    )
    parser.add_argument("--topic", required=True)
    parser.add_argument("--domain", required=True)
    parser.add_argument("--error", default="wrong initialization")
    parser.add_argument("--n", type=int, default=1)
    parser.add_argument("--model", default="llama3.1:8b")
    args = parser.parse_args()

    root = Path(__file__).resolve().parents[3]
    run(
        topic=args.topic,
        domain=args.domain,
        error=args.error,
        n=args.n,
        output_dir=root / "data" / "synthetic",
        model=args.model,
    )
