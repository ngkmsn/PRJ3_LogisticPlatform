package com.logistics.common.event;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Standard event envelope for asynchronous event-driven messaging across microservices.
 *
 * @param <T> the type of the event payload
 */
public class DomainEvent<T> implements Serializable {

    private String eventId;
    private String eventType;
    private String aggregateId;
    private Instant timestamp;
    private T payload;

    public DomainEvent() {
        this.eventId = UUID.randomUUID().toString();
        this.timestamp = Instant.now();
    }

    public DomainEvent(String eventType, String aggregateId, T payload) {
        this.eventId = UUID.randomUUID().toString();
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.timestamp = Instant.now();
        this.payload = payload;
    }

    public static <T> DomainEvent<T> of(String eventType, String aggregateId, T payload) {
        return new DomainEvent<>(eventType, aggregateId, payload);
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public void setAggregateId(String aggregateId) {
        this.aggregateId = aggregateId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public T getPayload() {
        return payload;
    }

    public void setPayload(T payload) {
        this.payload = payload;
    }

    @Override
    public String toString() {
        return "DomainEvent{" +
                "eventId='" + eventId + '\'' +
                ", eventType='" + eventType + '\'' +
                ", aggregateId='" + aggregateId + '\'' +
                ", timestamp=" + timestamp +
                ", payload=" + payload +
                '}';
    }
}
