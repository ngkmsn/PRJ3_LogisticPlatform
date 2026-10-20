package com.logistics.shipment.dto;

import com.logistics.shipment.entity.Assignment;
import com.logistics.shipment.model.AssignmentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public class AssignmentResponseDto {

    private String assignmentId;
    private String orderId;
    private String orderNumber;
    private String customerId;
    private String driverId;
    private AssignmentStatus status;
    private Instant assignedAt;
    private String assignmentSource;
    private String assignedBy;
    private BigDecimal totalAmount;
    private String deliveryAddress;
    private String notes;
    private String previousDriverId;
    private String reassignReason;
    private Instant reassignedAt;
    private String reassignedBy;
    private Instant createdAt;
    private Instant updatedAt;

    public AssignmentResponseDto() {
    }

    public AssignmentResponseDto(String assignmentId, String orderId, String orderNumber, String customerId,
                                 String driverId, AssignmentStatus status, Instant assignedAt,
                                 String assignmentSource, String assignedBy, BigDecimal totalAmount,
                                 String deliveryAddress, String notes, Instant createdAt, Instant updatedAt) {
        this(assignmentId, orderId, orderNumber, customerId, driverId, status, assignedAt,
                assignmentSource, assignedBy, totalAmount, deliveryAddress, notes,
                null, null, null, null, createdAt, updatedAt);
    }

    public AssignmentResponseDto(String assignmentId, String orderId, String orderNumber, String customerId,
                                 String driverId, AssignmentStatus status, Instant assignedAt,
                                 String assignmentSource, String assignedBy, BigDecimal totalAmount,
                                 String deliveryAddress, String notes,
                                 String previousDriverId, String reassignReason, Instant reassignedAt, String reassignedBy,
                                 Instant createdAt, Instant updatedAt) {
        this.assignmentId = assignmentId;
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.customerId = customerId;
        this.driverId = driverId;
        this.status = status;
        this.assignedAt = assignedAt;
        this.assignmentSource = assignmentSource;
        this.assignedBy = assignedBy;
        this.totalAmount = totalAmount;
        this.deliveryAddress = deliveryAddress;
        this.notes = notes;
        this.previousDriverId = previousDriverId;
        this.reassignReason = reassignReason;
        this.reassignedAt = reassignedAt;
        this.reassignedBy = reassignedBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static AssignmentResponseDto from(Assignment assignment) {
        if (assignment == null) {
            return null;
        }
        return new AssignmentResponseDto(
                assignment.getId(),
                assignment.getOrderId(),
                assignment.getOrderNumber(),
                assignment.getCustomerId(),
                assignment.getDriverId(),
                assignment.getStatus(),
                assignment.getAssignedAt(),
                assignment.getAssignmentSource(),
                assignment.getAssignedBy(),
                assignment.getTotalAmount(),
                assignment.getDeliveryAddress(),
                assignment.getNotes(),
                assignment.getPreviousDriverId(),
                assignment.getReassignReason(),
                assignment.getReassignedAt(),
                assignment.getReassignedBy(),
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

    public String getDriverId() {
        return driverId;
    }

    public void setDriverId(String driverId) {
        this.driverId = driverId;
    }

    public AssignmentStatus getStatus() {
        return status;
    }

    public void setStatus(AssignmentStatus status) {
        this.status = status;
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

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getDeliveryAddress() {
        return deliveryAddress;
    }

    public void setDeliveryAddress(String deliveryAddress) {
        this.deliveryAddress = deliveryAddress;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getPreviousDriverId() {
        return previousDriverId;
    }

    public void setPreviousDriverId(String previousDriverId) {
        this.previousDriverId = previousDriverId;
    }

    public String getReassignReason() {
        return reassignReason;
    }

    public void setReassignReason(String reassignReason) {
        this.reassignReason = reassignReason;
    }

    public Instant getReassignedAt() {
        return reassignedAt;
    }

    public void setReassignedAt(Instant reassignedAt) {
        this.reassignedAt = reassignedAt;
    }

    public String getReassignedBy() {
        return reassignedBy;
    }

    public void setReassignedBy(String reassignedBy) {
        this.reassignedBy = reassignedBy;
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
