package com.hermes.common.domain;

/**
 * Lifecycle of a payment as it flows through the ledger.
 *
 * PENDING  -> accepted by the API (idempotency key reserved), published to Kafka
 * APPLIED  -> worker debited the account inside a row-locked DB transaction
 * REJECTED -> worker could not apply it (insufficient funds, unknown account)
 */
public enum PaymentStatus {
    PENDING,
    APPLIED,
    REJECTED
}
