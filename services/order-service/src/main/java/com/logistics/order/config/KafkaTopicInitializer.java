package com.logistics.order.config;

import com.logistics.common.event.KafkaTopics;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.runtime.configuration.ConfigUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Properties;
import java.util.Set;

@ApplicationScoped
public class KafkaTopicInitializer {

    private static final Logger log = LoggerFactory.getLogger(KafkaTopicInitializer.class);

    @ConfigProperty(name = "kafka.bootstrap.servers", defaultValue = "localhost:9092")
    String bootstrapServers;

    public void onStart(@Observes StartupEvent ev) {
        if (ConfigUtils.isProfileActive("test")) {
            return;
        }

        try {
            Properties config = new Properties();
            config.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            config.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, "3000");

            try (AdminClient admin = AdminClient.create(config)) {
                Set<String> existingTopics = admin.listTopics().names().get();

                List<NewTopic> topics = List.of(
                        new NewTopic(KafkaTopics.ORDER_EVENTS, 1, (short) 1),
                        new NewTopic(KafkaTopics.SHIPMENT_EVENTS, 1, (short) 1),
                        new NewTopic(KafkaTopics.NOTIFICATION_EVENTS, 1, (short) 1),
                        new NewTopic(KafkaTopics.USER_EVENTS, 1, (short) 1)
                );

                List<NewTopic> topicsToCreate = topics.stream()
                        .filter(t -> !existingTopics.contains(t.name()))
                        .toList();

                if (!topicsToCreate.isEmpty()) {
                    admin.createTopics(topicsToCreate).all().get();
                    log.info("Initialized Kafka topics: {}", topicsToCreate.stream().map(NewTopic::name).toList());
                }
            }
        } catch (Exception e) {
            log.warn("Kafka broker not reachable at {} during startup topic initialization: {}", bootstrapServers, e.getMessage());
        }
    }
}
