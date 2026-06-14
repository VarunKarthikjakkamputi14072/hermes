package com.hermes.worker.service;

/**
 * Embeds one chunk of a document and writes the vector to the store — the unit of
 * work the ingestion loop repeats. The default implementation ties an
 * {@code EmbeddingModel} (fake or HTTP→Python) to a {@code VectorStore} (in-memory
 * or Qdrant); both sides default to offline fakes so the pipeline runs with no
 * external dependency, and either is a one-property swap to the real thing.
 */
public interface Embedder {

    /**
     * Returns the embedding so callers can assert on it in tests. May throw if the
     * model or store is unavailable — the message is then redelivered and, with
     * progress committed so far, the retry resumes rather than re-embedding.
     */
    float[] embed(String docId, int chunkIndex);
}
