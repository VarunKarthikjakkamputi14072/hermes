package com.hermes.worker.service;

import org.springframework.stereotype.Component;

/**
 * Deterministic stand-in for a real embedding model. Produces a small fixed-width
 * vector seeded from (docId, chunkIndex) so the pipeline runs offline and tests
 * are repeatable. Swapping in a real model means replacing this bean with one
 * that calls the Python embedding worker and upserts to the vector store — no
 * other code in the ingestion path changes.
 */
@Component
public class FakeEmbedder implements Embedder {

    private static final int DIM = 8;

    @Override
    public float[] embed(String docId, int chunkIndex) {
        int seed = (docId.hashCode() * 31) + chunkIndex;
        float[] vector = new float[DIM];
        for (int i = 0; i < DIM; i++) {
            // cheap deterministic pseudo-vector in [-1, 1]
            seed = (seed * 1_103_515_245) + 12_345;
            vector[i] = ((seed >>> 8) & 0xFFFF) / 32_768f - 1f;
        }
        return vector;
    }
}
