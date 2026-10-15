package com.logistics.order.publisher;

import io.quarkus.arc.DefaultBean;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
@DefaultBean
public class DefaultKafkaOrderEventPublisher implements KafkaOrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(DefaultKafkaOrderEventPublisher.class);

    @ConfigProperty(name = "kafka.bootstrap.servers", defaultValue = "localhost:9092")
    String bootstrapServers;

    @ConfigProperty(name = "kafka.producer.timeout-seconds", defaultValue = "5")
    long sendTimeoutSeconds;

    private Producer<String, String> producer;

    private synchronized Producer<String, String> getProducer() {
        if (producer == null) {
            Properties props = new Properties();
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            props.put(ProducerConfig.ACKS_CONFIG, "all");
            props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
            props.put(ProducerConfig.RETRIES_CONFIG, "3");
            props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, "3000");
            props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, "5000");
            this.producer = new KafkaProducer<>(props);
        }
        return producer;
    }

    @Override
    public void publish(String topic, String key, String payload) throws Exception {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, payload);
        Future<RecordMetadata> future = getProducer().send(record);
        // Synchronously wait for Kafka broker acknowledgement
        RecordMetadata metadata = future.get(sendTimeoutSeconds, TimeUnit.SECONDS);
        log.info("Successfully published message to Kafka: topic='{}', partition={}, offset={}, key='{}'",
                metadata.topic(), metadata.partition(), metadata.offset(), key);
    }

    @PreDestroy
    public void close() {
        if (producer != null) {
            try {
                producer.close(java.time.Duration.ofSeconds(5));
            } catch (Exception e) {
                log.warn("Error closing Kafka producer: {}", e.getMessage());
            }
        }
    }
}
