package com.hermes.common.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable message published to Kafka when an order is accepted by the API and
 * consumed by the fulfilment worker. Kept deliberately small — it carries only
 * what the worker needs to reserve inventory and update the order.
 */
public record OrderPlacedEvent(
        UUID orderId,
        String customerId,
        String sku,
        int quantity,
        Instant placedAt
) {
}
