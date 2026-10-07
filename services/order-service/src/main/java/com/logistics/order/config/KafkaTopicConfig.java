package com.logistics.order.config;

import com.logistics.common.event.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
public class KafkaTopicConfig {

    @Bean
    public NewTopic orderEventsTopic() {
        return TopicBuilder.name(KafkaTopics.ORDER_EVENTS)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic shipmentEventsTopic() {
        return TopicBuilder.name(KafkaTopics.SHIPMENT_EVENTS)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic notificationEventsTopic() {
        return TopicBuilder.name(KafkaTopics.NOTIFICATION_EVENTS)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic userEventsTopic() {
        return TopicBuilder.name(KafkaTopics.USER_EVENTS)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
