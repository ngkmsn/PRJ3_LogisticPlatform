package com.logistics.shipment.dto;

import com.logistics.shipment.entity.AssignmentHistory;
import com.logistics.shipment.model.AssignmentActionType;

import java.time.Instant;

public class AssignmentHistoryDto {

    private String id;
    private String orderId;
    private String assignmentId;
    private String previousDriverId;
    private String newDriverId;
    private AssignmentActionType actionType;
    private String reason;
    private String performedBy;
    private Instant createdAt;

    public AssignmentHistoryDto() {
    }

    public AssignmentHistoryDto(String id, String orderId, String assignmentId, String previousDriverId,
                                String newDriverId, AssignmentActionType actionType, String reason,
                                String performedBy, Instant createdAt) {
        this.id = id;
        this.orderId = orderId;
        this.assignmentId = assignmentId;
        this.previousDriverId = previousDriverId;
        this.newDriverId = newDriverId;
        this.actionType = actionType;
        this.reason = reason;
        this.performedBy = performedBy;
        this.createdAt = createdAt;
    }

    public static AssignmentHistoryDto from(AssignmentHistory history) {
        if (history == null) {
            return null;
        }
        return new AssignmentHistoryDto(
                history.getId(),
                history.getOrderId(),
                history.getAssignmentId(),
                history.getPreviousDriverId(),
                history.getNewDriverId(),
                history.getActionType(),
                history.getReason(),
                history.getPerformedBy(),
                history.getCreatedAt()
        );
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
}
