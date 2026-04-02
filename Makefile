.PHONY: help up down logs build \
        pipeline parse variants jsonl jsonl-check \
        ingest train serve export-pdf export-tex export-tex-pdf \
        lightning-temp \
        backend-dev backend-build \
        lint format test

# ── Config ─────────────────────────────────────────────────────────────────────
PYTHON     := uv run python
PYTHON_RAW := uv run --no-project python
DC         := docker compose
BACKEND    := backend
TEMP_DIR   := temp

# ── Default ────────────────────────────────────────────────────────────────────
help:
	@echo ""
	@echo "  Usage: make <target>"
	@echo ""
	@echo "  Infrastructure"
	@echo "    up              Start all services (detached)"
	@echo "    down            Stop all services"
	@echo "    logs            Tail logs for all services"
	@echo "    build           Build Docker images"
	@echo ""
	@echo "  Python pipeline"
	@echo "    pipeline        Run all pipeline stages (parse → variants → jsonl)"
	@echo "    parse           Stage 1 – parse raw .txt dialogues"
	@echo "    variants        Stage 2 – generate synthetic variants (requires Ollama)"
	@echo "    jsonl           Stage 3 – convert synthetic JSON → dataset.jsonl"
	@echo "    jsonl-check     Validate synthetic JSON before writing dataset.jsonl"
	@echo "    ingest          Chunk sources and upsert into ChromaDB"
	@echo ""
	@echo "  ML"
	@echo "    train           Fine-tune LLaMA 3.1 8B with LoRA (GPU required)"
	@echo "    serve           Run inference server locally (GPU required)"
	@echo "    export-pdf      Export a notebook to PDF with nbconvert webpdf"
	@echo "    export-tex      Export a notebook to LaTeX (.tex) with nbconvert"
	@echo "    export-tex-pdf  Export a notebook to LaTeX and compile it to PDF"
	@echo "    lightning-temp  Copy notebook + dataset v2 artifacts into temp/ for Lightning AI"
	@echo ""
	@echo "  Backend"
	@echo "    backend-dev     Run Spring Boot in dev mode (hot-reload)"
	@echo "    backend-build   Build production JAR + Vaadin bundle"
	@echo ""
	@echo "  Code quality"
	@echo "    lint            Run ruff linter"
	@echo "    format          Run ruff formatter"
	@echo "    test            Run Python tests"
	@echo ""

# ── Infrastructure ─────────────────────────────────────────────────────────────
up:
	$(DC) up -d

down:
	$(DC) down

logs:
	$(DC) logs -f

build:
	$(DC) build

# ── Python pipeline ────────────────────────────────────────────────────────────
pipeline: parse variants jsonl

parse:
	$(PYTHON) -m this_studio.pipeline.parse_dialogue

variants:
	$(PYTHON) -m this_studio.pipeline.generate_variants

jsonl:
	$(PYTHON_RAW) scripts/generate_finetune_dataset.py

jsonl-check:
	$(PYTHON_RAW) scripts/generate_finetune_dataset.py --check

ingest:
	$(PYTHON) -c "from this_studio.utils.chunking import run_all_sources; run_all_sources()"

# ── ML ─────────────────────────────────────────────────────────────────────────
train:
	$(PYTHON) -m this_studio.finetune.train_lora

serve:
	$(PYTHON) -m uvicorn this_studio.inference.serve:app \
	    --host 0.0.0.0 --port $${PORT:-8001}

export-pdf:
	@test -n "$(NOTEBOOK)" || (echo "Set NOTEBOOK=path/to/notebook.py or .ipynb" && exit 1)
	@case "$(NOTEBOOK)" in \
		*.py) \
			NOTEBOOK_VALUE="$(NOTEBOOK)"; \
			uv run --no-project jupytext --to notebook "$(NOTEBOOK)"; \
			NOTEBOOK_IPYNB="$${NOTEBOOK_VALUE%.py}.ipynb"; \
			;; \
		*.ipynb) \
			NOTEBOOK_IPYNB="$(NOTEBOOK)"; \
			;; \
		*) \
			echo "NOTEBOOK must end in .py or .ipynb"; \
			exit 1; \
			;; \
	esac; \
	uv run --no-project --with nbconvert --with playwright python -m nbconvert --to webpdf --allow-chromium-download "$$NOTEBOOK_IPYNB"

