.PHONY: help up down logs build \
        pipeline parse variants jsonl \
        ingest train serve \
        backend-dev backend-build \
        lint format test

# ── Config ─────────────────────────────────────────────────────────────────────
PYTHON     := uv run python
DC         := docker compose
BACKEND    := backend

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
	@echo "    jsonl           Stage 3 – convert parsed JSON → dataset.jsonl"
	@echo "    ingest          Chunk sources and upsert into ChromaDB"
	@echo ""
	@echo "  ML"
	@echo "    train           Fine-tune LLaMA 3.1 8B with LoRA (GPU required)"
	@echo "    serve           Run inference server locally (GPU required)"
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
	$(PYTHON) -m this_studio.pipeline.to_jsonl

ingest:
	$(PYTHON) -c "from this_studio.utils.chunking import run_all_sources; run_all_sources()"

# ── ML ─────────────────────────────────────────────────────────────────────────
train:
	$(PYTHON) -m this_studio.finetune.train_lora

serve:
	$(PYTHON) -m uvicorn this_studio.inference.serve:app \
	    --host 0.0.0.0 --port $${PORT:-8001}

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
