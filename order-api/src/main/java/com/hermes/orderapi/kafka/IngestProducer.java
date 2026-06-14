package com.hermes.orderapi.kafka;

import com.hermes.common.event.IngestRequestedEvent;
import com.hermes.common.event.Topics;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class IngestProducer {

    private final KafkaTemplate<String, IngestRequestedEvent> kafkaTemplate;

    public IngestProducer(KafkaTemplate<String, IngestRequestedEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publishes the event keyed by docId. Keying by document keeps all of one
     * document's work on a single partition, so its chunks are processed in order
     * and progress for that doc advances monotonically — the same partitioning
     * rationale the order engine uses for SKUs.
     */
    public void publish(IngestRequestedEvent event) {
        kafkaTemplate.send(Topics.INGEST_REQUESTED, event.docId(), event);
    }
}
