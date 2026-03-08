"""
utils/chromadb_client.py
------------------------
Graduated from notebooks/02-chromadb-ingestion.ipynb.

Wraps ChromaDB operations: upsert chunks, query by embedding.
"""

from __future__ import annotations

import json
import time
from typing import List

import chromadb
import ollama
from chromadb.config import Settings

# ---------------------------------------------------------------------------
# Config defaults (override via environment or pass directly)
# ---------------------------------------------------------------------------

CHROMA_DB_PATH = "./chroma_db"
COLLECTION_NAME = "socratic_tutor_collection"
EMBED_MODEL = "nomic-embed-text"  # ollama pull nomic-embed-text
BATCH_SIZE = 50


# ---------------------------------------------------------------------------
# Client
# ---------------------------------------------------------------------------


class ChromaClient:
    """
    Thin wrapper around ChromaDB for the socratic tutor project.
    Uses Ollama for local embeddings (nomic-embed-text).
    """

    def __init__(
        self,
        db_path: str = CHROMA_DB_PATH,
        collection_name: str = COLLECTION_NAME,
        embed_model: str = EMBED_MODEL,
        batch_size: int = BATCH_SIZE,
    ):
        self.embed_model = embed_model
        self.batch_size = batch_size
        self.client = chromadb.PersistentClient(
            path=db_path,
            settings=Settings(anonymized_telemetry=False),
        )
        self.collection = self.client.get_or_create_collection(
            name=collection_name,
            metadata={"hnsw:space": "cosine"},
        )

    # ------------------------------------------------------------------
    # Embedding
    # ------------------------------------------------------------------

    def embed(self, texts: List[str]) -> List[List[float]]:
        """Embed a list of texts using Ollama nomic-embed-text."""
        embeddings = []
        for text in texts:
            resp = ollama.embeddings(
                model=self.embed_model, prompt=f"search_document: {text}"
            )
            embeddings.append(resp["embedding"])
        return embeddings

    # ------------------------------------------------------------------
    # Upsert
    # ------------------------------------------------------------------

    def upsert_chunks(self, chunks_path: str) -> int:
        """
        Load chunks from a JSON file and upsert into ChromaDB in batches.
        Returns the number of chunks upserted.
        """
        with open(chunks_path, encoding="utf-8") as f:
            raw = json.load(f)

        # Filter out malformed / empty
        chunks = [
            c
            for c in raw
            if c.get("content", "").strip()
            and c.get("id", "").strip()
            and not self.collection.get(ids=[c["id"]])["ids"]  # skip already-ingested
        ]

        print(f"Chunks to upsert: {len(chunks)}")

        for i in range(0, len(chunks), self.batch_size):
            batch = chunks[i : i + self.batch_size]
            texts = [c["content"] for c in batch]
            ids = [c["id"] for c in batch]
            metadatas = [
                {
                    "source": c.get("source", ""),
                    "url": c.get("url", ""),
                    "title": c.get("title", ""),
                    "heading": c.get("heading", ""),
                    "has_code": str(c.get("has_code", False)),
                    "tokens": str(c.get("tokens", 0)),
                }
                for c in batch
            ]
            embeddings = self.embed(texts)
            self.collection.upsert(
                ids=ids,
                documents=texts,
                embeddings=embeddings,
                metadatas=metadatas,
            )
            print(f"  Upserted {i + len(batch)}/{len(chunks)}")
            time.sleep(0.1)  # mild back-off for Ollama

        print(f"Done. Collection size: {self.collection.count()}")
        return len(chunks)

    # ------------------------------------------------------------------
    # Query
    # ------------------------------------------------------------------

    def query(
        self,
        question: str,
        n_results: int = 5,
        where: dict | None = None,
    ) -> list[dict]:
        """
        Semantic search. Returns a list of result dicts with
        keys: id, document, metadata, distance.
        """
        resp = ollama.embeddings(
            model=self.embed_model, prompt=f"search_query: {question}"
        )
        query_embedding = resp["embedding"]

        kwargs: dict = dict(
            query_embeddings=[query_embedding],
            n_results=n_results,
            include=["documents", "metadatas", "distances"],
        )
        if where:
            kwargs["where"] = where

        result = self.collection.query(**kwargs)

        results = []
        for idx in range(len(result["ids"][0])):
            results.append(
                {
                    "id": result["ids"][0][idx],
                    "document": result["documents"][0][idx],
                    "metadata": result["metadatas"][0][idx],
                    "distance": result["distances"][0][idx],
                }
            )
        return results

    # ------------------------------------------------------------------
    # Info
    # ------------------------------------------------------------------

    def count(self) -> int:
        return self.collection.count()

    def stats(self) -> dict:
        count = self.collection.count()
        return {"collection": self.collection.name, "count": count}
