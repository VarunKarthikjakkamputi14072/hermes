package com.hermes.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A risk flag raised by the deterministic rules for a suspicious charge.
 *
 * {@code decision}, {@code riskScore} and {@code reasons} are computed by code.
 * {@code narrative} is filled in asynchronously by the LLM (NVIDIA NIM) — it is
 * null until generated, so the UI can show "analyzing…". The model explains; it
 * never changes the decision.
 */
@Entity
@Table(name = "fraud_flags", indexes = {
        @Index(name = "idx_fraud_created_at", columnList = "created_at"),
        @Index(name = "idx_fraud_narrative_pending", columnList = "narrative_pending")
})
public class FraudFlag {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Column(name = "risk_score", nullable = false)
    private int riskScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 16)
    private RiskDecision decision;

    @Column(name = "reasons", nullable = false, length = 512)
    private String reasons;

    @Column(name = "narrative", length = 2000)
    private String narrative;

    /** True while the AI narrative is still pending — drives the generator query. */
    @Column(name = "narrative_pending", nullable = false)
    private boolean narrativePending;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected FraudFlag() {
        // for JPA
    }

    public FraudFlag(UUID id, String accountId, UUID paymentId, long amountCents,
                     int riskScore, RiskDecision decision, String reasons) {
        this.id = id;
        this.accountId = accountId;
        this.paymentId = paymentId;
        this.amountCents = amountCents;
        this.riskScore = riskScore;
        this.decision = decision;
        this.reasons = reasons;
        this.narrativePending = true;
        this.createdAt = Instant.now();
    }

    public void attachNarrative(String narrative) {
        this.narrative = narrative;
        this.narrativePending = false;
    }

    public UUID getId() {
        return id;
    }

    public String getAccountId() {
        return accountId;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public int getRiskScore() {
        return riskScore;
    }

    public RiskDecision getDecision() {
        return decision;
    }

    public String getReasons() {
        return reasons;
    }

    public String getNarrative() {
        return narrative;
    }

    public boolean isNarrativePending() {
        return narrativePending;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
