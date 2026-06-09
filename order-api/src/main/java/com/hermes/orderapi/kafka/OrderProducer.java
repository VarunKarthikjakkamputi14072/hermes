package com.hermes.orderapi.kafka;

import com.hermes.common.event.OrderPlacedEvent;
import com.hermes.common.event.Topics;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class OrderProducer {

    private final KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;

    public OrderProducer(KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publishes the event keyed by SKU. Keying by SKU keeps all orders for the
     * same product on one partition, so the worker processes them in order and
     * the pessimistic lock rarely contends across partitions.
     */
    public void publish(OrderPlacedEvent event) {
        kafkaTemplate.send(Topics.ORDERS_PLACED, event.sku(), event);
    }
}
