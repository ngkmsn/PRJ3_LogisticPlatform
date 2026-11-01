package com.logistics.common.dto;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Operational telemetry metrics DTO for Dead Letter Queues (PB-053).
 */
public class DeadLetterMetricsDto {

    private long totalPending;
    private long totalReplayed;
    private long totalDiscarded;
    private long totalMessages;
    private Instant generatedAt;
    private Map<String, Long> messagesByStatus = new HashMap<>();

    public DeadLetterMetricsDto() {
        this.generatedAt = Instant.now();
    }

    public DeadLetterMetricsDto(long totalPending, long totalReplayed, long totalDiscarded) {
        this();
        this.totalPending = totalPending;
        this.totalReplayed = totalReplayed;
        this.totalDiscarded = totalDiscarded;
        this.totalMessages = totalPending + totalReplayed + totalDiscarded;
        this.messagesByStatus.put("PENDING_INSPECTION", totalPending);
        this.messagesByStatus.put("REPLAYED", totalReplayed);
        this.messagesByStatus.put("DISCARDED", totalDiscarded);
    }

    public long getTotalPending() {
        return totalPending;
    }

    public void setTotalPending(long totalPending) {
        this.totalPending = totalPending;
    }

    public long getTotalReplayed() {
        return totalReplayed;
    }

    public void setTotalReplayed(long totalReplayed) {
        this.totalReplayed = totalReplayed;
    }

    public long getTotalDiscarded() {
        return totalDiscarded;
    }

    public void setTotalDiscarded(long totalDiscarded) {
        this.totalDiscarded = totalDiscarded;
    }

    public long getTotalMessages() {
        return totalMessages;
    }

    public void setTotalMessages(long totalMessages) {
        this.totalMessages = totalMessages;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(Instant generatedAt) {
        this.generatedAt = generatedAt;
    }

    public Map<String, Long> getMessagesByStatus() {
        return messagesByStatus;
    }

    public void setMessagesByStatus(Map<String, Long> messagesByStatus) {
        this.messagesByStatus = messagesByStatus;
    }
}
