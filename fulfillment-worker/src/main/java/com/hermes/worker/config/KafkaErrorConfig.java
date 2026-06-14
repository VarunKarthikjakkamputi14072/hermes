package com.hermes.worker.config;

import com.hermes.common.event.Topics;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Resilience policy for the consumers: a failing message is retried a few times
 * with a fixed back-off, then parked on a dead-letter topic instead of blocking
 * the partition forever — the classic poison-message pattern, ported from the
 * Argus DLQ idea into Spring Kafka. The recoverer routes each failure to the DLT
 * that matches its source topic, so orders and ingestion jobs are dead-lettered
 * independently.
 */
@Configuration
public class KafkaErrorConfig {

    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<Object, Object> template) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                template,
                (record, ex) -> {
                    String dlt = Topics.INGEST_REQUESTED.equals(record.topic())
                            ? Topics.INGEST_REQUESTED_DLT
                            : Topics.ORDERS_PLACED_DLT;
                    return new TopicPartition(dlt, record.partition());
                }
        );
        // 3 retries, 2s apart, before routing to the DLT
        return new DefaultErrorHandler(recoverer, new FixedBackOff(2_000L, 3L));
    }
}
