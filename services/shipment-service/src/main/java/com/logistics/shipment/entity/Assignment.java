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
                ", createdAt=" + createdAt +
                '}';
    }
}
