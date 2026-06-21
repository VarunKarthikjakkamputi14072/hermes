package com.hermes.orderapi.metrics;

/**
 * One frame on the ledger SSE stream (GET /api/payments/stream). Serialised to
 * JSON by Jackson and consumed by the frontend's EventSource.
 *
 * {@code accountsOverdrawn} is the live invariant — it must always be 0.
 * {@code duplicatesBlocked} is the headline "double-charges prevented" counter.
 */
public record LedgerSnapshot(
        long timestamp,
        long pending,
        long applied,
        long rejected,
        long total,
        double appliedPerSec,
        long duplicatesBlocked,
        long totalDebitedCents,
        long accountsOverdrawn
) {
}
