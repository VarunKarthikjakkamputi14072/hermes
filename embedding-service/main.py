"""Embedding microservice — the Python side of Hermes' ingestion seam.

Hermes (Java) owns the durable job lifecycle and asks this service only to turn
chunk text into a vector. It runs offline by default with a deterministic hashing
embedding (no model download, no torch), so the whole stack boots with zero
external dependencies. Set MODEL_NAME to a sentence-transformers model to use a
real model instead — the only change needed to go from demo to real embeddings.
"""
from __future__ import annotations

import hashlib
import math
import os

from fastapi import FastAPI
from pydantic import BaseModel

DIM = int(os.getenv("EMBED_DIM", "384"))
MODEL_NAME = os.getenv("MODEL_NAME", "")  # empty => deterministic offline fake

app = FastAPI(title="hermes-embedding-service")
_model = None


class EmbedRequest(BaseModel):
    text: str


class EmbedResponse(BaseModel):
    vector: list[float]


def _load_model():
    """Lazily load a real sentence-transformers model when MODEL_NAME is set."""
    global _model
    if _model is None:
        from sentence_transformers import SentenceTransformer  # imported only if used

        _model = SentenceTransformer(MODEL_NAME)
    return _model


def _fake_embed(text: str) -> list[float]:
    """Deterministic, L2-normalised pseudo-embedding seeded from the text."""
    seed = int.from_bytes(hashlib.sha256(text.encode("utf-8")).digest()[:8], "big")
    vec = []
    for _ in range(DIM):
        seed = (seed * 6_364_136_223_846_793_005 + 1_442_695_040_888_963_407) & ((1 << 64) - 1)
        vec.append((seed >> 11) / (1 << 53) * 2.0 - 1.0)
    norm = math.sqrt(sum(v * v for v in vec)) or 1.0
    return [v / norm for v in vec]


@app.get("/health")
def health() -> dict:
    return {"status": "ok", "model": MODEL_NAME or "fake", "dim": DIM}


@app.post("/embed", response_model=EmbedResponse)
def embed(req: EmbedRequest) -> EmbedResponse:
    if MODEL_NAME:
        vector = _load_model().encode(req.text, normalize_embeddings=True).tolist()
    else:
        vector = _fake_embed(req.text)
    return EmbedResponse(vector=vector)
