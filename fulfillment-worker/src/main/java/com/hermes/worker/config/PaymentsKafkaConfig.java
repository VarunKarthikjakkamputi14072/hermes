package com.hermes.worker.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermes.common.event.PaymentRequestedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Payments now arrive via Debezium (outbox CDC), so the Kafka value is the raw
 * event JSON with NO Spring {@code __TypeId__} header. This factory deserializes
 * it directly into {@link PaymentRequestedEvent} (type headers off) using the
 * Spring ObjectMapper so the Instant field parses. Orders and ingestion keep the
 * default, header-based factory.
 */
@Configuration
public class PaymentsKafkaConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentRequestedEvent>
            paymentsKafkaListenerContainerFactory(KafkaProperties properties,
                                                  ObjectMapper objectMapper,
                                                  DefaultErrorHandler errorHandler) {

        JsonDeserializer<PaymentRequestedEvent> json =
                new JsonDeserializer<>(PaymentRequestedEvent.class, objectMapper);
        json.setUseTypeHeaders(false);
        json.addTrustedPackages("com.hermes.common.event");

        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getBootstrapServers());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);

        DefaultKafkaConsumerFactory<String, PaymentRequestedEvent> consumerFactory =
                new DefaultKafkaConsumerFactory<>(props,
                        new StringDeserializer(),
                        new ErrorHandlingDeserializer<>(json));

        ConcurrentKafkaListenerContainerFactory<String, PaymentRequestedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler); // routes poison messages to payments.requested.DLT
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        return factory;
    }
}
