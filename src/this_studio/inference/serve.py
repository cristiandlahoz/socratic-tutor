"""
inference/serve.py
------------------
FastAPI wrapper that serves the fine-tuned LoRA model.

Endpoints:
    POST /v1/chat          – single-turn or multi-turn generation
    GET  /v1/health        – liveness probe

Environment variables (see .env.example):
    MODEL_PATH     – path to saved LoRA adapters  (default: outputs/socratic-tutor-lora)
    MAX_NEW_TOKENS – max generation tokens         (default: 512)
    TEMPERATURE    – sampling temperature          (default: 0.7)
    TOP_P          – nucleus sampling              (default: 0.9)
    PORT           – server port                   (default: 8001)

Run:
    uvicorn this_studio.inference.serve:app --host 0.0.0.0 --port 8001
    # or via Docker Compose: make up
"""

from __future__ import annotations

import os
from pathlib import Path
from typing import List, Literal

import torch
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

# ---------------------------------------------------------------------------
# Config
# ---------------------------------------------------------------------------

MODEL_PATH = Path(os.getenv("MODEL_PATH", "outputs/socratic-tutor-lora"))
MAX_NEW_TOKENS = int(os.getenv("MAX_NEW_TOKENS", "512"))
TEMPERATURE = float(os.getenv("TEMPERATURE", "0.7"))
TOP_P = float(os.getenv("TOP_P", "0.9"))

SYSTEM_PROMPT = (
    "Eres un tutor socrático de programación en lenguaje C. "
    "Tu objetivo no es dar respuestas directas, sino guiar al estudiante "
    "mediante preguntas que lo lleven a descubrir el concepto por sí mismo. "
    "Usa ejemplos del mundo real antes de introducir código. "
    "Valida cada respuesta del estudiante antes de avanzar."
)

# ---------------------------------------------------------------------------
# Pydantic schemas
# ---------------------------------------------------------------------------


class ChatMessage(BaseModel):
    role: Literal["system", "user", "assistant"]
    content: str


class ChatRequest(BaseModel):
    messages: List[ChatMessage] = Field(
        ...,
        description="Conversation history. If no system message is present one will be prepended automatically.",
    )
    max_new_tokens: int = Field(MAX_NEW_TOKENS, ge=1, le=2048)
    temperature: float = Field(TEMPERATURE, ge=0.01, le=2.0)
    top_p: float = Field(TOP_P, ge=0.01, le=1.0)


class ChatResponse(BaseModel):
    role: str = "assistant"
    content: str


# ---------------------------------------------------------------------------
# App & lifespan
# ---------------------------------------------------------------------------

app = FastAPI(
    title="Socratic Tutor — Inference API",
    description="Serves a LoRA fine-tuned LLaMA 3.1 8B as a Socratic C tutor.",
    version="1.0.0",
)

# Module-level model/tokenizer — loaded once on startup
_model = None
_tokenizer = None


@app.on_event("startup")
def load_model() -> None:
    global _model, _tokenizer

    if not torch.cuda.is_available():
        raise RuntimeError("No CUDA GPU detected. The inference server requires a GPU.")

    if not MODEL_PATH.exists():
        raise RuntimeError(
            f"Model not found at {MODEL_PATH}. Fine-tune first: make train"
        )

    # Deferred import — unsloth only available on GPU instances
    from unsloth import FastLanguageModel  # type: ignore[import-untyped]
    from unsloth.chat_templates import get_chat_template  # type: ignore[import-untyped]

    print(f"Loading LoRA adapters from {MODEL_PATH} …")
    _model, _tokenizer = FastLanguageModel.from_pretrained(
        model_name=str(MODEL_PATH),
        max_seq_length=2048,
        dtype=None,
        load_in_4bit=True,
    )
    _tokenizer = get_chat_template(_tokenizer, chat_template="llama-3.1")
    FastLanguageModel.for_inference(_model)  # 2x faster inference
    print("Model ready.")


# ---------------------------------------------------------------------------
# Routes
# ---------------------------------------------------------------------------


@app.get("/v1/health")
def health() -> dict:
    return {"status": "ok", "model_loaded": _model is not None}


@app.post("/v1/chat", response_model=ChatResponse)
def chat(request: ChatRequest) -> ChatResponse:
    if _model is None or _tokenizer is None:
        raise HTTPException(status_code=503, detail="Model not loaded yet.")

    messages = [m.model_dump() for m in request.messages]

    # Prepend system prompt if absent
    if not messages or messages[0]["role"] != "system":
        messages = [{"role": "system", "content": SYSTEM_PROMPT}] + messages

    inputs = _tokenizer.apply_chat_template(
        messages,
        tokenize=True,
        add_generation_prompt=True,
        return_tensors="pt",
    ).to("cuda")

    with torch.no_grad():
        outputs = _model.generate(
            input_ids=inputs,
            max_new_tokens=request.max_new_tokens,
            temperature=request.temperature,
            top_p=request.top_p,
            repetition_penalty=1.1,
            do_sample=True,
        )

    # Decode only the newly generated tokens
    new_tokens = outputs[0][inputs.shape[-1] :]
    text = _tokenizer.decode(new_tokens, skip_special_tokens=True)

    return ChatResponse(content=text.strip())
