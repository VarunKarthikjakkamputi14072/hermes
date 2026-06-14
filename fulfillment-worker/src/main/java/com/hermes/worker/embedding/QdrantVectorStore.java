package com.hermes.worker.embedding;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Writes chunk vectors into a shared Qdrant collection over its REST API. This is
 * the store MedQuery / ChatDoc read at query time — the real link between the
 * ingestion engine and the RAG apps. Active when
 * {@code hermes.vectorstore.mode=qdrant}. The collection is created on startup if
 * absent, so the stack stays self-bootstrapping like the Kafka topics.
 */
@Component
@ConditionalOnProperty(name = "hermes.vectorstore.mode", havingValue = "qdrant")
public class QdrantVectorStore implements VectorStore {

    private static final Logger log = LoggerFactory.getLogger(QdrantVectorStore.class);

    private final RestClient client;
    private final String collection;
    private final int dimension;

    public QdrantVectorStore(
            RestClient.Builder restClientBuilder,
            @Value("${hermes.vectorstore.qdrant.url:http://qdrant:6333}") String url,
            @Value("${hermes.vectorstore.qdrant.collection:hermes-chunks}") String collection,
            @Value("${hermes.embedding.http.dimension:384}") int dimension) {
        this.client = restClientBuilder.baseUrl(url).build();
        this.collection = collection;
        this.dimension = dimension;
    }

    @PostConstruct
    void ensureCollection() {
        try {
            client.put()
                    .uri("/collections/{c}", collection)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("vectors", Map.of("size", dimension, "distance", "Cosine")))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Qdrant collection '{}' ready (dim={})", collection, dimension);
        } catch (Exception e) {
            // Already exists, or Qdrant not up yet — upserts will surface a real
            // failure and the message retries / dead-letters as usual.
            log.warn("Qdrant collection ensure skipped: {}", e.toString());
        }
    }

    @Override
    public void upsert(String chunkId, String docId, float[] vector) {
        // Qdrant point ids are uint64 or UUID; derive a stable UUID from chunkId so
        // a resumed/redelivered chunk overwrites rather than duplicates.
        String pointId = UUID.nameUUIDFromBytes(chunkId.getBytes(StandardCharsets.UTF_8)).toString();
        Float[] boxed = new Float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            boxed[i] = vector[i];
        }
        Map<String, Object> point = Map.of(
                "id", pointId,
                "vector", boxed,
                "payload", Map.of("chunkId", chunkId, "docId", docId)
        );
        client.put()
                .uri("/collections/{c}/points?wait=true", collection)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("points", List.of(point)))
                .retrieve()
                .toBodilessEntity();
    }
}
