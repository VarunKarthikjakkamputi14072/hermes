package com.hermes.worker.embedding;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Deterministic offline embedding model — the default so the whole pipeline runs
 * with no Python service or network. Produces a stable fixed-width vector seeded
 * from the text, so tests are repeatable. {@code hermes.worker.embed-delay-ms}
 * simulates model latency so the live progress bar visibly fills during a demo.
 */
@Component
@ConditionalOnProperty(name = "hermes.embedding.mode", havingValue = "fake", matchIfMissing = true)
public class FakeEmbeddingModel implements EmbeddingModel {

    private static final int DIM = 384; // matches all-MiniLM-L6-v2, so a real model is a drop-in

    private final long delayMs;

    public FakeEmbeddingModel(@Value("${hermes.worker.embed-delay-ms:0}") long delayMs) {
        this.delayMs = Math.max(0, delayMs);
    }

    @Override
    public float[] embed(String text) {
        if (delayMs > 0) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        int seed = text.hashCode();
        float[] vector = new float[DIM];
        double norm = 0;
        for (int i = 0; i < DIM; i++) {
            seed = (seed * 1_103_515_245) + 12_345;
            float v = ((seed >>> 8) & 0xFFFF) / 32_768f - 1f;
            vector[i] = v;
            norm += (double) v * v;
        }
        // L2-normalise so cosine search behaves like a real sentence-embedding model
        float inv = norm > 0 ? (float) (1.0 / Math.sqrt(norm)) : 1f;
        for (int i = 0; i < DIM; i++) {
            vector[i] *= inv;
        }
        return vector;
    }

    @Override
    public int dimension() {
        return DIM;
    }
}
