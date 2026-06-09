package com.hermes.worker.kafka;

import com.hermes.common.event.OrderPlacedEvent;
import com.hermes.common.event.Topics;
import com.hermes.worker.service.FulfillmentResult;
import com.hermes.worker.service.FulfillmentService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OrderConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderConsumer.class);

    private final FulfillmentService fulfillmentService;
    private final MeterRegistry meterRegistry;
    private final Map<FulfillmentResult, Counter> counters = new ConcurrentHashMap<>();

    public OrderConsumer(FulfillmentService fulfillmentService, MeterRegistry meterRegistry) {
        this.fulfillmentService = fulfillmentService;
        this.meterRegistry = meterRegistry;
    }

    @KafkaListener(
            topics = Topics.ORDERS_PLACED,
            groupId = Topics.FULFILMENT_GROUP,
            concurrency = "${hermes.worker.concurrency:3}"
    )
    public void onOrderPlaced(OrderPlacedEvent event) {
        FulfillmentResult result = fulfillmentService.fulfil(event);
        record(result);
        if (log.isDebugEnabled()) {
            log.debug("Order {} -> {}", event.orderId(), result);
        }
    }

    private void record(FulfillmentResult result) {
        counters.computeIfAbsent(result, r ->
                Counter.builder("hermes.orders.processed")
                        .description("Orders processed by the fulfilment worker")
                        .tag("result", r.name().toLowerCase())
                        .register(meterRegistry)
        ).increment();
    }
}
