package com.hermes.worker.service;

import java.util.UUID;

/**
 * Thrown when an order event is consumed before the producing transaction's
 * commit is visible. It is retryable — the listener's error handler will redeliver
 * after a back-off, by which time the row has been committed.
 */
public class OrderNotYetVisibleException extends RuntimeException {

    public OrderNotYetVisibleException(UUID orderId) {
        super("Order " + orderId + " not yet visible; will retry");
    }
}
