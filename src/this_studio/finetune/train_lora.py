"""
finetune/train_lora.py
----------------------
Graduated from notebooks/03-finetune-unsloth.ipynb.

Fine-tunes LLaMA 3.1 8B (4-bit QLoRA) on data/dataset.jsonl using Unsloth.
Target: Socratic tutor for C programming in Spanish.

Run on a GPU instance (Colab A100 / RunPod / Lambda Labs):
    python -m this_studio.finetune.train_lora

Environment variables (see .env.example):
    DATASET_PATH   – path to JSONL file         (default: data/dataset.jsonl)
    OUTPUT_DIR     – checkpoint + adapter dir   (default: outputs/socratic-tutor-lora)
    MAX_SEQ_LENGTH – token window               (default: 2048)
    NUM_EPOCHS     – training epochs            (default: 3)
    REPORT_TO      – wandb | none               (default: none)
"""

from __future__ import annotations

import os
from pathlib import Path

import torch

# ---------------------------------------------------------------------------
# Config (override via environment variables)
# ---------------------------------------------------------------------------

DATASET_PATH = Path(os.getenv("DATASET_PATH", "data/dataset.jsonl"))
OUTPUT_DIR = Path(os.getenv("OUTPUT_DIR", "outputs/socratic-tutor-lora"))
MAX_SEQ_LENGTH = int(os.getenv("MAX_SEQ_LENGTH", "2048"))
NUM_EPOCHS = int(os.getenv("NUM_EPOCHS", "3"))
REPORT_TO = os.getenv("REPORT_TO", "none")

LORA_RANK = 16
LORA_ALPHA = 16
LOAD_IN_4BIT = True
DTYPE = None  # auto-detect: bfloat16 on Ampere+, float16 elsewhere

SYSTEM_PROMPT = (
    "Eres un tutor socrático de programación en lenguaje C. "
    "Tu objetivo no es dar respuestas directas, sino guiar al estudiante "
    "mediante preguntas que lo lleven a descubrir el concepto por sí mismo. "
    "Usa ejemplos del mundo real antes de introducir código. "
    "Valida cada respuesta del estudiante antes de avanzar."
)


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


def _check_gpu() -> None:
    if not torch.cuda.is_available():
        raise RuntimeError("No CUDA GPU detected. Run this script on a GPU instance.")
    props = torch.cuda.get_device_properties(0)
    vram = props.total_memory / 1e9
    print(f"GPU : {props.name}")
    print(f"VRAM: {vram:.1f} GB")


def _formatting_func(examples: dict, tokenizer) -> dict:
    """Convert each conversation list → single formatted string."""
    texts = []
    for conversation in examples["conversations"]:
        text = tokenizer.apply_chat_template(
            conversation,
            tokenize=False,
            add_generation_prompt=False,
        )
        texts.append(text)
    return {"text": texts}


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------


