"""Embedding microservice — the Python side of Hermes' ingestion seam.

Hermes (Java) owns the durable job lifecycle and asks this service only to turn
chunk text into a vector. Provider is chosen by EMBED_PROVIDER:

* ``nvidia``   — real NVIDIA NIM embeddings (nv-embedqa-e5-v5, free-tier key).
                 This is the default for the ecosystem demo: real vectors.
* ``sentence`` — local sentence-transformers (set MODEL_NAME).
* ``fake``     — deterministic offline vector (no key, no network) for CI/tests.

EMBED_DIM must match the active model (nv-embedqa-e5-v5 = 1024, MiniLM = 384).
"""
from __future__ import annotations

import hashlib
import math
import os
import time

import httpx
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

PROVIDER = os.getenv("EMBED_PROVIDER", "fake").lower()
DIM = int(os.getenv("EMBED_DIM", "1024" if PROVIDER == "nvidia" else "384"))

NVIDIA_API_KEY = os.getenv("NVIDIA_API_KEY", "")
NVIDIA_BASE_URL = os.getenv("NVIDIA_BASE_URL", "https://integrate.api.nvidia.com/v1")
NVIDIA_EMBED_MODEL = os.getenv("NVIDIA_EMBEDDING_MODEL", "nvidia/nv-embedqa-e5-v5")
MODEL_NAME = os.getenv("MODEL_NAME", "")  # sentence-transformers model

app = FastAPI(title="hermes-embedding-service")
_st_model = None


class EmbedRequest(BaseModel):
    text: str


class EmbedResponse(BaseModel):
    vector: list[float]


def _nvidia_embed(text: str) -> list[float]:
    if not NVIDIA_API_KEY:
        raise HTTPException(503, "EMBED_PROVIDER=nvidia but NVIDIA_API_KEY is not set")
    body = {
        "model": NVIDIA_EMBED_MODEL,
        "input": [text],
        "input_type": "passage",
        "encoding_format": "float",
        "truncate": "END",
    }
    headers = {"Authorization": f"Bearer {NVIDIA_API_KEY}", "Content-Type": "application/json"}
    last = None
    # Retry 429 / 5xx with exponential backoff so the free-tier per-minute rate
    # limit just slows the rebuild instead of failing it.
    for attempt in range(5):
        last = httpx.post(
            f"{NVIDIA_BASE_URL.rstrip('/')}/embeddings", headers=headers, json=body, timeout=30
        )
        if last.status_code < 400:
            return [float(x) for x in last.json()["data"][0]["embedding"]]
        if last.status_code in (429, 500, 502, 503, 504):
            time.sleep(min(2 ** attempt, 20))  # 1, 2, 4, 8, 16s — rides out the rate window
            continue
        break  # non-retryable error
    raise HTTPException(502, f"NVIDIA embeddings error {last.status_code}: {last.text[:200]}")


def _sentence_embed(text: str) -> list[float]:
    global _st_model
    if _st_model is None:
        from sentence_transformers import SentenceTransformer

        _st_model = SentenceTransformer(MODEL_NAME)
    return _st_model.encode(text, normalize_embeddings=True).tolist()


def _fake_embed(text: str) -> list[float]:
    """Deterministic, L2-normalised pseudo-embedding — CI/tests only."""
    seed = int.from_bytes(hashlib.sha256(text.encode("utf-8")).digest()[:8], "big")
    vec = []
    for _ in range(DIM):
        seed = (seed * 6_364_136_223_846_793_005 + 1_442_695_040_888_963_407) & ((1 << 64) - 1)
        vec.append((seed >> 11) / (1 << 53) * 2.0 - 1.0)
    norm = math.sqrt(sum(v * v for v in vec)) or 1.0
    return [v / norm for v in vec]


@app.get("/health")
def health() -> dict:
    model = {"nvidia": NVIDIA_EMBED_MODEL, "sentence": MODEL_NAME}.get(PROVIDER, "fake")
    return {"status": "ok", "provider": PROVIDER, "model": model, "dim": DIM}


@app.post("/embed", response_model=EmbedResponse)
def embed(req: EmbedRequest) -> EmbedResponse:
    if PROVIDER == "nvidia":
        vector = _nvidia_embed(req.text)
    elif PROVIDER == "sentence":
        vector = _sentence_embed(req.text)
    else:
        vector = _fake_embed(req.text)
    return EmbedResponse(vector=vector)
