package com.hermes.worker.fraud;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Minimal client for NVIDIA NIM's OpenAI-compatible chat endpoint. The API key is
 * read from the environment and never logged. When no key is configured the
 * client is disabled and callers fall back to a deterministic template, so the
 * system runs fully keyless and in CI (real LLM by default, fallback otherwise).
 */
@Component
public class NimClient {

    private static final Logger log = LoggerFactory.getLogger(NimClient.class);

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    public NimClient(
            @Value("${NVIDIA_API_KEY:}") String apiKey,
            @Value("${hermes.nim.base-url:https://integrate.api.nvidia.com/v1}") String baseUrl,
            @Value("${hermes.nim.model:meta/llama-3.3-70b-instruct}") String model) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
    }

    public boolean enabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** Returns the assistant's message, or null on any error / when disabled. */
    public String chat(String system, String user) {
        if (!enabled()) {
            return null;
        }
        try {
            String payload = mapper.writeValueAsString(Map.of(
                    "model", model,
                    "temperature", 0.2,
                    "max_tokens", 220,
                    "messages", List.of(
                            Map.of("role", "system", "content", system),
                            Map.of("role", "user", "content", user))));
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.warn("NIM responded {} ({})", response.statusCode(), model);
                return null;
            }
            JsonNode content = mapper.readTree(response.body())
                    .path("choices").path(0).path("message").path("content");
            return content.isMissingNode() ? null : content.asText(null);
        } catch (Exception e) {
            log.warn("NIM call failed: {}", e.toString());
            return null;
        }
    }
}
