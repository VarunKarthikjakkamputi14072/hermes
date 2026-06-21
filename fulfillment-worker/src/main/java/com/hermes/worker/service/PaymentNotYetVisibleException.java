package com.hermes.worker.service;

import java.util.UUID;

/**
 * Thrown when a payment event is consumed before the producing transaction's
 * commit is visible (the dual-write race). Retryable — the listener's error
 * handler redelivers after a back-off, by which time the row has committed.
 */
public class PaymentNotYetVisibleException extends RuntimeException {

    public PaymentNotYetVisibleException(UUID paymentId) {
        super("Payment " + paymentId + " not yet visible; will retry");
    }
}
