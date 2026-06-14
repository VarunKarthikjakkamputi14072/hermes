package com.hermes.worker.embedding;

/**
 * Where embedded chunks are written so the RAG apps can read them back at query
 * time. Hermes is the writer; MedQuery / ChatDoc are the readers — they meet only
 * here, never by calling each other. The default is an in-memory store so the
 * pipeline runs offline; {@code hermes.vectorstore.mode=qdrant} points it at a
 * shared Qdrant the apps also read.
 */
public interface VectorStore {

    /**
     * Upserts one chunk's vector. {@code chunkId} ({@code docId:index}) is the
     * stable key, so a redelivered or resumed job overwrites rather than
     * duplicates — keeping the write idempotent like the rest of the path.
     */
    void upsert(String chunkId, String docId, float[] vector);
}
