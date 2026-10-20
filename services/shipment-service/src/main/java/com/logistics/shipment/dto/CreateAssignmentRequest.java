package com.logistics.shipment.dto;

import java.math.BigDecimal;

/**
 * Data Transfer Object encapsulating parameters required to create
 * and persist a new Assignment record linked with an eligible driver (PB-031).
 */
public class CreateAssignmentRequest {

    private String orderId;
    private String orderNumber;
    private String customerId;
    private String recipientName;
    private String recipientPhone;
    private String deliveryAddress;
    private BigDecimal totalAmount;
    private String driverId;
    private String notes;

    public CreateAssignmentRequest() {
    }

    public CreateAssignmentRequest(String orderId, String orderNumber, String customerId,
                                   String recipientName, String recipientPhone, String deliveryAddress,
                                   BigDecimal totalAmount, String driverId, String notes) {
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.customerId = customerId;
        this.recipientName = recipientName;
        this.recipientPhone = recipientPhone;
        this.deliveryAddress = deliveryAddress;
        this.totalAmount = totalAmount;
        this.driverId = driverId;
        this.notes = notes;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String orderId;
        private String orderNumber;
        private String customerId;
        private String recipientName;
        private String recipientPhone;
        private String deliveryAddress;
        private BigDecimal totalAmount;
        private String driverId;
        private String notes;

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

        public Builder deliveryAddress(String deliveryAddress) {
            this.deliveryAddress = deliveryAddress;
            return this;
        }

        public Builder totalAmount(BigDecimal totalAmount) {
            this.totalAmount = totalAmount;
            return this;
        }

        public Builder driverId(String driverId) {
            this.driverId = driverId;
            return this;
        }

        public Builder notes(String notes) {
            this.notes = notes;
            return this;
        }

        public CreateAssignmentRequest build() {
            return new CreateAssignmentRequest(
                    orderId, orderNumber, customerId, recipientName, recipientPhone,
                    deliveryAddress, totalAmount, driverId, notes
            );
        }
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

    @Override
    public String toString() {
        return "CreateAssignmentRequest{" +
                "orderId='" + orderId + '\'' +
                ", orderNumber='" + orderNumber + '\'' +
                ", customerId='" + customerId + '\'' +
                ", driverId='" + driverId + '\'' +
                '}';
    }
}
