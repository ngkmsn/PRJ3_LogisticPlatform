package com.logistics.shipment.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Event payload contract for DRIVER_ASSIGNED domain event (PB-032).
 * Broadcast to Kafka topic logistics.shipment.events when a driver is assigned to an order.
 */
public class DriverAssignedPayload {

    private String assignmentId;
    private String orderId;
    private String orderNumber;
    private String customerId;
    private String driverId;
    private String status;
    private Instant assignedAt;
    private String deliveryAddress;
    private BigDecimal totalAmount;
    private String notes;

    public DriverAssignedPayload() {
    }

    public DriverAssignedPayload(String assignmentId, String orderId, String orderNumber,
                                String customerId, String driverId, String status,
                                Instant assignedAt, String deliveryAddress,
                                BigDecimal totalAmount, String notes) {
        this.assignmentId = assignmentId;
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.customerId = customerId;
        this.driverId = driverId;
        this.status = status;
        this.assignedAt = assignedAt;
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
        private String driverId;
        private String status = "ASSIGNED";
        private Instant assignedAt;
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

        public Builder driverId(String driverId) {
            this.driverId = driverId;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder assignedAt(Instant assignedAt) {
            this.assignedAt = assignedAt;
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

        public DriverAssignedPayload build() {
            return new DriverAssignedPayload(
                    assignmentId, orderId, orderNumber, customerId, driverId,
                    status, assignedAt, deliveryAddress, totalAmount, notes
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

    public String getDriverId() {
        return driverId;
    }

    public void setDriverId(String driverId) {
        this.driverId = driverId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public void setAssignedAt(Instant assignedAt) {
        this.assignedAt = assignedAt;
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
        return "DriverAssignedPayload{" +
                "assignmentId='" + assignmentId + '\'' +
                ", orderId='" + orderId + '\'' +
                ", driverId='" + driverId + '\'' +
                ", status='" + status + '\'' +
                ", assignedAt=" + assignedAt +
                '}';
    }
}
