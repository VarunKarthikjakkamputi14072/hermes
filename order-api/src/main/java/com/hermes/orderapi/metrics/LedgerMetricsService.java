package com.hermes.orderapi.metrics;

import com.hermes.common.domain.PaymentStatus;
import com.hermes.common.repository.AccountRepository;
import com.hermes.common.repository.PaymentRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Computes the live ledger snapshot behind the payments SSE stream. Throughput is
 * derived from the change in total payment count between ticks (no counter in the
 * request path). {@link #tick()} is the single source of truth for the rate and
 * must be called by exactly one caller (the push scheduler).
 */
@Service
public class LedgerMetricsService {

    private final PaymentRepository payments;
    private final AccountRepository accounts;
    private final PaymentMetrics paymentMetrics;

    private long lastTotal;
    private long lastNanos = System.nanoTime();

    public LedgerMetricsService(PaymentRepository payments,
                                AccountRepository accounts,
                                PaymentMetrics paymentMetrics) {
        this.payments = payments;
        this.accounts = accounts;
        this.paymentMetrics = paymentMetrics;
    }

    public synchronized void resetBaseline() {
        this.lastTotal = payments.count();
        this.lastNanos = System.nanoTime();
    }

    /** Advance one tick: counts + payments/sec from the delta. */
    public synchronized LedgerSnapshot tick() {
        long pending = payments.countByStatus(PaymentStatus.PENDING);
        long applied = payments.countByStatus(PaymentStatus.APPLIED);
        long rejected = payments.countByStatus(PaymentStatus.REJECTED);
        long total = pending + applied + rejected;

        long now = System.nanoTime();
        double seconds = (now - lastNanos) / 1_000_000_000.0;
        double rate = seconds > 0 ? Math.max(0, total - lastTotal) / seconds : 0;
        lastTotal = total;
        lastNanos = now;

        return build(pending, applied, rejected, total, rate);
    }

    /** Counts only, without advancing the rate baseline — for the connect event. */
    public synchronized LedgerSnapshot peek() {
        long pending = payments.countByStatus(PaymentStatus.PENDING);
        long applied = payments.countByStatus(PaymentStatus.APPLIED);
        long rejected = payments.countByStatus(PaymentStatus.REJECTED);
        return build(pending, applied, rejected, pending + applied + rejected, 0);
    }

    private LedgerSnapshot build(long pending, long applied, long rejected, long total, double rate) {
        return new LedgerSnapshot(
                Instant.now().toEpochMilli(),
                pending, applied, rejected, total, rate,
                paymentMetrics.getDuplicatesBlocked(),
                payments.sumAmountByStatus(PaymentStatus.APPLIED),
                accounts.countByBalanceCentsLessThan(0)
        );
    }
}
