SHELL := /bin/bash

INFERENCE_ENGINE_DIR := $(dir $(abspath $(lastword $(MAKEFILE_LIST))))
REMOTE_DIR ?= ~/inference-engine
CONNECT ?=
ARGS ?=
RUN ?= false
DETACHED ?= true

.PHONY: bootswap-inference-engine bootswap-inference-engine-runpod

bootswap-inference-engine:
	@$(MAKE) --no-print-directory _bootswap BOOTSTRAP=bootstrap-inference_engine.sh

bootswap-inference-engine-runpod:
	@$(MAKE) --no-print-directory _bootswap BOOTSTRAP=bootstrap-inference_engine-runpod.sh

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
	ssh_opts=("$${connect_parts[@]:1}"); \
	echo "Copying inference engine files to $${remote_host}:$(REMOTE_DIR)"; \
	remote_prepare='remote_dir='"'"'$(REMOTE_DIR)'"'"'; case "$$remote_dir" in "~") remote_dir="$$HOME" ;; "~/"*) remote_dir="$$HOME/$${remote_dir#~/}" ;; esac; rm -rf "$$remote_dir"; mkdir -p "$$remote_dir"; tar -xzf - -C "$$remote_dir"'; \
	tar -C "$(INFERENCE_ENGINE_DIR)" -czf - . | ssh "$${ssh_opts[@]}" "$${remote_host}" "$${remote_prepare}"; \
	if [[ "$${run_requested}" == "true" ]]; then \
	  echo "Starting $(BOOTSTRAP) on $${remote_host}"; \
	  if [[ "$(DETACHED)" == "true" ]]; then remote_cmd='remote_dir='"'"'$(REMOTE_DIR)'"'"'; case "$$remote_dir" in "~") remote_dir="$$HOME" ;; "~/"*) remote_dir="$$HOME/$${remote_dir#~/}" ;; esac; cd "$$remote_dir" && ./$(BOOTSTRAP) -d'; else remote_cmd='remote_dir='"'"'$(REMOTE_DIR)'"'"'; case "$$remote_dir" in "~") remote_dir="$$HOME" ;; "~/"*) remote_dir="$$HOME/$${remote_dir#~/}" ;; esac; cd "$$remote_dir" && ./$(BOOTSTRAP)'; fi; \
	  ssh "$${ssh_opts[@]}" "$${remote_host}" "$${remote_cmd}"; \
	else \
	  echo "Copied only. Set RUN=true or pass ARGS='... --run true' to start $(BOOTSTRAP)."; \
	fi
