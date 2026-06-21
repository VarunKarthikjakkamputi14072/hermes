package com.hermes.worker.kafka;

import com.hermes.common.event.PaymentRequestedEvent;
import com.hermes.common.event.Topics;
import com.hermes.worker.service.LedgerResult;
import com.hermes.worker.service.LedgerService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PaymentConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentConsumer.class);

    private final LedgerService ledgerService;
    private final MeterRegistry meterRegistry;
    private final Map<LedgerResult, Counter> counters = new ConcurrentHashMap<>();

    public PaymentConsumer(LedgerService ledgerService, MeterRegistry meterRegistry) {
        this.ledgerService = ledgerService;
        this.meterRegistry = meterRegistry;
    }

    @KafkaListener(
            topics = Topics.PAYMENTS_REQUESTED,
            groupId = Topics.PAYMENT_GROUP,
            concurrency = "${hermes.worker.concurrency:3}"
    )
    public void onPaymentRequested(PaymentRequestedEvent event) {
        LedgerResult result = ledgerService.apply(event);
        record(result);
        if (log.isDebugEnabled()) {
            log.debug("Payment {} -> {}", event.paymentId(), result);
        }
    }

    private void record(LedgerResult result) {
        counters.computeIfAbsent(result, r ->
                Counter.builder("hermes.payments.processed")
                        .description("Payments settled by the ledger worker")
                        .tag("result", r.name().toLowerCase())
                        .register(meterRegistry)
        ).increment();
    }
}
