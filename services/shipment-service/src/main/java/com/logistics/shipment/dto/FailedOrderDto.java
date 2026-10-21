package com.logistics.shipment.dto;

import com.logistics.shipment.entity.Assignment;
import com.logistics.shipment.model.AssignmentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public class FailedOrderDto {

    private String assignmentId;
    private String orderId;
    private String orderNumber;
    private String customerId;
    private String recipientName;
    private String recipientPhone;
    private String deliveryAddress;
    private BigDecimal totalAmount;
    private AssignmentStatus status;
    private String driverId;
    private String previousDriverId;
    private Instant failedAt;
    private String failureReason;
    private String failureNotes;
    private String notes;
    private Instant createdAt;
    private Instant updatedAt;

    public FailedOrderDto() {
    }

    public FailedOrderDto(String assignmentId, String orderId, String orderNumber, String customerId,
                          String recipientName, String recipientPhone, String deliveryAddress,
                          BigDecimal totalAmount, AssignmentStatus status, String driverId,
                          String previousDriverId, Instant failedAt, String failureReason,
                          String failureNotes, String notes, Instant createdAt, Instant updatedAt) {
        this.assignmentId = assignmentId;
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.customerId = customerId;
        this.recipientName = recipientName;
        this.recipientPhone = recipientPhone;
        this.deliveryAddress = deliveryAddress;
        this.totalAmount = totalAmount;
        this.status = status;
        this.driverId = driverId;
        this.previousDriverId = previousDriverId;
        this.failedAt = failedAt;
        this.failureReason = failureReason;
        this.failureNotes = failureNotes;
        this.notes = notes;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static FailedOrderDto from(Assignment assignment) {
        if (assignment == null) {
            return null;
        }

        return new FailedOrderDto(
                assignment.getId(),
                assignment.getOrderId(),
                assignment.getOrderNumber(),
                assignment.getCustomerId(),
                assignment.getRecipientName(),
                assignment.getRecipientPhone(),
                assignment.getDeliveryAddress(),
                assignment.getTotalAmount(),
                assignment.getStatus(),
                assignment.getDriverId(),
                assignment.getPreviousDriverId(),
                assignment.getFailedAt(),
                assignment.getFailureReason(),
                assignment.getFailureNotes(),
                assignment.getNotes(),
                assignment.getCreatedAt(),
                assignment.getUpdatedAt()
        );
    }

    public String getAssignmentId() {
        return assignmentId;
    }

    public void setAssignmentId(String assignmentId) {
        this.assignmentId = assignmentId;
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

    public AssignmentStatus getStatus() {
        return status;
    }

    public void setStatus(AssignmentStatus status) {
        this.status = status;
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

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
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
}
