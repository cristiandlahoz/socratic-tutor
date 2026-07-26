SHELL := /bin/bash
.NOTPARALLEL:

ROOT_DIR := $(dir $(abspath $(lastword $(MAKEFILE_LIST))))
TOFU_DIR := $(ROOT_DIR)tofu
ANSIBLE_DIR := $(ROOT_DIR)ansible
ENV_FILE ?= $(ROOT_DIR).env
TFVARS ?= terraform.tfvars

.PHONY: check deps init fmt validate plan apply provision up status ssh tunnel logs down

check:
	@command -v tofu >/dev/null || { echo "missing: OpenTofu (tofu)" >&2; exit 1; }
	@test -f "$(ENV_FILE)" || { echo "copy .env.example to .env and add DIGITALOCEAN_TOKEN" >&2; exit 1; }
	@test -f "$(TOFU_DIR)/$(TFVARS)" || { echo "copy tofu/terraform.tfvars.example to tofu/$(TFVARS) and set your CIDRs/key" >&2; exit 1; }
	@set -a; source "$(ENV_FILE)"; set +a; \
		test -n "$${DIGITALOCEAN_TOKEN:-}" || { echo "DIGITALOCEAN_TOKEN is empty" >&2; exit 1; }

deps:
	@command -v ansible-galaxy >/dev/null || { echo "missing: ansible-galaxy" >&2; exit 1; }
	@ansible-galaxy collection install -r "$(ANSIBLE_DIR)/requirements.yml"

init: check
	@set -a; source "$(ENV_FILE)"; set +a; tofu -chdir="$(TOFU_DIR)" init

fmt:
	@tofu -chdir="$(TOFU_DIR)" fmt -recursive

validate: init
	@tofu -chdir="$(TOFU_DIR)" validate

plan: init
	@set -a; source "$(ENV_FILE)"; set +a; \
		tofu -chdir="$(TOFU_DIR)" plan -var-file="$(TFVARS)" -out=inference.tfplan

apply: init
	@set -a; source "$(ENV_FILE)"; set +a; \
		tofu -chdir="$(TOFU_DIR)" apply inference.tfplan

provision: check deps
	@command -v ansible-playbook >/dev/null || { echo "missing: ansible-playbook" >&2; exit 1; }
	@set -euo pipefail; \
	set -a; source "$(ENV_FILE)"; set +a; \
	host="$$(tofu -chdir="$(TOFU_DIR)" output -raw public_ip)"; \
	volume="$$(tofu -chdir="$(TOFU_DIR)" output -raw volume_name)"; \
	ANSIBLE_CONFIG="$(ANSIBLE_DIR)/ansible.cfg" ansible-playbook \
		-i "$${host}," \
		-u root \
		--extra-vars "inference_volume_name=$${volume}" \
		"$(ANSIBLE_DIR)/playbook.yml"

up: plan apply provision

status:
	@tofu -chdir="$(TOFU_DIR)" output

ssh:
	@host="$$(tofu -chdir="$(TOFU_DIR)" output -raw public_ip)"; ssh "root@$${host}"

tunnel:
	@host="$$(tofu -chdir="$(TOFU_DIR)" output -raw public_ip)"; \
		echo "Tunneling llama-swap to localhost:8080 and Ollama to localhost:11434 via $${host}"; \
		ssh -N \
			-L 8080:127.0.0.1:8080 \
			-L 11434:127.0.0.1:11434 \
			"root@$${host}"

logs:
	@host="$$(tofu -chdir="$(TOFU_DIR)" output -raw public_ip)"; ssh "root@$${host}" journalctl -fu socratic-inference.service

# GPU Droplets remain billable while powered off. This intentionally destroys
# only the compute node; the protected block volume keeps models/build caches.
down: check
	@set -a; source "$(ENV_FILE)"; set +a; \
		tofu -chdir="$(TOFU_DIR)" destroy \
		-var-file="$(TFVARS)" \
		-target=digitalocean_droplet.inference
