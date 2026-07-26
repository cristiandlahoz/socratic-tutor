# DigitalOcean GPU inference

OpenTofu creates one 48 GB GPU Droplet, a private VPC, firewall, and a protected
persistent block volume. Ansible then installs and starts:

- `llama.cpp` with CUDA and Hugging Face HTTPS support;
- `llama-swap` with the existing Ornith and Gemma configuration;
- the GPU promotion monitor;
- Ollama with `qwen3-embedding:0.6b`.

All services survive reboot through systemd. Models, llama.cpp builds, Ollama
data, and caches live on the block volume, so `make down` can destroy expensive
GPU compute without throwing away downloads.

## Recommended machine

Use `gpu-l40sx1-48gb` in `tor1`: one NVIDIA L40S with 48 GB VRAM, 64 GiB RAM,
8 vCPUs, and a 500 GiB boot disk. The configured GGUF weights total roughly
11.1 GB. Even with both 20k contexts, KV caches, CUDA overhead, and Ollama's
small embedding model, 48 GB leaves comfortable operating headroom.

`gpu-6000adax1-48gb` is the fallback. It costs the same and has the same VRAM,
but L40S is the cleaner inference choice.

DigitalOcean bills a GPU Droplet while it is powered off. Use `make down` when
idle; it destroys only the Droplet. The volume has `prevent_destroy = true`.

## DigitalOcean token setup

Create a scoped DigitalOcean API token. Then:

```bash
cd ops/inference-engine/digitalocean
cp .env.example .env
```

Paste the token into `.env` and restrict the file permissions:

```dotenv
DIGITALOCEAN_TOKEN="dop_v1_your_token"
```

```bash
chmod 600 .env
```

The Makefile exports it only for OpenTofu commands. `.env` is git-ignored and
the provider reads `DIGITALOCEAN_TOKEN` directly, so the token does not enter
OpenTofu configuration or state.

## Configure and create

Prerequisites: `tofu`, Ansible, and an SSH key already uploaded to DigitalOcean.

```bash
cp tofu/terraform.tfvars.example tofu/terraform.tfvars
```

Set:

- `ssh_key_name` to the existing DigitalOcean SSH key name.

That is the only required infrastructure value. SSH is reachable publicly on
port 22 with key authentication; llama-swap and Ollama remain closed. Leave
`inference_client_cidrs` at its empty default, so ports 8080 and 11434 have no
public firewall rules.

For a production app already running on DigitalOcean, additionally set
`existing_vpc_uuid` to its `tor1` VPC UUID and `inference_client_cidrs` to that
VPC CIDR.

Then run:

```bash
make validate
make plan
make apply
make provision
```

Or run `make up` for the same sequence. Provisioning builds llama.cpp, starts
both configured llama-swap models (the warm-up requests download them), starts
Ollama, and pulls `qwen3-embedding:0.6b`. The first run can take 15–30 minutes.

Useful commands:

```bash
make status
make ssh
make tunnel
make logs
make down
```

If a recreated Droplet reuses an IP and SSH reports a changed host key, remove
only that old IP entry from `~/.ssh/known_hosts`, verify the new fingerprint in
the DigitalOcean console, and provision again.

## How the application should connect

Best setup: run the Spring application in DigitalOcean `tor1` and attach it to
the same VPC. Use the private output values:

```bash
tofu -chdir=tofu output -raw openai_base_url
tofu -chdir=tofu output -raw ollama_base_url
```

Configure the app with:

```dotenv
OPENAI_BASE_URL=http://PRIVATE_IP:8080/v1
OLLAMA_BASE_URL=http://PRIVATE_IP:11434
```

Only SSH uses the public IP. Ports 8080 and 11434 are limited by the
DigitalOcean firewall to `inference_client_cidrs`; llama-server child ports bind
to loopback and are never exposed.

For local development, let the Makefile read the DigitalOcean-returned public IP
and create both forwards:

```bash
make tunnel
```

If the app stays outside DigitalOcean in production, use Tailscale or WireGuard
between the app and inference host instead of relying on a long-lived SSH
tunnel.

Do not publish either inference API to `0.0.0.0/0`. Neither llama-swap nor
Ollama is an authentication boundary.

## Sources

- [DigitalOcean provider authentication](https://docs.digitalocean.com/reference/terraform/reference/)
- [DigitalOcean GPU Droplet features](https://docs.digitalocean.com/products/droplets/details/features/)
- [GPU plan availability](https://docs.digitalocean.com/products/droplets/details/gpu-availability/)
- [Recommended NVIDIA GPU image](https://docs.digitalocean.com/products/droplets/getting-started/recommended-gpu-setup/)
- [Block storage pricing](https://docs.digitalocean.com/products/volumes/details/pricing/)
