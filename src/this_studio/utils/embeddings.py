"""
utils/embeddings.py
-------------------
Thin wrappers for embedding operations shared across pipeline and inference.
"""

from __future__ import annotations

from typing import List

import ollama

EMBED_MODEL = "nomic-embed-text"
DOCUMENT_PREFIX = "search_document: "
QUERY_PREFIX = "search_query: "


def embed_documents(texts: List[str], model: str = EMBED_MODEL) -> List[List[float]]:
    """Embed a list of documents (adds nomic search_document prefix)."""
    return [
        ollama.embeddings(model=model, prompt=f"{DOCUMENT_PREFIX}{text}")["embedding"]
        for text in texts
    ]


def embed_query(text: str, model: str = EMBED_MODEL) -> List[float]:
    """Embed a single query (adds nomic search_query prefix)."""
    return ollama.embeddings(model=model, prompt=f"{QUERY_PREFIX}{text}")["embedding"]
