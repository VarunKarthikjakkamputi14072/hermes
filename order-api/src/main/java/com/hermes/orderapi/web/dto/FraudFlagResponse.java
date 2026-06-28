package com.hermes.orderapi.web.dto;

import com.hermes.common.domain.FraudFlag;
import com.hermes.common.domain.RiskDecision;

import java.time.Instant;
import java.util.UUID;

public record FraudFlagResponse(
        UUID id,
        String accountId,
        UUID paymentId,
        long amountCents,
        int riskScore,
        RiskDecision decision,
        String reasons,
        String narrative,        // null while the AI analyst note is still being written
        boolean narrativePending,
        Instant createdAt
) {
    public static FraudFlagResponse from(FraudFlag f) {
        return new FraudFlagResponse(
                f.getId(), f.getAccountId(), f.getPaymentId(), f.getAmountCents(),
                f.getRiskScore(), f.getDecision(), f.getReasons(),
                f.getNarrative(), f.isNarrativePending(), f.getCreatedAt());
    }
}
