# Inference Engine

Server-side inference setup for Socratic Tutor.

It uses `llama-swap` as a single OpenAI-compatible endpoint and routes requests by the OpenAI `model` field.

## Models and purpose

### Main tutor model

- `AtomicChat/ornith-9b-GGUF:UD-Q4_K_XL`
- Context window: `8192`
- Purpose: primary tutoring conversations and higher-quality reasoning.

### Side-job model

- `unsloth/gemma-4-E4B-it-GGUF:IQ4_XS`
- Context window: `4096`
- Purpose: low/medium-stakes support jobs:
  - C runner scaffolding / example preparation
  - Chat title/name generation
  - Guardrails / safety classification

## GPU / CPU handling

Each backend is started through `start-llama-server.sh`, which checks free VRAM at startup:

- If enough VRAM is free, it starts with GPU offload: `-ngl 99`.
- If not enough VRAM is free, it falls back to CPU: `-ngl 0` and disables flash attention.

Current thresholds:

- Ornith: requires `7000 MB` free VRAM for GPU offload.
- Gemma: requires `4000 MB` free VRAM for GPU offload.

## Bootstrap / daily run

On the inference server:

```bash
cd ~/llamacpp
./bootstrap-inference_engine.sh -d
```

The `-d` flag starts the engine detached with `nohup`, so it keeps running after the SSH session closes.

The bootstrap script:

1. Checks `llama-swap`; downloads it if missing.
2. Checks `llama-server`; builds llama.cpp with CUDA and CURL/OpenSSL support if missing.
3. Starts the inference engine.
4. Starts `inference-engine-monitor.sh` in detached mode.

The monitor handles CPU → GPU promotion. If Ornith was started on CPU because VRAM was busy, the monitor periodically checks free VRAM. When enough VRAM becomes available, it unloads Ornith from llama-swap and sends a tiny warm-up request so llama-swap restarts it; `start-llama-server.sh` then re-checks VRAM and starts it on GPU.

Logs and PID:

```bash
~/llamacpp/inference-engine.log
~/llamacpp/inference-engine.pid
~/llamacpp/inference-engine-monitor.log
~/llamacpp/inference-engine-monitor.pid
```

## Spring AI configuration

Spring AI uses one base URL and selects the backend with the model name.

```bash
OPENAI_BASE_URL=http://127.0.0.1:8080/v1
CHAT_MODEL=AtomicChat/ornith-9b-GGUF:UD-Q4_K_XL
GUARD_MODEL=unsloth/gemma-4-E4B-it-GGUF:IQ4_XS
TITLE_MODEL=unsloth/gemma-4-E4B-it-GGUF:IQ4_XS
C_EXAMPLE_PREPARATION_MODEL=unsloth/gemma-4-E4B-it-GGUF:IQ4_XS
```
