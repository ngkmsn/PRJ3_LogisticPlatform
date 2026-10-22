package com.logistics.common.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Transfer Object representing an order requiring Operations triage and intervention (PB-049).
 * Focuses on UNASSIGNED orders and FAILED delivery orders awaiting assignment or reassignment.
 */
public class ActionableOrderDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private String orderId;
    private String orderNumber;
    private String status;           // UNASSIGNED, FAILED
    private String issueCategory;    // UNASSIGNED, FAILED
    private String issueSeverity;    // HIGH, CRITICAL
    private String customerId;
    private String recipientName;
    private String recipientPhone;
    private String deliveryAddress;
    private BigDecimal totalAmount;
    private Instant createdAt;
    private Instant updatedAt;

    // Driver & Assignment context
    private String driverId;
    private String previousDriverId;
    private Instant assignedAt;
    private String assignmentSource;
    private String assignedBy;

    // Unassigned context
    private String unassignedReason;
    private Instant unassignedAt;

    // Failure context
    private Instant failedAt;
    private String failureReason;
    private String failureNotes;

    // Actionable operations
    private List<String> allowedActions = new ArrayList<>();

    public ActionableOrderDto() {
    }

    public static String resolveSeverity(String status) {
        if ("FAILED".equalsIgnoreCase(status)) {
            return "CRITICAL";
        }
        return "HIGH";
    }

    public static List<String> resolveAllowedActions(String status) {
        List<String> actions = new ArrayList<>();
        if ("UNASSIGNED".equalsIgnoreCase(status)) {
            actions.add("ASSIGN");
        } else if ("FAILED".equalsIgnoreCase(status)) {
            actions.add("REASSIGN");
        }
        return actions;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
        this.issueCategory = status;
        this.issueSeverity = resolveSeverity(status);
        this.allowedActions = resolveAllowedActions(status);
    }

    public String getIssueCategory() {
        return issueCategory;
    }

    public void setIssueCategory(String issueCategory) {
        this.issueCategory = issueCategory;
    }

    public String getIssueSeverity() {
        return issueSeverity;
    }

    public void setIssueSeverity(String issueSeverity) {
        this.issueSeverity = issueSeverity;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public void setRecipientName(String recipientName) {
        this.recipientName = recipientName;
    }

    public String getRecipientPhone() {
        return recipientPhone;
    }

    public void setRecipientPhone(String recipientPhone) {
        this.recipientPhone = recipientPhone;
    }

    public String getDeliveryAddress() {
        return deliveryAddress;
    }

    public void setDeliveryAddress(String deliveryAddress) {
        this.deliveryAddress = deliveryAddress;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getDriverId() {
        return driverId;
    }

    public void setDriverId(String driverId) {
        this.driverId = driverId;
    }

    public String getPreviousDriverId() {
        return previousDriverId;
    }

    public void setPreviousDriverId(String previousDriverId) {
        this.previousDriverId = previousDriverId;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public void setAssignedAt(Instant assignedAt) {
        this.assignedAt = assignedAt;
    }

    public String getAssignmentSource() {
        return assignmentSource;
    }

    public void setAssignmentSource(String assignmentSource) {
        this.assignmentSource = assignmentSource;
    }

    public String getAssignedBy() {
        return assignedBy;
    }

    public void setAssignedBy(String assignedBy) {
        this.assignedBy = assignedBy;
    }

    public String getUnassignedReason() {
        return unassignedReason;
    }

    public void setUnassignedReason(String unassignedReason) {
        this.unassignedReason = unassignedReason;
    }

    public Instant getUnassignedAt() {
        return unassignedAt;
    }

    public void setUnassignedAt(Instant unassignedAt) {
        this.unassignedAt = unassignedAt;
    }

    public Instant getFailedAt() {
        return failedAt;
    }

    public void setFailedAt(Instant failedAt) {
        this.failedAt = failedAt;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public String getFailureNotes() {
        return failureNotes;
    }

    public void setFailureNotes(String failureNotes) {
        this.failureNotes = failureNotes;
    }

    public List<String> getAllowedActions() {
        return allowedActions;
    }

    public void setAllowedActions(List<String> allowedActions) {
        this.allowedActions = allowedActions;
    }
}
