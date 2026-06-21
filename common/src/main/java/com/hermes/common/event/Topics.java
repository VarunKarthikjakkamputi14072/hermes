package com.hermes.common.event;

/** Single source of truth for Kafka topic + consumer-group names. */
public final class Topics {

    /** Orders accepted by the API, awaiting fulfilment. */
    public static final String ORDERS_PLACED = "orders.placed";

    /** Dead-letter topic for messages that exhaust their retries. */
    public static final String ORDERS_PLACED_DLT = "orders.placed.DLT";

    /** Consumer group for the fulfilment workers. */
    public static final String FULFILMENT_GROUP = "fulfilment-workers";

    /** Documents accepted by the API, awaiting asynchronous embedding. */
    public static final String INGEST_REQUESTED = "ingest.requested";

    /** Dead-letter topic for ingestion jobs that exhaust their retries. */
    public static final String INGEST_REQUESTED_DLT = "ingest.requested.DLT";

    /** Consumer group for the ingestion workers. */
    public static final String INGEST_GROUP = "ingestion-workers";

    /** Payments accepted by the API, awaiting settlement against the ledger. */
    public static final String PAYMENTS_REQUESTED = "payments.requested";

    /** Dead-letter topic for payments that exhaust their retries. */
    public static final String PAYMENTS_REQUESTED_DLT = "payments.requested.DLT";

    /** Consumer group for the ledger workers. */
    public static final String PAYMENT_GROUP = "ledger-workers";

    private Topics() {
    }
}
