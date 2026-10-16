package com.logistics.shipment.entity;

import com.logistics.shipment.model.InboxStatus;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(
        name = "inbox_events",
        indexes = {
                @Index(name = "idx_inbox_events_event_id", columnList = "event_id"),
                @Index(name = "idx_inbox_events_aggregate_id", columnList = "aggregate_id")
        }
)
public class InboxEvent extends PanacheEntityBase {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "event_id", length = 100, nullable = false, unique = true)
    private String eventId;

    @Column(name = "event_type", length = 50, nullable = false)
    private String eventType;

    @Column(name = "aggregate_id", length = 100, nullable = false)
    private String aggregateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private InboxStatus status;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public InboxEvent() {
    }

    public InboxEvent(String id, String eventId, String eventType, String aggregateId,
                      InboxStatus status, Instant processedAt, Instant createdAt) {
        this.id = id;
        this.eventId = eventId;
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.status = status;
        this.processedAt = processedAt;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public InboxStatus getStatus() {
        return status;
    }

    public void setStatus(InboxStatus status) {
        this.status = status;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InboxEvent that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "InboxEvent{" +
                "id='" + id + '\'' +
                ", eventId='" + eventId + '\'' +
                ", eventType='" + eventType + '\'' +
                ", aggregateId='" + aggregateId + '\'' +
                ", status=" + status +
                ", processedAt=" + processedAt +
                '}';
    }
}
