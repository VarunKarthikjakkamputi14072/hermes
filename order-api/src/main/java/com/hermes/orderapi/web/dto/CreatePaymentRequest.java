package com.hermes.orderapi.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * A charge request. The {@code idempotencyKey} is supplied by the client (as
 * with Stripe): retrying with the same key is guaranteed not to charge twice.
 */
public record CreatePaymentRequest(
        @NotBlank String accountId,
        @Min(1) long amountCents,
        @NotBlank String idempotencyKey
) {
}
