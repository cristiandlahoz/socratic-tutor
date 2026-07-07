SHELL := /bin/bash

INFERENCE_ENGINE_DIR := $(dir $(abspath $(lastword $(MAKEFILE_LIST))))
REMOTE_DIR ?=
CONNECT ?=
ARGS ?=
RUN ?= false
DETACHED ?= true

.PHONY: bootswap-inference-engine bootswap-inference-engine-runpod

bootswap-inference-engine:
	@$(MAKE) --no-print-directory _bootswap BOOTSTRAP=bootstrap-inference_engine.sh DEFAULT_REMOTE_DIR='~/inference-engine'

bootswap-inference-engine-runpod:
	@$(MAKE) --no-print-directory _bootswap BOOTSTRAP=bootstrap-inference_engine-runpod.sh DEFAULT_REMOTE_DIR=/workspace/inference-engine

.PHONY: _bootswap
_bootswap:
	@set -euo pipefail; \
	connect="$(CONNECT)"; \
	run_requested="$(RUN)"; \
	if [[ -n "$(ARGS)" ]]; then \
	  read -r -a arg_parts <<< "$(ARGS)"; \
	  kept=(); \
	  i=0; \
	  while (( i < $${#arg_parts[@]} )); do \
	    if [[ "$${arg_parts[$$i]}" == "--run" ]]; then \
	      i=$$((i + 1)); \
	      run_requested="$${arg_parts[$$i]:-true}"; \
	    else \
	      kept+=("$${arg_parts[$$i]}"); \
	    fi; \
	    i=$$((i + 1)); \
	  done; \
	  if [[ -z "$${connect}" ]]; then connect="$${kept[*]}"; fi; \
	fi; \
	if [[ -z "$${connect}" ]]; then \
	  if [[ "$${run_requested}" == "true" ]]; then \
	    cd "$(INFERENCE_ENGINE_DIR)"; \
	    if [[ "$(DETACHED)" == "true" ]]; then ./$(BOOTSTRAP) -d; else ./$(BOOTSTRAP); fi; \
	  else \
	    echo "No CONNECT/ARGS provided and RUN is not true; nothing to copy or start."; \
	    echo "Examples:"; \
	    echo "  make bootswap-inference-engine CONNECT='root@host -p 1234'"; \
	    echo "  make bootswap-inference-engine-runpod CONNECT='root@host -p 1234' RUN=true"; \
	    echo "  make bootswap-inference-engine-runpod ARGS='root@host -p 1234 --run true'"; \
	  fi; \
	  exit 0; \
	fi; \
	read -r -a connect_parts <<< "$${connect}"; \
	remote_host="$${connect_parts[0]}"; \
	remote_dir_input="$(or $(REMOTE_DIR),$(DEFAULT_REMOTE_DIR))"; \
	echo "Copying inference engine files to $${remote_host}:$${remote_dir_input}"; \
	remote_resolve="remote_dir='$${remote_dir_input}'; if [[ \"\$$remote_dir\" == \"~\" ]]; then remote_dir=\"\$$HOME\"; elif [[ \"\$$remote_dir\" == \"~/\"* ]]; then remote_dir=\"\$$HOME/\$${remote_dir:2}\"; elif [[ \"\$$remote_dir\" != /* ]]; then remote_dir=\"\$$HOME/\$$remote_dir\"; fi"; \
	remote_prepare="$${remote_resolve}; mkdir -p \"\$$remote_dir\"; tar --no-same-owner -xzf - -C \"\$$remote_dir\""; \
	if (( $${#connect_parts[@]} > 1 )); then \
	  COPYFILE_DISABLE=1 tar --no-xattrs -C "$(INFERENCE_ENGINE_DIR)" -czf - . | ssh "$${connect_parts[@]:1}" "$${remote_host}" "$${remote_prepare}"; \
	else \
	  COPYFILE_DISABLE=1 tar --no-xattrs -C "$(INFERENCE_ENGINE_DIR)" -czf - . | ssh "$${remote_host}" "$${remote_prepare}"; \
	fi; \
	if [[ "$${run_requested}" == "true" ]]; then \
	  echo "Starting $(BOOTSTRAP) on $${remote_host}"; \
	  if [[ "$(DETACHED)" == "true" ]]; then remote_cmd="$${remote_resolve}; cd \"\$$remote_dir\" && ./$(BOOTSTRAP) -d"; else remote_cmd="$${remote_resolve}; cd \"\$$remote_dir\" && ./$(BOOTSTRAP)"; fi; \
	  if (( $${#connect_parts[@]} > 1 )); then ssh "$${connect_parts[@]:1}" "$${remote_host}" "$${remote_cmd}"; else ssh "$${remote_host}" "$${remote_cmd}"; fi; \
	else \
	  echo "Copied only. Set RUN=true or pass ARGS='... --run true' to start $(BOOTSTRAP)."; \
	fi
