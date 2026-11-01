package com.logistics.common.dlq;

import java.time.Instant;
import java.util.UUID;

/**
 * Standard Dead Letter Queue envelope packaging unprocessable or exhausted messages.
 * Contains forensic metadata (failure reason, attempts, timestamp, exception class)
 * and preserves sanitized payload for investigation, auditing, and idempotent replay.
 */
public class DeadLetterEnvelope<T> {

    public static final String STATUS_PENDING_INSPECTION = "PENDING_INSPECTION";
    public static final String STATUS_REPLAYED = "REPLAYED";
    public static final String STATUS_DISCARDED = "DISCARDED";

    private String deadLetterId;
    private String originalMessageId;
    private String originalTopic;
    private Integer originalPartition;
    private Long originalOffset;
    private String eventType;
    private String aggregateId;
    private T payload;
    private String exceptionClass;
    private String errorMessage;
    private int attempts;
    private Instant failedAt;
    private String status;
    private Instant replayedAt;
    private String replayedBy;

    public DeadLetterEnvelope() {
        this.deadLetterId = UUID.randomUUID().toString();
        this.failedAt = Instant.now();
        this.status = STATUS_PENDING_INSPECTION;
    }

    public DeadLetterEnvelope(String originalMessageId, String originalTopic, String eventType,
                              String aggregateId, T payload, String exceptionClass,
                              String errorMessage, int attempts) {
        this();
        this.originalMessageId = originalMessageId;
        this.originalTopic = originalTopic;
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.exceptionClass = exceptionClass;
        this.errorMessage = DeadLetterSanitizer.sanitize(errorMessage);
        this.attempts = attempts;
    }

    public String getDeadLetterId() {
        return deadLetterId;
    }

    public void setDeadLetterId(String deadLetterId) {
        this.deadLetterId = deadLetterId;
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

    public T getPayload() {
        return payload;
    }

    public void setPayload(T payload) {
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
        this.errorMessage = DeadLetterSanitizer.sanitize(errorMessage);
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public Instant getFailedAt() {
        return failedAt;
    }

    public void setFailedAt(Instant failedAt) {
        this.failedAt = failedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    @Override
    public String toString() {
        return "DeadLetterEnvelope{" +
                "deadLetterId='" + deadLetterId + '\'' +
                ", originalMessageId='" + originalMessageId + '\'' +
                ", originalTopic='" + originalTopic + '\'' +
                ", eventType='" + eventType + '\'' +
                ", aggregateId='" + aggregateId + '\'' +
                ", exceptionClass='" + exceptionClass + '\'' +
                ", attempts=" + attempts +
                ", failedAt=" + failedAt +
                ", status='" + status + '\'' +
                '}';
    }
}
