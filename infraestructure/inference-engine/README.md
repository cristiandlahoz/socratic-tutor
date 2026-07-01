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
- Context window: `2048`
- Purpose: low/medium-stakes support jobs:
  - C runner scaffolding / example preparation
  - Chat title/name generation
  - Guardrails / safety classification

## GPU / CPU handling

Each backend is started through `start-llama-server.sh`, which checks free VRAM at startup:

- If enough VRAM is free, it starts with GPU offload: `-ngl 99`.
- If not enough VRAM is free, it falls back to CPU: `-ngl 0` and disables flash attention.
- CPU threads default to `auto`: GPU-offloaded backends use a conservative share of host cores to avoid oversubscribing multiple loaded models; CPU fallback uses most available cores.
- KV cache defaults to `q8_0` for K and V to reduce VRAM use while preserving quality better than more aggressive 4-bit KV cache.
- Batch size defaults to `1024` with physical micro-batch `512` for faster prompt ingestion when VRAM allows.

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
4. Warms configured models with tiny `/v1/chat/completions` requests so the first real Spring AI request does not pay the cold-start cost.
5. Starts `inference-engine-monitor.sh` in detached mode.

The monitor handles CPU → GPU promotion for both configured models. If Ornith or Gemma was started on CPU because VRAM was busy, the monitor periodically checks free VRAM. When enough VRAM becomes available for that model, it unloads the model from llama-swap and sends a tiny warm-up request so llama-swap restarts it; `start-llama-server.sh` then re-checks VRAM and starts it on GPU.

`llama-swap`'s built-in preload hook is intentionally not used because v233 preloads with `GET /`; this returns `404` when `llama-server` is started with `--no-ui`. The engine instead warms models through the same OpenAI-compatible chat endpoint used by the application.

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
