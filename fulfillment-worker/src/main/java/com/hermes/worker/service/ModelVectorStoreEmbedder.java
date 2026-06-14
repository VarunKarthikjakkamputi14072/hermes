package com.hermes.worker.service;

import com.hermes.worker.embedding.EmbeddingModel;
import com.hermes.worker.embedding.VectorStore;
import org.springframework.stereotype.Component;

/**
 * The real-shaped embedding unit: fetch the chunk's text, embed it with the model,
 * and upsert the vector to the store keyed by {@code docId:index}. Model and store
 * are both behind interfaces, so swapping the offline fakes for the Python service
 * and Qdrant is pure configuration — this class doesn't change.
 */
@Component
public class ModelVectorStoreEmbedder implements Embedder {

    private final EmbeddingModel model;
    private final VectorStore vectorStore;

    public ModelVectorStoreEmbedder(EmbeddingModel model, VectorStore vectorStore) {
        this.model = model;
        this.vectorStore = vectorStore;
    }

    @Override
    public float[] embed(String docId, int chunkIndex) {
        String chunkId = docId + ":" + chunkIndex;
        // In production the chunk text comes from object storage where the upload
        // landed; here it is synthesised so the pipeline runs without real files.
        // The embedding + vector-store write below are the real path either way.
        String text = chunkText(docId, chunkIndex);
        float[] vector = model.embed(text);
        vectorStore.upsert(chunkId, docId, vector);
        return vector;
    }

    private String chunkText(String docId, int chunkIndex) {
        return "document " + docId + " chunk " + chunkIndex;
    }
}
