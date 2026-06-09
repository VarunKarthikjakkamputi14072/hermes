package com.hermes.common.domain;

/**
 * Lifecycle of an order as it flows through the engine.
 *
 * PENDING   -> accepted by the API, published to Kafka, not yet processed
 * FULFILLED -> worker reserved inventory inside a DB transaction
 * REJECTED  -> worker could not fulfil it (e.g. out of stock, unknown product)
 */
public enum OrderStatus {
    PENDING,
    FULFILLED,
    REJECTED
}
