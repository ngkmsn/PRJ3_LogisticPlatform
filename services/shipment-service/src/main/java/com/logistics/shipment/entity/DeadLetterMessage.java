package com.logistics.shipment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dead_letter_messages")
public class DeadLetterMessage {

    public static final String STATUS_PENDING_INSPECTION = "PENDING_INSPECTION";
    public static final String STATUS_REPLAYED = "REPLAYED";
    public static final String STATUS_DISCARDED = "DISCARDED";

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "original_message_id", length = 100)
    private String originalMessageId;

    @Column(name = "original_topic", length = 100, nullable = false)
    private String originalTopic;

    @Column(name = "original_partition")
    private Integer originalPartition;

    @Column(name = "original_offset")
    private Long originalOffset;

    @Column(name = "event_type", length = 100)
    private String eventType;

    @Column(name = "aggregate_id", length = 100)
    private String aggregateId;

    @Column(name = "payload", columnDefinition = "TEXT", nullable = false)
    private String payload;

    @Column(name = "exception_class", length = 255)
    private String exceptionClass;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "status", length = 30, nullable = false)
    private String status = STATUS_PENDING_INSPECTION;

    @Column(name = "failed_at", nullable = false)
    private Instant failedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "replayed_at")
    private Instant replayedAt;

    @Column(name = "replayed_by", length = 100)
    private String replayedBy;

    public DeadLetterMessage() {
        this.id = UUID.randomUUID().toString();
        this.status = STATUS_PENDING_INSPECTION;
        this.createdAt = Instant.now();
        this.failedAt = Instant.now();
    }

    public DeadLetterMessage(String originalMessageId, String originalTopic, String eventType,
                             String aggregateId, String payload, String exceptionClass,
                             String errorMessage, int retryCount) {
        this();
        this.originalMessageId = originalMessageId;
        this.originalTopic = originalTopic;
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.exceptionClass = exceptionClass;
        this.errorMessage = errorMessage != null && errorMessage.length() > 1000
                ? errorMessage.substring(0, 997) + "..."
                : errorMessage;
        this.retryCount = retryCount;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOriginalMessageId() {
        return originalMessageId;
    }

    public void setOriginalMessageId(String originalMessageId) {
        this.originalMessageId = originalMessageId;
    }

    public String getOriginalTopic() {
        return originalTopic;
    }

    public void setOriginalTopic(String originalTopic) {
        this.originalTopic = originalTopic;
    }

    public Integer getOriginalPartition() {
        return originalPartition;
    }

    public void setOriginalPartition(Integer originalPartition) {
        this.originalPartition = originalPartition;
    }

    public Long getOriginalOffset() {
        return originalOffset;
    }

    public void setOriginalOffset(Long originalOffset) {
        this.originalOffset = originalOffset;
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

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getExceptionClass() {
        return exceptionClass;
    }

    public void setExceptionClass(String exceptionClass) {
        this.exceptionClass = exceptionClass;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage != null && errorMessage.length() > 1000
                ? errorMessage.substring(0, 997) + "..."
                : errorMessage;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getFailedAt() {
        return failedAt;
    }

    public void setFailedAt(Instant failedAt) {
        this.failedAt = failedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getReplayedAt() {
        return replayedAt;
    }

    public void setReplayedAt(Instant replayedAt) {
        this.replayedAt = replayedAt;
    }

    public String getReplayedBy() {
        return replayedBy;
    }

    public void setReplayedBy(String replayedBy) {
        this.replayedBy = replayedBy;
    }
}
