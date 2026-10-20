package com.logistics.shipment.dto;

import com.logistics.shipment.entity.Assignment;
import com.logistics.shipment.model.AssignmentStatus;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

public class UnassignedOrderDto {

    private String assignmentId;
    private String orderId;
    private String orderNumber;
    private String customerId;
    private String recipientName;
    private String recipientPhone;
    private String pickupAddress;
    private String dropoffAddress;
    private BigDecimal totalAmount;
    private AssignmentStatus status;
    private String unassignedReason;
    private Instant unassignedAt;
    private Long waitingTimeSeconds;
    private String notes;
    private Instant createdAt;
    private Instant updatedAt;

    public UnassignedOrderDto() {
    }

    public UnassignedOrderDto(String assignmentId, String orderId, String orderNumber, String customerId,
                              String recipientName, String recipientPhone, String pickupAddress,
                              String dropoffAddress, BigDecimal totalAmount, AssignmentStatus status,
                              String unassignedReason, Instant unassignedAt, Long waitingTimeSeconds,
                              String notes, Instant createdAt, Instant updatedAt) {
        this.assignmentId = assignmentId;
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.customerId = customerId;
        this.recipientName = recipientName;
        this.recipientPhone = recipientPhone;
        this.pickupAddress = pickupAddress;
        this.dropoffAddress = dropoffAddress;
        this.totalAmount = totalAmount;
        this.status = status;
        this.unassignedReason = unassignedReason;
        this.unassignedAt = unassignedAt;
        this.waitingTimeSeconds = waitingTimeSeconds;
        this.notes = notes;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static UnassignedOrderDto from(Assignment assignment) {
        if (assignment == null) {
            return null;
        }

        Instant waitReference = assignment.getUnassignedAt() != null
                ? assignment.getUnassignedAt()
                : assignment.getCreatedAt();
        Long waitingSeconds = waitReference != null
                ? Math.max(0, Duration.between(waitReference, Instant.now()).getSeconds())
                : null;

        // Pickup address: default warehouse address if separate pickup is not yet configured,
        // or derived from notes / system default
        String pickupAddress = "Main Fulfillment Hub, Hanoi";

        return new UnassignedOrderDto(
                assignment.getId(),
                assignment.getOrderId(),
                assignment.getOrderNumber(),
                assignment.getCustomerId(),
                assignment.getRecipientName(),
                assignment.getRecipientPhone(),
                pickupAddress,
                assignment.getDeliveryAddress(),
                assignment.getTotalAmount(),
                assignment.getStatus(),
                assignment.getUnassignedReason(),
                assignment.getUnassignedAt(),
                waitingSeconds,
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

    public String getPickupAddress() {
        return pickupAddress;
    }

    public void setPickupAddress(String pickupAddress) {
        this.pickupAddress = pickupAddress;
    }

    public String getDropoffAddress() {
        return dropoffAddress;
    }

    public void setDropoffAddress(String dropoffAddress) {
        this.dropoffAddress = dropoffAddress;
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

    public Long getWaitingTimeSeconds() {
        return waitingTimeSeconds;
    }

    public void setWaitingTimeSeconds(Long waitingTimeSeconds) {
        this.waitingTimeSeconds = waitingTimeSeconds;
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
