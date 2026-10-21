package com.logistics.shipment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.logistics.shipment.entity.Assignment;
import com.logistics.shipment.model.AssignmentStatus;

import java.math.BigDecimal;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class DriverOrderDto {

    private String assignmentId;
    private String orderId;
    private String orderNumber;
    private String recipientName;
    private String recipientPhone;
    private String pickupAddress;
    private String deliveryAddress;
    private BigDecimal totalAmount;
    private AssignmentStatus status;
    private Instant assignedAt;
    private Instant pickedUpAt;
    private String pickupNotes;
    private Instant transitStartedAt;
    private String transitNotes;
    private Instant deliveredAt;
    private String deliveryNotes;
    private String proofOfDelivery;
    private Instant failedAt;
    private String failureReason;
    private String failureNotes;
    private String notes;
    private Instant createdAt;

    public DriverOrderDto() {
    }

    public DriverOrderDto(String assignmentId, String orderId, String orderNumber,
                          String recipientName, String recipientPhone, String pickupAddress,
                          String deliveryAddress, BigDecimal totalAmount, AssignmentStatus status,
                          Instant assignedAt, Instant pickedUpAt, String pickupNotes,
                          Instant transitStartedAt, String transitNotes,
                          Instant deliveredAt, String deliveryNotes, String proofOfDelivery,
                          Instant failedAt, String failureReason, String failureNotes,
                          String notes, Instant createdAt) {
        this.assignmentId = assignmentId;
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.recipientName = recipientName;
        this.recipientPhone = recipientPhone;
        this.pickupAddress = pickupAddress;
        this.deliveryAddress = deliveryAddress;
        this.totalAmount = totalAmount;
        this.status = status;
        this.assignedAt = assignedAt;
        this.pickedUpAt = pickedUpAt;
        this.pickupNotes = pickupNotes;
        this.transitStartedAt = transitStartedAt;
        this.transitNotes = transitNotes;
        this.deliveredAt = deliveredAt;
        this.deliveryNotes = deliveryNotes;
        this.proofOfDelivery = proofOfDelivery;
        this.failedAt = failedAt;
        this.failureReason = failureReason;
        this.failureNotes = failureNotes;
        this.notes = notes;
        this.createdAt = createdAt;
    }

    public DriverOrderDto(String assignmentId, String orderId, String orderNumber,
                          String recipientName, String recipientPhone, String pickupAddress,
                          String deliveryAddress, BigDecimal totalAmount, AssignmentStatus status,
                          Instant assignedAt, Instant pickedUpAt, String pickupNotes,
                          Instant transitStartedAt, String transitNotes,
                          Instant deliveredAt, String deliveryNotes, String proofOfDelivery,
                          String notes, Instant createdAt) {
        this(assignmentId, orderId, orderNumber, recipientName, recipientPhone, pickupAddress,
                deliveryAddress, totalAmount, status, assignedAt, pickedUpAt, pickupNotes,
                transitStartedAt, transitNotes, deliveredAt, deliveryNotes, proofOfDelivery,
                null, null, null, notes, createdAt);
    }

    public DriverOrderDto(String assignmentId, String orderId, String orderNumber,
                          String recipientName, String recipientPhone, String pickupAddress,
                          String deliveryAddress, BigDecimal totalAmount, AssignmentStatus status,
                          Instant assignedAt, Instant pickedUpAt, String pickupNotes,
                          Instant transitStartedAt, String transitNotes,
                          String notes, Instant createdAt) {
        this(assignmentId, orderId, orderNumber, recipientName, recipientPhone, pickupAddress,
                deliveryAddress, totalAmount, status, assignedAt, pickedUpAt, pickupNotes,
                transitStartedAt, transitNotes, null, null, null, null, null, null, notes, createdAt);
    }

    public DriverOrderDto(String assignmentId, String orderId, String orderNumber,
                          String recipientName, String recipientPhone, String pickupAddress,
                          String deliveryAddress, BigDecimal totalAmount, AssignmentStatus status,
                          Instant assignedAt, Instant pickedUpAt, String pickupNotes,
                          String notes, Instant createdAt) {
        this(assignmentId, orderId, orderNumber, recipientName, recipientPhone, pickupAddress,
                deliveryAddress, totalAmount, status, assignedAt, pickedUpAt, pickupNotes,
                null, null, null, null, null, null, null, null, notes, createdAt);
    }

    public DriverOrderDto(String assignmentId, String orderId, String orderNumber,
                          String recipientName, String recipientPhone, String pickupAddress,
                          String deliveryAddress, BigDecimal totalAmount, AssignmentStatus status,
                          Instant assignedAt, String notes, Instant createdAt) {
        this(assignmentId, orderId, orderNumber, recipientName, recipientPhone, pickupAddress,
                deliveryAddress, totalAmount, status, assignedAt, null, null, null, null, null, null, null, null, null, null, notes, createdAt);
    }

    public static DriverOrderDto from(Assignment assignment) {
        if (assignment == null) {
            return null;
        }

        // Default pickup hub if not explicitly configured on assignment
        String defaultPickupLocation = "123 Lê Duẩn, Quận 1, TP.HCM";

        return new DriverOrderDto(
                assignment.getId(),
                assignment.getOrderId(),
                assignment.getOrderNumber(),
                assignment.getRecipientName(),
                assignment.getRecipientPhone(),
                defaultPickupLocation,
                assignment.getDeliveryAddress(),
                assignment.getTotalAmount(),
                assignment.getStatus(),
                assignment.getAssignedAt(),
                assignment.getPickedUpAt(),
                assignment.getPickupNotes(),
                assignment.getTransitStartedAt(),
                assignment.getTransitNotes(),
                assignment.getDeliveredAt(),
                assignment.getDeliveryNotes(),
                assignment.getProofOfDelivery(),
                assignment.getFailedAt(),
                assignment.getFailureReason(),
                assignment.getFailureNotes(),
                assignment.getNotes(),
                assignment.getCreatedAt()
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

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public void setAssignedAt(Instant assignedAt) {
        this.assignedAt = assignedAt;
    }

    public Instant getPickedUpAt() {
        return pickedUpAt;
    }

    public void setPickedUpAt(Instant pickedUpAt) {
        this.pickedUpAt = pickedUpAt;
    }

    public String getPickupNotes() {
        return pickupNotes;
    }

    public void setPickupNotes(String pickupNotes) {
        this.pickupNotes = pickupNotes;
    }

    public Instant getTransitStartedAt() {
        return transitStartedAt;
    }

    public void setTransitStartedAt(Instant transitStartedAt) {
        this.transitStartedAt = transitStartedAt;
    }

    public String getTransitNotes() {
        return transitNotes;
    }

    public void setTransitNotes(String transitNotes) {
        this.transitNotes = transitNotes;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public void setDeliveredAt(Instant deliveredAt) {
        this.deliveredAt = deliveredAt;
    }

    public String getDeliveryNotes() {
        return deliveryNotes;
    }

    public void setDeliveryNotes(String deliveryNotes) {
        this.deliveryNotes = deliveryNotes;
    }

    public String getProofOfDelivery() {
        return proofOfDelivery;
    }

    public void setProofOfDelivery(String proofOfDelivery) {
        this.proofOfDelivery = proofOfDelivery;
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
}
