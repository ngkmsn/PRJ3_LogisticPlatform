package com.logistics.common.dto;

import java.io.Serializable;
import java.time.Instant;

/**
 * Actionable operational alert or exception requiring Dispatcher attention (PB-048).
 */
public class AttentionItemDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    private String type;        // UNASSIGNED_ORDER, FAILED_ORDER, STALE_GPS, HIGH_SLA_RISK
    private String severity;    // HIGH, MEDIUM, LOW
    private String title;
    private String description;
    private String referenceId; // orderId or driverId
    private String orderNumber;
    private String age;
    private Instant createdAt;

    public AttentionItemDto() {
    }

    public AttentionItemDto(String id, String type, String severity, String title,
                            String description, String referenceId, String orderNumber,
                            String age, Instant createdAt) {
        this.id = id;
        this.type = type;
        this.severity = severity;
        this.title = title;
        this.description = description;
        this.referenceId = referenceId;
        this.orderNumber = orderNumber;
        this.age = age;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public AttentionItemDto(String type, String severity, String title, String description) {
        this(java.util.UUID.randomUUID().toString(), type, severity, title, description, null, null, null, Instant.now());
    }

    public AttentionItemDto(String type, String severity, String title, String description, String referenceId, String orderNumber) {
        this(java.util.UUID.randomUUID().toString(), type, severity, title, description, referenceId, orderNumber, null, Instant.now());
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public String getAge() {
        return age;
    }

    public void setAge(String age) {
        this.age = age;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
