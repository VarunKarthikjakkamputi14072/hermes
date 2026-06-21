package com.hermes.orderapi.web.dto;

import com.hermes.common.domain.Payment;
import com.hermes.common.domain.PaymentStatus;

import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID paymentId,
        String idempotencyKey,
        String accountId,
        long amountCents,
        PaymentStatus status,
        String failureReason,
        boolean deduplicated,
        Instant createdAt,
        Instant updatedAt
) {
    /** A freshly accepted payment. */
    public static PaymentResponse accepted(Payment p) {
        return of(p, false);
    }

    /** An idempotent replay — the key already existed, so nothing new happened. */
    public static PaymentResponse deduplicated(Payment p) {
        return of(p, true);
    }

    private static PaymentResponse of(Payment p, boolean deduplicated) {
        return new PaymentResponse(
                p.getId(),
                p.getIdempotencyKey(),
                p.getAccountId(),
                p.getAmountCents(),
                p.getStatus(),
                p.getFailureReason(),
                deduplicated,
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}
