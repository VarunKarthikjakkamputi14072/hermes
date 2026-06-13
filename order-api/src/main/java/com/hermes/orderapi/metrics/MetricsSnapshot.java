package com.hermes.orderapi.metrics;

/**
 * One frame on the SSE stream. Serialised to JSON by Jackson and consumed by the
 * frontend's EventSource. {@code featuredStock} is null unless the client asked
 * for a specific {@code ?sku=}.
 */
public record MetricsSnapshot(
        long timestamp,
        long pending,
        long fulfilled,
        long rejected,
        long total,
        double acceptedPerSec,
        String featuredSku,
        Integer featuredStock
) {
}
