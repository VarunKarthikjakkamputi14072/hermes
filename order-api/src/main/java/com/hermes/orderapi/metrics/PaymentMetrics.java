package com.hermes.orderapi.metrics;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Process-wide counter for idempotent dedupes — every time a charge is blocked
 * because its idempotency key already existed. This is the headline number on
 * the dashboard: "double-charges prevented".
 */
@Component
public class PaymentMetrics {

    private final AtomicLong duplicatesBlocked = new AtomicLong();

    public void recordDuplicate() {
        duplicatesBlocked.incrementAndGet();
    }

    public long getDuplicatesBlocked() {
        return duplicatesBlocked.get();
    }
}
