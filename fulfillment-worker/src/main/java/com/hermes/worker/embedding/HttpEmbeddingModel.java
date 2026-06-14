package com.hermes.worker.embedding;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Calls the Python embedding service over HTTP — the real Java↔Python seam. The
 * models live in Python (sentence-transformers); Hermes owns the durable job
 * lifecycle and asks Python only to turn text into a vector. Active when
 * {@code hermes.embedding.mode=http}.
 */
@Component
@ConditionalOnProperty(name = "hermes.embedding.mode", havingValue = "http")
public class HttpEmbeddingModel implements EmbeddingModel {

    private final RestClient client;
    private final int dimension;

    public HttpEmbeddingModel(
            RestClient.Builder restClientBuilder,
            @Value("${hermes.embedding.http.base-url:http://embedding-service:8000}") String baseUrl,
            @Value("${hermes.embedding.http.dimension:384}") int dimension) {
        // use the Boot-autoconfigured builder so the Jackson message converter is
        // wired in — a bare RestClient.builder() ships an empty body for POJO/Map
        this.client = restClientBuilder.baseUrl(baseUrl).build();
        this.dimension = dimension;
    }

    public record EmbedResponse(List<Float> vector) {
    }

    @Override
    public float[] embed(String text) {
        EmbedResponse response = client.post()
                .uri("/embed")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("text", text))
                .retrieve()
                .body(EmbedResponse.class);
        if (response == null || response.vector() == null) {
            throw new IllegalStateException("embedding service returned no vector");
        }
        List<Float> v = response.vector();
        float[] out = new float[v.size()];
        for (int i = 0; i < v.size(); i++) {
            out[i] = v.get(i);
        }
        return out;
    }

    @Override
    public int dimension() {
        return dimension;
    }
}
