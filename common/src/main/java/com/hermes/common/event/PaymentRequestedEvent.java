package com.hermes.common.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a payment is accepted by the API and consumed by the ledger
 * worker. Carries only what the worker needs to debit the account and settle
 * the payment row.
 */
public record PaymentRequestedEvent(
        UUID paymentId,
        String idempotencyKey,
        String accountId,
        long amountCents,
        Instant requestedAt
) {
}
