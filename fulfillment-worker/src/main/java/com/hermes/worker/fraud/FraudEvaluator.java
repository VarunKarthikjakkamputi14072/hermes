package com.hermes.worker.fraud;

import com.hermes.common.domain.FraudFlag;
import com.hermes.common.domain.PaymentStatus;
import com.hermes.common.domain.RiskDecision;
import com.hermes.common.repository.FraudFlagRepository;
import com.hermes.common.repository.PaymentRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Deterministic risk rules. This is the part that DECIDES — purely rule-based and
 * reproducible. The LLM is never consulted here; it only narrates a flag later.
 *
 * Signals (all computable from recent ledger history):
 *  - velocity:     many charges from one account in a short window
 *  - card-testing: a run of insufficient-funds rejections (classic stolen-card probing)
 *  - high amount:  a single large charge
 */
@Component
public class FraudEvaluator {

    private static final int VELOCITY_WINDOW_S = 10;
    private static final long VELOCITY_THRESHOLD = 5;
    private static final int CARDTEST_WINDOW_S = 60;
    private static final long CARDTEST_THRESHOLD = 3;
    private static final long HIGH_AMOUNT_CENTS = 50_000; // $500

    private static final int VELOCITY_SCORE = 40;
    private static final int CARDTEST_SCORE = 40;
    private static final int HIGH_AMOUNT_SCORE = 20;
    private static final int REVIEW_THRESHOLD = 40;
    private static final int HOLD_THRESHOLD = 70;

    private final PaymentRepository payments;
    private final FraudFlagRepository flags;

    public FraudEvaluator(PaymentRepository payments, FraudFlagRepository flags) {
        this.payments = payments;
        this.flags = flags;
    }

    /** Evaluate a just-settled payment; persist a flag only if it warrants REVIEW/HOLD. */
    public void evaluate(UUID paymentId, String accountId, long amountCents) {
        Instant now = Instant.now();
        long velocity = payments.countByAccountIdAndCreatedAtAfter(
                accountId, now.minusSeconds(VELOCITY_WINDOW_S));
        long recentRejects = payments.countByAccountIdAndStatusAndCreatedAtAfter(
                accountId, PaymentStatus.REJECTED, now.minusSeconds(CARDTEST_WINDOW_S));

        int score = 0;
        List<String> reasons = new ArrayList<>();
        if (velocity >= VELOCITY_THRESHOLD) {
            score += VELOCITY_SCORE;
            reasons.add(velocity + " charges in " + VELOCITY_WINDOW_S + "s (velocity)");
        }
        if (recentRejects >= CARDTEST_THRESHOLD) {
            score += CARDTEST_SCORE;
            reasons.add(recentRejects + " insufficient-funds attempts in " + CARDTEST_WINDOW_S
                    + "s (card-testing pattern)");
        }
        if (amountCents >= HIGH_AMOUNT_CENTS) {
            score += HIGH_AMOUNT_SCORE;
            reasons.add("large charge of " + usd(amountCents));
        }
        score = Math.min(score, 100);

        RiskDecision decision = score >= HOLD_THRESHOLD ? RiskDecision.HOLD
                : score >= REVIEW_THRESHOLD ? RiskDecision.REVIEW
                : RiskDecision.ALLOW;
        if (decision == RiskDecision.ALLOW) {
            return; // nothing interesting to flag
        }

        flags.save(new FraudFlag(
                UUID.randomUUID(), accountId, paymentId, amountCents,
                score, decision, String.join("; ", reasons)));
    }

    private static String usd(long cents) {
        return String.format("$%,.2f", cents / 100.0);
    }
}
