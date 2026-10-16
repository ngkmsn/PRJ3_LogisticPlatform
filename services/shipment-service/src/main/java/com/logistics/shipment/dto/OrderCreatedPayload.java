package com.logistics.shipment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.logistics.common.model.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderCreatedPayload {

    private String orderId;
    private String orderNumber;
    private String customerId;
    private String recipientName;
    private String recipientPhone;
    private String deliveryAddress;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private String notes;
    private Instant createdAt;

    public OrderCreatedPayload() {
    }

    public OrderCreatedPayload(String orderId, String orderNumber, String customerId,
                               String recipientName, String recipientPhone, String deliveryAddress,
                               OrderStatus status, BigDecimal totalAmount, String notes,
                               Instant createdAt) {
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.customerId = customerId;
        this.recipientName = recipientName;
        this.recipientPhone = recipientPhone;
        this.deliveryAddress = deliveryAddress;
        this.status = status;
        this.totalAmount = totalAmount;
        this.notes = notes;
        this.createdAt = createdAt;
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

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
