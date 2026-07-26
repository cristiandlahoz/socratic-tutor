# Inference Engine

Server-side inference setup for Socratic Tutor.

It uses `llama-swap` as a single OpenAI-compatible endpoint and routes requests by the OpenAI `model` field.

For reproducible DigitalOcean GPU infrastructure, 1Password-backed provider
authentication, Ansible provisioning, systemd services, and Ollama setup, see
[`digitalocean/README.md`](digitalocean/README.md).

## Models and purpose

### Main tutor model

- `AtomicChat/ornith-9b-GGUF:UD-Q4_K_XL`
- Context window: `20000`
- Purpose: primary tutoring conversations and higher-quality reasoning.

### Side-job model

- `unsloth/gemma-4-E4B-it-GGUF:IQ4_XS`
- Context window: `20000`
- Purpose: low/medium-stakes support jobs:
  - C runner scaffolding / example preparation
  - Chat title/name generation
  - Guardrails / safety classification
  - Subject syllabus PDF-to-context generation

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
2. Checks `llama-server`; if it is missing or does not advertise Hugging Face `-hf` loading, builds llama.cpp with CURL support.
3. Selects the llama.cpp build backend automatically: CUDA on NVIDIA hosts, Metal on Apple Silicon, CPU otherwise. Override with `LLAMA_CPP_BACKEND=cuda|metal|cpu`.
4. Starts the inference engine from the script directory, so the relative commands in `config.yaml` do not depend on the caller's current working directory.
5. Warms configured models with tiny `/v1/chat/completions` requests. This also downloads each GGUF model into the llama.cpp cache when it is not already present.
6. Starts `inference-engine-monitor.sh` in detached mode on NVIDIA hosts.

The default model cache is:

```bash
~/.cache/llama.cpp
```

Override it with:

```bash
LLAMA_CACHE=/srv/llama-cache ./bootstrap-inference_engine.sh -d
```

## RunPod persistent setup

RunPod keeps `/workspace` persistent across pods. Use the RunPod wrapper so
llama.cpp builds, downloaded GGUF files, logs, PID files, and monitor state are
stored there instead of under an ephemeral home directory:

```bash
./bootstrap-inference_engine-runpod.sh -d
```

The wrapper sources `runpod.env`, which defaults to these persistent paths:

```bash
/workspace/bin
/workspace/llama.cpp
/workspace/llama-cache
/workspace/inference-engine
/workspace/huggingface
/workspace/.cache
```

Use `RUNPOD_WORKSPACE=/some/path` if the persistent mount is not `/workspace`,
or pass the wrapper option directly:

```bash
./bootstrap-inference_engine-runpod.sh --workspace /some/path -d
./bootstrap-inference_engine-runpod.sh --home -d
```

`--home` sets the workspace root to `$HOME`, so binaries, llama.cpp, caches,
logs, PID files, monitor state, and Hugging Face caches all stay under the home
directory.

## Lightning AI persistent setup

Lightning AI Studios usually set `$HOME` to the persistent Studio directory,
for example `/teamspace/studios/this_studio`. Confirm this first:

```bash
ssh user@ssh.lightning.ai 'echo $HOME'
```

Then copy or keep this inference-engine folder under `$HOME` and start the
RunPod wrapper with `--home`:

```bash
cd "$HOME/inference-engine"
./bootstrap-inference_engine-runpod.sh --home -d
```

With `--home`, all runtime artifacts stay under the persistent Studio home:

```bash
$HOME/bin                # llama-swap and llama-server symlink
$HOME/llama.cpp          # llama.cpp source and build output
$HOME/llama-cache        # downloaded GGUF models used by llama-server
$HOME/inference-engine   # logs, PID files, and monitor state
$HOME/huggingface        # Hugging Face cache
$HOME/.cache             # XDG cache
```

Verify the engine after startup:

```bash
curl -fsS http://127.0.0.1:8080/v1/models
```

If Lightning reports a non-persistent `$HOME`, do not use `--home`; instead pass
an explicit persistent path:

```bash
./bootstrap-inference_engine-runpod.sh --workspace /teamspace/studios/this_studio/inference-engine-state -d
```

For GPU acceleration, llama.cpp needs the CUDA Toolkit (`nvcc`) or a configured
`CUDAToolkit_ROOT`. `nvidia-smi` alone only proves that the GPU driver is
available. On NVIDIA hosts, the bootstrap tries to install a CUDA Toolkit package
with `apt-get` when `nvcc` is missing. If no toolkit package is available in the
image's configured apt repositories, `auto` mode falls back to a CPU build. Set
`AUTO_INSTALL_CUDA_TOOLKIT=false` to skip this attempt, or use a CUDA development
RunPod image when you want a guaranteed GPU build.

## Copying to a remote host

The local `Makefile` can copy this whole inference-engine folder over SSH using
`tar`, so the remote host does not need `rsync`. By default it only copies files
and does not start the engine:

```bash
make bootswap-inference-engine CONNECT='root@host -p 1234'
make bootswap-inference-engine-runpod CONNECT='root@host -p 1234'
```

Set `RUN=true` to start the selected bootstrap script after copying:

```bash
make bootswap-inference-engine-runpod CONNECT='root@host -p 1234' RUN=true
```

The same remote connection can be passed through `ARGS`, including the optional
`--run true` flag:

```bash
make bootswap-inference-engine-runpod ARGS='root@host -p 1234 --run true'
```

Files are copied to `~/inference-engine` by default for the regular target, and
to `/workspace/inference-engine` by default for the RunPod target. Override with
`REMOTE_DIR=/path/to/inference-engine` or another home-relative path.

The monitor handles CPU → GPU promotion for configured models on NVIDIA hosts. If Ornith or Gemma was started on CPU because VRAM was busy, the monitor periodically checks free VRAM. When enough VRAM becomes available for that model, it unloads the model from llama-swap and sends a tiny warm-up request so llama-swap restarts it; `start-llama-server.sh` then re-checks VRAM and starts it on GPU.

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
SWITZERLAND_KNIFE_MODEL=unsloth/gemma-4-E4B-it-GGUF:IQ4_XS
```

## Useful overrides

```bash
# Build CPU-only even on a machine where CUDA/Metal would be detected.
LLAMA_CPP_BACKEND=cpu ./bootstrap-inference_engine.sh -d

# Pin a llama.cpp ref before building.
LLAMA_CPP_REF=b1234 ./bootstrap-inference_engine.sh -d

# Start Gemma directly through the launcher alias.
./start-llama-server.sh gemma 5800

# Override Gemma context when needed. The default Gemma context is 20000.
GEMMA_CONTEXT=12000 ./start-llama-server.sh gemma 5800

# Start Ornith directly through the launcher alias.
./start-llama-server.sh ornith 5800
```
