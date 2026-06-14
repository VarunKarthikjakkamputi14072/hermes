package com.hermes.worker.embedding;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default offline vector store — keyed by chunkId so writes are idempotent. Stands
 * in for Qdrant/Pinecone exactly as MedQuery's in-memory cosine fallback stands in
 * for Pinecone, so tests and local runs need no external store.
 */
@Component
@ConditionalOnProperty(name = "hermes.vectorstore.mode", havingValue = "memory", matchIfMissing = true)
public class InMemoryVectorStore implements VectorStore {

    private final Map<String, float[]> vectors = new ConcurrentHashMap<>();

    @Override
    public void upsert(String chunkId, String docId, float[] vector) {
        vectors.put(chunkId, vector);
    }

    public int size() {
        return vectors.size();
    }
}
