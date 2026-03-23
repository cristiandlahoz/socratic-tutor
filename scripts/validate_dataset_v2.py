from __future__ import annotations

import csv
import json
import re
from collections import Counter, defaultdict
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
OUT_DIR = ROOT / "data" / "finetune"
SOURCE_FILE = OUT_DIR / "dataset_v2_source.jsonl"
TRAIN_FILE = OUT_DIR / "dataset_v2_train.jsonl"
EVAL_FILE = OUT_DIR / "dataset_v2_eval.jsonl"
TEST_FILE = OUT_DIR / "dataset_v2_test.jsonl"
MANIFEST_FILE = OUT_DIR / "dataset_v2_manifest.csv"

EXPECTED_STRATEGY = {
    "explicar primero": 420,
    "corregir misconception": 360,
    "preguntar primero": 300,
    "boundary/refocus": 120,
}
EXPECTED_UNITS = {"III": 270, "IV": 330, "V": 210, "VI": 270, "boundary": 120}
EXPECTED_FORMAT = {"single_turn": 960, "multi_turn_short": 240}
EXPECTED_LANGUAGE = {"es": 1140, "en": 60}
EXPECTED_SPLITS = {"train": 960, "eval": 120, "test": 120}
MAX_OPENER_REPETITION = 24


def load_jsonl(path: Path) -> list[dict[str, object]]:
    return [json.loads(line) for line in path.open(encoding="utf-8")]


def first_sentence(text: str) -> str:
    return re.split(r"(?<=[.!?])\s+", text.strip())[0]


def contains_explicit_correction(text: str) -> bool:
    markers = [
        "confusión",
        "corregir",
        "correcto",
        "ojo con eso",
        "watch that assumption",
        "mix-up",
        "correction",
        "sounds plausible, but",
        "lo correcto",
        "hay un detalle",
        "ajustar",
        "false rule",
        "blended there",
        "conclusión no",
        "let's correct the base",
        "two ideas got blended",
        "that conclusion starts",
        "esa idea suena razonable",
        "ese paso está bien encaminado",
        "esa mezcla de conceptos",
        "lo que está fallando",
        "aquí el problema",
        "vamos a corregir la base",
    ]
    lowered = text.lower()
    return any(marker in lowered for marker in markers)


def validate_structure(source_records: list[dict[str, object]], split_records: dict[str, list[dict[str, object]]]) -> dict[str, object]:
    lengths = Counter(len(record["messages"]) for record in source_records)
    if lengths != Counter({2: 960, 4: 240}):
        raise ValueError(f"Unexpected source message-length distribution: {dict(lengths)}")

    for record in source_records:
        if record["messages"][-1]["role"] != "assistant":
            raise ValueError(f"{record['id']} does not end with assistant")

    for split_name, records in split_records.items():
        if len(records) != EXPECTED_SPLITS[split_name]:
            raise ValueError(f"{split_name} count mismatch: {len(records)}")
        for record in records:
            if record["messages"][0]["role"] != "system":
                raise ValueError(f"{record['id']} in {split_name} is missing system prompt")
            if len(record["messages"]) not in {3, 5}:
                raise ValueError(f"{record['id']} in {split_name} has invalid length {len(record['messages'])}")
            if record["messages"][-1]["role"] != "assistant":
                raise ValueError(f"{record['id']} in {split_name} does not end with assistant")

    return {"source_lengths": dict(lengths)}


def validate_manifest(rows: list[dict[str, str]]) -> dict[str, object]:
    counts = {
        "strategy": Counter(row["strategy"] for row in rows),
        "unit": Counter(row["unit"] for row in rows),
        "format": Counter(row["format"] for row in rows),
        "language": Counter(row["language"] for row in rows),
    }
    if dict(counts["strategy"]) != EXPECTED_STRATEGY:
        raise ValueError(f"Strategy distribution mismatch: {dict(counts['strategy'])}")
    if dict(counts["unit"]) != EXPECTED_UNITS:
        raise ValueError(f"Unit distribution mismatch: {dict(counts['unit'])}")
    if dict(counts["format"]) != EXPECTED_FORMAT:
        raise ValueError(f"Format distribution mismatch: {dict(counts['format'])}")
    if dict(counts["language"]) != EXPECTED_LANGUAGE:
        raise ValueError(f"Language distribution mismatch: {dict(counts['language'])}")
    return {key: dict(value) for key, value in counts.items()}


def validate_style(source_records: list[dict[str, object]], manifest_rows: dict[str, dict[str, str]]) -> dict[str, object]:
    opener_counter = Counter()
    findings = defaultdict(list)

    for record in source_records:
        rid = record["id"]
        meta = manifest_rows[rid]
        assistant = record["messages"][1]["content"]
        sentence = first_sentence(assistant)
        opener_counter[" ".join(assistant.split()[:4]).lower()] += 1

        if meta["strategy"] == "explicar primero" and "?" in sentence:
            findings["explain_starts_with_question"].append(rid)
        if meta["strategy"] == "corregir misconception":
            early_text = " ".join(re.split(r"(?<=[.!?])\s+", assistant.strip())[:2])
            if not contains_explicit_correction(early_text):
                findings["implicit_misconception_correction"].append(rid)
        if assistant.count("?") > 2:
            findings["too_many_questions"].append(rid)
        if re.search(r"\bahi\b|\bdespues\b|\bporq\b|\bpa\b|\btoy\b|\bna\b|\btá\b", assistant.lower()):
            findings["orthography_or_slang"].append(rid)

        user = record["messages"][0]["content"]
        if any(
            phrase in user
            for phrase in [
                "qué papel juega los ",
                "qué papel juega las ",
                "va por un lado",
            ]
        ):
            findings["awkward_user_template"].append(rid)

    max_opener_count = max(opener_counter.values())
    if max_opener_count > MAX_OPENER_REPETITION:
        findings["opener_repetition_exceeded"].append(str(max_opener_count))

    return {
        "findings": {key: values[:20] for key, values in findings.items()},
        "counts": {key: len(values) for key, values in findings.items()},
        "max_four_word_opener_count": max_opener_count,
        "top_openers": opener_counter.most_common(10),
    }


def main() -> None:
    source_records = load_jsonl(SOURCE_FILE)
    split_records = {
        "train": load_jsonl(TRAIN_FILE),
        "eval": load_jsonl(EVAL_FILE),
        "test": load_jsonl(TEST_FILE),
    }
    manifest_rows_list = list(csv.DictReader(MANIFEST_FILE.open(encoding="utf-8")))
    manifest_rows = {row["id"]: row for row in manifest_rows_list}

    structure = validate_structure(source_records, split_records)
    manifest = validate_manifest(manifest_rows_list)
    style = validate_style(source_records, manifest_rows)

    report = {
        "structure": structure,
        "manifest": manifest,
        "style": style,
    }
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
