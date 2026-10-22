package com.logistics.shipment.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Event payload contract for ORDER_DELIVERED domain event (PB-041).
 * Broadcast to Kafka topic logistics.shipment.events when a driver confirms order delivery.
 */
public class OrderDeliveredPayload {

    private String assignmentId;
    private String orderId;
    private String orderNumber;
    private String customerId;
    private String driverId;
    private String status;
    private Instant deliveredAt;
    private String deliveryNotes;
    private String proofOfDelivery;
    private String recipientSignature;
    private String recipientName;
    private String recipientPhone;
    private String deliveryAddress;
    private BigDecimal totalAmount;
    private String notes;

    public OrderDeliveredPayload() {
    }

    public OrderDeliveredPayload(String assignmentId, String orderId, String orderNumber,
                                 String customerId, String driverId, String status,
                                 Instant deliveredAt, String deliveryNotes, String proofOfDelivery,
                                 String recipientSignature, String deliveryAddress,
                                 BigDecimal totalAmount, String notes) {
        this(assignmentId, orderId, orderNumber, customerId, null, null, driverId, status,
             deliveredAt, deliveryNotes, proofOfDelivery, recipientSignature, deliveryAddress,
             totalAmount, notes);
    }

    public OrderDeliveredPayload(String assignmentId, String orderId, String orderNumber,
                                 String customerId, String recipientName, String recipientPhone,
                                 String driverId, String status, Instant deliveredAt,
                                 String deliveryNotes, String proofOfDelivery,
                                 String recipientSignature, String deliveryAddress,
                                 BigDecimal totalAmount, String notes) {
        this.assignmentId = assignmentId;
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.customerId = customerId;
        this.recipientName = recipientName;
        this.recipientPhone = recipientPhone;
        this.driverId = driverId;
        this.status = status;
        this.deliveredAt = deliveredAt;
        this.deliveryNotes = deliveryNotes;
        this.proofOfDelivery = proofOfDelivery;
        this.recipientSignature = recipientSignature;
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
        private String status = "DELIVERED";
        private Instant deliveredAt;
        private String deliveryNotes;
        private String proofOfDelivery;
        private String recipientSignature;
        private String recipientName;
        private String recipientPhone;
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

        public Builder recipientName(String recipientName) {
            this.recipientName = recipientName;
            return this;
        }

        public Builder recipientPhone(String recipientPhone) {
            this.recipientPhone = recipientPhone;
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

        public Builder deliveredAt(Instant deliveredAt) {
            this.deliveredAt = deliveredAt;
            return this;
        }

        public Builder deliveryNotes(String deliveryNotes) {
            this.deliveryNotes = deliveryNotes;
            return this;
        }

        public Builder proofOfDelivery(String proofOfDelivery) {
            this.proofOfDelivery = proofOfDelivery;
            return this;
        }

        public Builder recipientSignature(String recipientSignature) {
            this.recipientSignature = recipientSignature;
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

        public OrderDeliveredPayload build() {
            return new OrderDeliveredPayload(
                    assignmentId, orderId, orderNumber, customerId, recipientName, recipientPhone,
                    driverId, status, deliveredAt, deliveryNotes, proofOfDelivery, recipientSignature,
                    deliveryAddress, totalAmount, notes
            );
        }
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

    public String getRecipientSignature() {
        return recipientSignature;
    }

    public void setRecipientSignature(String recipientSignature) {
        this.recipientSignature = recipientSignature;
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
}
