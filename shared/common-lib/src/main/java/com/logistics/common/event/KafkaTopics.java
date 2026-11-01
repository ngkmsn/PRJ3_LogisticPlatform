package com.logistics.common.event;

/**
 * Foundation Kafka topic names used across logistics microservices.
 */
public final class KafkaTopics {

    private KafkaTopics() {
        // Utility class
    }

    public static final String ORDER_EVENTS = "logistics.order.events";
    public static final String SHIPMENT_EVENTS = "logistics.shipment.events";
    public static final String NOTIFICATION_EVENTS = "logistics.notification.events";
    public static final String USER_EVENTS = "logistics.user.events";

    // Dead Letter Queue topics (PB-053)
    public static final String ORDER_EVENTS_DLQ = "logistics.order.events.dlq";
    public static final String SHIPMENT_EVENTS_DLQ = "logistics.shipment.events.dlq";
    public static final String NOTIFICATION_EVENTS_DLQ = "logistics.notification.events.dlq";

    /**
     * Derives the corresponding DLQ topic for a given original event topic.
     */
    public static String dlqTopicFor(String topic) {
        if (topic == null || topic.isBlank()) {
            return "logistics.dead-letter.events";
        }
        return topic.endsWith(".dlq") ? topic : topic + ".dlq";
    }
}
