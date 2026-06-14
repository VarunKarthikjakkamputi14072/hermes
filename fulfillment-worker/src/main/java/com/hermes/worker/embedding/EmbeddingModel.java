package com.hermes.worker.embedding;

/**
 * Turns chunk text into a vector. The default is an offline deterministic fake;
 * setting {@code hermes.embedding.mode=http} swaps in a call to the Python
 * embedding service — the real Java↔Python seam — with no change to the
 * ingestion path. The same "drop-in fakes when no provider" discipline the RAG
 * apps use.
 */
public interface EmbeddingModel {

    float[] embed(String text);

    /** Dimensionality of the vectors this model produces. */
    int dimension();
}
