FROM ghcr.io/docling-project/docling-serve:v1.16.1

ENV HF_HUB_OFFLINE=1 \
    TRANSFORMERS_OFFLINE=1 \
    HF_HOME=/opt/hf-cache

COPY --chown=1001:0 tokenizers/qwen3-0.6b-tokenizer /opt/tokenizers/qwen3-0.6b-tokenizer
COPY --chown=1001:0 tokenizer-lock.json /opt/tokenizers/tokenizer-lock.json

