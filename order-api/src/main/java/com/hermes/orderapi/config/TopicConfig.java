package com.hermes.orderapi.config;

import com.hermes.common.event.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Topics are declared here and created on startup by Spring's KafkaAdmin, so the
 * stack is self-bootstrapping — no manual {@code kafka-topics.sh} step. Six
 * partitions give the workers room to scale out horizontally.
 */
@Configuration
public class TopicConfig {

    @Bean
    public NewTopic ordersPlaced() {
        return TopicBuilder.name(Topics.ORDERS_PLACED)
                .partitions(6)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic ordersPlacedDlt() {
        return TopicBuilder.name(Topics.ORDERS_PLACED_DLT)
                .partitions(6)
                .replicas(1)
                .build();
    }
}
