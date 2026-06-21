package com.hermes.orderapi.kafka;

import com.hermes.common.event.PaymentRequestedEvent;
import com.hermes.common.event.Topics;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class PaymentProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public PaymentProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publishes keyed by accountId so all charges against the same account land
     * on one partition and settle in order — the row lock then rarely contends
     * across partitions.
     */
    public void publish(PaymentRequestedEvent event) {
        kafkaTemplate.send(Topics.PAYMENTS_REQUESTED, event.accountId(), event);
    }
}
