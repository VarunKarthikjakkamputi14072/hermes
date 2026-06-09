package com.hermes.common.event;

/** Single source of truth for Kafka topic + consumer-group names. */
public final class Topics {

    /** Orders accepted by the API, awaiting fulfilment. */
    public static final String ORDERS_PLACED = "orders.placed";

    /** Dead-letter topic for messages that exhaust their retries. */
    public static final String ORDERS_PLACED_DLT = "orders.placed.DLT";

    /** Consumer group for the fulfilment workers. */
    public static final String FULFILMENT_GROUP = "fulfilment-workers";

    private Topics() {
    }
}
