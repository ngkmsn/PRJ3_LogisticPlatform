package com.logistics.order.publisher;

public interface KafkaOrderEventPublisher {

    /**
     * Publishes a message to a Kafka topic and waits for acknowledgement.
     *
     * @param topic   target Kafka topic name
     * @param key     message key (e.g. orderId or orderNumber)
     * @param payload serialized JSON message payload
     * @throws Exception if sending fails or confirmation is not received
     */
    void publish(String topic, String key, String payload) throws Exception;
}