export-tex:
	@test -n "$(NOTEBOOK)" || (echo "Set NOTEBOOK=path/to/notebook.py or .ipynb" && exit 1)
	@case "$(NOTEBOOK)" in \
		*.py) \
			NOTEBOOK_VALUE="$(NOTEBOOK)"; \
			uv run --no-project jupytext --to notebook "$(NOTEBOOK)"; \
			NOTEBOOK_IPYNB="$${NOTEBOOK_VALUE%.py}.ipynb"; \
			;; \
		*.ipynb) \
			NOTEBOOK_IPYNB="$(NOTEBOOK)"; \
			;; \
		*) \
			echo "NOTEBOOK must end in .py or .ipynb"; \
			exit 1; \
			;; \
	esac; \
	uv run --no-project --with nbconvert python -m nbconvert --to latex "$$NOTEBOOK_IPYNB"; \
	NOTEBOOK_TEX="$${NOTEBOOK_IPYNB%.ipynb}.tex"; \
	python3 scripts/style_notebook_latex.py "$$NOTEBOOK_TEX"

export-tex-pdf:
	@test -n "$(NOTEBOOK)" || (echo "Set NOTEBOOK=path/to/notebook.py or .ipynb" && exit 1)
	@case "$(NOTEBOOK)" in \
		*.py) \
			NOTEBOOK_VALUE="$(NOTEBOOK)"; \
			uv run --no-project jupytext --to notebook "$(NOTEBOOK)"; \
			NOTEBOOK_IPYNB="$${NOTEBOOK_VALUE%.py}.ipynb"; \
			;; \
		*.ipynb) \
			NOTEBOOK_IPYNB="$(NOTEBOOK)"; \
			;; \
		*) \
			echo "NOTEBOOK must end in .py or .ipynb"; \
			exit 1; \
			;; \
	esac; \
	uv run --no-project --with nbconvert python -m nbconvert --to latex "$$NOTEBOOK_IPYNB"; \
	NOTEBOOK_TEX="$${NOTEBOOK_IPYNB%.ipynb}.tex"; \
	python3 scripts/style_notebook_latex.py "$$NOTEBOOK_TEX"; \
	if command -v latexmk >/dev/null 2>&1; then \
		latexmk -xelatex "$$NOTEBOOK_TEX"; \
	elif command -v tectonic >/dev/null 2>&1; then \
		tectonic "$$NOTEBOOK_TEX"; \
	elif command -v xelatex >/dev/null 2>&1; then \
		xelatex "$$NOTEBOOK_TEX"; \
		xelatex "$$NOTEBOOK_TEX"; \
	else \
		echo "Missing LaTeX engine. Install latexmk, tectonic, or xelatex."; \
		exit 1; \
	fi

lightning-temp:
	mkdir -p $(TEMP_DIR)/notebooks
	mkdir -p $(TEMP_DIR)/data/finetune
	mkdir -p $(TEMP_DIR)/scripts
	mkdir -p $(TEMP_DIR)/outputs/qwen3-socratic-lora
	mkdir -p $(TEMP_DIR)/outputs/qwen3-socratic-adapter
	mkdir -p $(TEMP_DIR)/outputs/qwen3-socratic-gguf
	cp notebooks/qwen3_4b_lora_finetune.ipynb $(TEMP_DIR)/notebooks/
	cp data/finetune/dataset_v2_train.jsonl $(TEMP_DIR)/data/finetune/
	cp data/finetune/dataset_v2_eval.jsonl $(TEMP_DIR)/data/finetune/
	cp data/finetune/dataset_v2_test.jsonl $(TEMP_DIR)/data/finetune/
	cp data/finetune/dataset_v2_canary_prompts.jsonl $(TEMP_DIR)/data/finetune/
	cp data/finetune/dataset_v2_manifest.csv $(TEMP_DIR)/data/finetune/
	cp data/finetune/dataset_v2_report.json $(TEMP_DIR)/data/finetune/
	cp data/finetune/dataset_v2_style_guide.md $(TEMP_DIR)/data/finetune/
	cp scripts/generate_dataset_v2.py $(TEMP_DIR)/scripts/
	cp scripts/validate_dataset_v2.py $(TEMP_DIR)/scripts/
	@echo "Prepared Lightning AI bundle in $(TEMP_DIR)/"

# ── Backend ────────────────────────────────────────────────────────────────────
backend-dev:
	cd $(BACKEND) && mvn spring-boot:run

backend-build:
	cd $(BACKEND) && mvn -P production package -DskipTests

# ── Code quality ───────────────────────────────────────────────────────────────
lint:
	uv run ruff check src/

format:
	uv run ruff format src/

test:
	uv run pytest tests/ -v
