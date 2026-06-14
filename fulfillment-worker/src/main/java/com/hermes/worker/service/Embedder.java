package com.hermes.worker.service;

/**
 * The seam where the JVM hands chunk text to an embedding model. Production will
 * back this with a call out to a Python embedding worker (the models live in
 * Python) and a write into the vector store; for local runs and tests a
 * deterministic fake stands in, mirroring the {@code USE_FAKE_PROVIDERS}
 * discipline used elsewhere in the platform so the whole pipeline runs with no
 * external dependency.
 */
public interface Embedder {

    /**
     * Embeds one chunk of a document and writes the vector to the store. Returns
     * the embedding so callers can assert on it in tests. May throw if the
     * embedding backend is unavailable — the ingestion transaction then rolls
     * back and Kafka redelivers, exactly like the order path.
     */
    float[] embed(String docId, int chunkIndex);
}
