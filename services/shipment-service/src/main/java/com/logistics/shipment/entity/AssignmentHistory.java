package com.logistics.shipment.entity;

import com.logistics.shipment.model.AssignmentActionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "assignment_history")
public class AssignmentHistory {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "order_id", length = 100, nullable = false)
    private String orderId;

    @Column(name = "assignment_id", length = 100, nullable = false)
    private String assignmentId;

    @Column(name = "previous_driver_id", length = 100)
    private String previousDriverId;

    @Column(name = "new_driver_id", length = 100, nullable = false)
    private String newDriverId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", length = 30, nullable = false)
    private AssignmentActionType actionType;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "performed_by", length = 100, nullable = false)
    private String performedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public AssignmentHistory() {
    }

    public AssignmentHistory(String orderId, String assignmentId, String previousDriverId,
                             String newDriverId, AssignmentActionType actionType,
                             String reason, String performedBy) {
        this.id = UUID.randomUUID().toString();
        this.orderId = orderId;
        this.assignmentId = assignmentId;
        this.previousDriverId = previousDriverId;
        this.newDriverId = newDriverId;
        this.actionType = actionType;
        this.reason = reason;
        this.performedBy = performedBy;
        this.createdAt = Instant.now();
    }

    public AssignmentHistory(String id, String orderId, String assignmentId, String previousDriverId,
                             String newDriverId, AssignmentActionType actionType,
                             String reason, String performedBy, Instant createdAt) {
        this.id = id;
        this.orderId = orderId;
        this.assignmentId = assignmentId;
        this.previousDriverId = previousDriverId;
        this.newDriverId = newDriverId;
        this.actionType = actionType;
        this.reason = reason;
        this.performedBy = performedBy;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        if (this.id == null || this.id.isBlank()) {
            this.id = UUID.randomUUID().toString();
        }
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getAssignmentId() {
        return assignmentId;
    }

    public void setAssignmentId(String assignmentId) {
        this.assignmentId = assignmentId;
    }

    public String getPreviousDriverId() {
        return previousDriverId;
    }

    public void setPreviousDriverId(String previousDriverId) {
        this.previousDriverId = previousDriverId;
    }

    public String getNewDriverId() {
        return newDriverId;
    }

    public void setNewDriverId(String newDriverId) {
        this.newDriverId = newDriverId;
    }

    public AssignmentActionType getActionType() {
        return actionType;
    }

    public void setActionType(AssignmentActionType actionType) {
        this.actionType = actionType;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getPerformedBy() {
        return performedBy;
    }

    public void setPerformedBy(String performedBy) {
        this.performedBy = performedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "AssignmentHistory{" +
                "id='" + id + '\'' +
                ", orderId='" + orderId + '\'' +
                ", assignmentId='" + assignmentId + '\'' +
                ", previousDriverId='" + previousDriverId + '\'' +
                ", newDriverId='" + newDriverId + '\'' +
                ", actionType=" + actionType +
                ", reason='" + reason + '\'' +
                ", performedBy='" + performedBy + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
