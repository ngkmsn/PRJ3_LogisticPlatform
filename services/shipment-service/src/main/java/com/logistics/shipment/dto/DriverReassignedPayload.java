package com.logistics.shipment.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Event payload contract for DRIVER_REASSIGNED domain event (PB-036).
 * Broadcast to Kafka topic logistics.shipment.events when an order is reassigned to a new driver.
 */
public class DriverReassignedPayload {

    private String assignmentId;
    private String orderId;
    private String orderNumber;
    private String customerId;
    private String previousDriverId;
    private String newDriverId;
    private String status;
    private Instant reassignedAt;
    private String reassignReason;
    private String reassignedBy;
    private String deliveryAddress;
    private BigDecimal totalAmount;
    private String notes;

    public DriverReassignedPayload() {
    }

    public DriverReassignedPayload(String assignmentId, String orderId, String orderNumber,
                                   String customerId, String previousDriverId, String newDriverId,
                                   String status, Instant reassignedAt, String reassignReason,
                                   String reassignedBy, String deliveryAddress,
                                   BigDecimal totalAmount, String notes) {
        this.assignmentId = assignmentId;
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.customerId = customerId;
        this.previousDriverId = previousDriverId;
        this.newDriverId = newDriverId;
        this.status = status;
        this.reassignedAt = reassignedAt;
        this.reassignReason = reassignReason;
        this.reassignedBy = reassignedBy;
        this.deliveryAddress = deliveryAddress;
        this.totalAmount = totalAmount;
        this.notes = notes;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String assignmentId;
        private String orderId;
        private String orderNumber;
        private String customerId;
        private String previousDriverId;
        private String newDriverId;
        private String status = "ASSIGNED";
        private Instant reassignedAt;
        private String reassignReason;
        private String reassignedBy;
        private String deliveryAddress;
        private BigDecimal totalAmount;
        private String notes;

        public Builder assignmentId(String assignmentId) {
            this.assignmentId = assignmentId;
            return this;
        }

        public Builder orderId(String orderId) {
            this.orderId = orderId;
            return this;
        }

        public Builder orderNumber(String orderNumber) {
            this.orderNumber = orderNumber;
            return this;
        }

        public Builder customerId(String customerId) {
            this.customerId = customerId;
            return this;
        }

        public Builder previousDriverId(String previousDriverId) {
            this.previousDriverId = previousDriverId;
            return this;
        }

        public Builder newDriverId(String newDriverId) {
            this.newDriverId = newDriverId;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder reassignedAt(Instant reassignedAt) {
            this.reassignedAt = reassignedAt;
            return this;
        }

        public Builder reassignReason(String reassignReason) {
            this.reassignReason = reassignReason;
            return this;
        }

        public Builder reassignedBy(String reassignedBy) {
            this.reassignedBy = reassignedBy;
            return this;
        }

        public Builder deliveryAddress(String deliveryAddress) {
            this.deliveryAddress = deliveryAddress;
            return this;
        }

        public Builder totalAmount(BigDecimal totalAmount) {
            this.totalAmount = totalAmount;
            return this;
        }

        public Builder notes(String notes) {
            this.notes = notes;
            return this;
        }

        public DriverReassignedPayload build() {
            return new DriverReassignedPayload(
                    assignmentId, orderId, orderNumber, customerId, previousDriverId,
                    newDriverId, status, reassignedAt, reassignReason, reassignedBy,
                    deliveryAddress, totalAmount, notes
            );
        }
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getReassignedAt() {
        return reassignedAt;
    }

    public void setReassignedAt(Instant reassignedAt) {
        this.reassignedAt = reassignedAt;
    }

    public String getReassignReason() {
        return reassignReason;
    }

    public void setReassignReason(String reassignReason) {
        this.reassignReason = reassignReason;
    }

    public String getReassignedBy() {
        return reassignedBy;
    }

    public void setReassignedBy(String reassignedBy) {
        this.reassignedBy = reassignedBy;
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

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    @Override
    public String toString() {
        return "DriverReassignedPayload{" +
                "assignmentId='" + assignmentId + '\'' +
                ", orderId='" + orderId + '\'' +
                ", previousDriverId='" + previousDriverId + '\'' +
                ", newDriverId='" + newDriverId + '\'' +
                ", status='" + status + '\'' +
                ", reassignedAt=" + reassignedAt +
                ", reassignReason='" + reassignReason + '\'' +
                '}';
    }
}
