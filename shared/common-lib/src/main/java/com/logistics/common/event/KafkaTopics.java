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
}
