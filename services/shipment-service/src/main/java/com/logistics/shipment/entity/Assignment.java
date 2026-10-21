package com.logistics.shipment.entity;

import com.logistics.shipment.model.AssignmentStatus;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(
        name = "assignments",
        indexes = {
                @Index(name = "idx_assignments_order_id", columnList = "order_id"),
                @Index(name = "idx_assignments_status", columnList = "status")
        }
)
public class Assignment extends PanacheEntityBase {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "order_id", length = 100, nullable = false, unique = true)
    private String orderId;

    @Column(name = "order_number", length = 50)
    private String orderNumber;

    @Column(name = "customer_id", length = 100, nullable = false)
    private String customerId;

    @Column(name = "recipient_name", length = 100)
    private String recipientName;

    @Column(name = "recipient_phone", length = 20)
    private String recipientPhone;

    @Column(name = "delivery_address", length = 255, nullable = false)
    private String deliveryAddress;

    @Column(name = "total_amount", precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private AssignmentStatus status;

    @Column(name = "driver_id", length = 100)
    private String driverId;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Column(name = "unassigned_reason", length = 100)
    private String unassignedReason;

    @Column(name = "unassigned_at")
    private Instant unassignedAt;

    @Column(name = "assignment_source", length = 30)
    private String assignmentSource;

    @Column(name = "assigned_by", length = 100)
    private String assignedBy;

    @Column(name = "previous_driver_id", length = 100)
    private String previousDriverId;

    @Column(name = "reassign_reason", length = 255)
    private String reassignReason;

    @Column(name = "reassigned_at")
    private Instant reassignedAt;

    @Column(name = "reassigned_by", length = 100)
    private String reassignedBy;

    @Column(name = "picked_up_at")
    private Instant pickedUpAt;

    @Column(name = "pickup_notes", length = 255)
    private String pickupNotes;

    @Column(name = "transit_started_at")
    private Instant transitStartedAt;

    @Column(name = "transit_notes", length = 255)
    private String transitNotes;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "delivery_notes", length = 255)
    private String deliveryNotes;

    @Column(name = "proof_of_delivery", length = 255)
    private String proofOfDelivery;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "failure_notes", length = 255)
    private String failureNotes;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public Assignment() {
    }

    public Assignment(String id, String orderId, String orderNumber, String customerId,
                      String recipientName, String recipientPhone, String deliveryAddress,
                      BigDecimal totalAmount, AssignmentStatus status, String driverId,
                      String notes, Instant createdAt, Instant updatedAt) {
        this(id, orderId, orderNumber, customerId, recipientName, recipientPhone, deliveryAddress,
                totalAmount, status, driverId, (status == AssignmentStatus.ASSIGNED ? updatedAt : null),
                notes, createdAt, updatedAt);
    }

    public Assignment(String id, String orderId, String orderNumber, String customerId,
                      String recipientName, String recipientPhone, String deliveryAddress,
                      BigDecimal totalAmount, AssignmentStatus status, String driverId,
                      Instant assignedAt, String notes, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.customerId = customerId;
        this.recipientName = recipientName;
        this.recipientPhone = recipientPhone;
        this.deliveryAddress = deliveryAddress;
        this.totalAmount = totalAmount;
        this.status = status;
        this.driverId = driverId;
        this.assignedAt = assignedAt;
        this.notes = notes;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
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

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public void setAssignedAt(Instant assignedAt) {
        this.assignedAt = assignedAt;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Assignment that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Assignment{" +
                "id='" + id + '\'' +
                ", orderId='" + orderId + '\'' +
                ", orderNumber='" + orderNumber + '\'' +
                ", customerId='" + customerId + '\'' +
                ", status=" + status +
                ", driverId='" + driverId + '\'' +
                ", previousDriverId='" + previousDriverId + '\'' +
                ", assignedAt=" + assignedAt +
                ", assignmentSource='" + assignmentSource + '\'' +
                ", assignedBy='" + assignedBy + '\'' +
                ", reassignReason='" + reassignReason + '\'' +
                ", reassignedAt=" + reassignedAt +
                ", reassignedBy='" + reassignedBy + '\'' +
                ", unassignedReason='" + unassignedReason + '\'' +
                ", unassignedAt=" + unassignedAt +
                ", createdAt=" + createdAt +
                '}';
    }
}