def train() -> None:
    _check_gpu()

    # ── Imports (deferred so the module is importable without GPU deps) ────────
    from datasets import load_dataset  # type: ignore[import-untyped]
    from trl import SFTConfig, SFTTrainer  # type: ignore[import-untyped]
    from unsloth import FastLanguageModel  # type: ignore[import-untyped]
    from unsloth.chat_templates import (  # type: ignore[import-untyped]
        get_chat_template,
        train_on_responses_only,
    )

    # ── 1. Load base model ─────────────────────────────────────────────────────
    print("\n[1/5] Loading base model …")
    model, tokenizer = FastLanguageModel.from_pretrained(
        model_name="unsloth/Meta-Llama-3.1-8B-Instruct",
        max_seq_length=MAX_SEQ_LENGTH,
        dtype=DTYPE,
        load_in_4bit=LOAD_IN_4BIT,
    )
    tokenizer = get_chat_template(tokenizer, chat_template="llama-3.1")
    print("Base model loaded.")

    # ── 2. Attach LoRA adapters ────────────────────────────────────────────────
    print("\n[2/5] Attaching LoRA adapters …")
    model = FastLanguageModel.get_peft_model(
        model,
        r=LORA_RANK,
        target_modules=[
            "q_proj",
            "k_proj",
            "v_proj",
            "o_proj",
            "gate_proj",
            "up_proj",
            "down_proj",
        ],
        lora_alpha=LORA_ALPHA,
        lora_dropout=0,  # optimal for Unsloth
        bias="none",
        use_gradient_checkpointing="unsloth",
        random_state=42,
        use_rslora=False,
        loftq_config=None,
    )
    model.print_trainable_parameters()

    # ── 3. Load & format dataset ───────────────────────────────────────────────
    print(f"\n[3/5] Loading dataset from {DATASET_PATH} …")
    if not DATASET_PATH.exists():
        raise FileNotFoundError(
            f"Dataset not found at {DATASET_PATH}. "
            "Run the pipeline stages first:\n"
            "  make pipeline"
        )
    raw_dataset = load_dataset("json", data_files=str(DATASET_PATH), split="train")
    print(f"Total examples : {len(raw_dataset)}")
    print(f"Columns        : {raw_dataset.column_names}")

    dataset = raw_dataset.map(
        lambda ex: _formatting_func(ex, tokenizer),
        batched=True,
    )

    # ── 4. Train ───────────────────────────────────────────────────────────────
    print("\n[4/5] Setting up trainer …")
    start_vram = round(torch.cuda.max_memory_reserved() / 1024**3, 3)

    trainer = SFTTrainer(
        model=model,
        tokenizer=tokenizer,
        train_dataset=dataset,
        args=SFTConfig(
            dataset_text_field="text",
            max_seq_length=MAX_SEQ_LENGTH,
            per_device_train_batch_size=2,
            gradient_accumulation_steps=4,  # effective batch = 8
            warmup_steps=10,
            num_train_epochs=NUM_EPOCHS,
            learning_rate=2e-4,
            fp16=not torch.cuda.is_bf16_supported(),
            bf16=torch.cuda.is_bf16_supported(),
            logging_steps=5,
            optim="adamw_8bit",
            weight_decay=0.01,
            lr_scheduler_type="cosine",
            seed=42,
            output_dir=str(OUTPUT_DIR / "checkpoints"),
            report_to=REPORT_TO,
        ),
    )

    # Train only on assistant turns — ignores user/system tokens in loss
    trainer = train_on_responses_only(
        trainer,
        instruction_part="<|start_header_id|>user<|end_header_id|>\n\n",
        response_part="<|start_header_id|>assistant<|end_header_id|>\n\n",
    )

    print("Training …")
    trainer_stats = trainer.train()

    used_vram = round(torch.cuda.max_memory_reserved() / 1024**3, 3)
    print(f"\nTraining finished in {trainer_stats.metrics['train_runtime']:.0f}s")
    print(f"Peak VRAM: {used_vram} GB  ({used_vram - start_vram:.3f} GB for LoRA)")

    # ── 5. Save ─────────────────────────────────────────────────────────────────
    print(f"\n[5/5] Saving LoRA adapters → {OUTPUT_DIR} …")
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    model.save_pretrained(str(OUTPUT_DIR))
    tokenizer.save_pretrained(str(OUTPUT_DIR))
    print("Done.")

    # ── Optional: merge + save full model ─────────────────────────────────────
    # model.save_pretrained_merged(
    #     str(OUTPUT_DIR / "merged"),
    #     tokenizer,
    #     save_method="merged_16bit",
    # )

    # ── Optional: save as GGUF (llama.cpp / Ollama) ───────────────────────────
    # model.save_pretrained_gguf(
    #     str(OUTPUT_DIR / "gguf"),
    #     tokenizer,
    #     quantization_method="q4_k_m",
    # )


if __name__ == "__main__":
    train()
