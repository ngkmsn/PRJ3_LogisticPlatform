package com.logistics.order.dto;

import com.logistics.common.model.OrderStatus;
import com.logistics.order.entity.Order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class OrderCreatedEventPayload {

    private String orderId;
    private String orderNumber;
    private String customerId;
    private String recipientName;
    private String recipientPhone;
    private String deliveryAddress;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private String notes;
    private List<OrderItemDto> items;
    private Instant createdAt;

    public OrderCreatedEventPayload() {
    }

    public OrderCreatedEventPayload(String orderId, String orderNumber, String customerId,
                                   String recipientName, String recipientPhone, String deliveryAddress,
                                   OrderStatus status, BigDecimal totalAmount, String notes,
                                   List<OrderItemDto> items, Instant createdAt) {
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.customerId = customerId;
        this.recipientName = recipientName;
        this.recipientPhone = recipientPhone;
        this.deliveryAddress = deliveryAddress;
        this.status = status;
        this.totalAmount = totalAmount;
        this.notes = notes;
        this.items = items;
        this.createdAt = createdAt;
    }

    public static OrderCreatedEventPayload from(Order order) {
        if (order == null) {
            return null;
        }
        List<OrderItemDto> itemDtos = order.getItems() != null
                ? order.getItems().stream().map(OrderItemDto::from).collect(Collectors.toList())
                : Collections.emptyList();

        return new OrderCreatedEventPayload(
                order.getId(),
                order.getOrderNumber(),
                order.getCustomerId(),
                order.getRecipientName(),
                order.getRecipientPhone(),
                order.getDeliveryAddress(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getNotes(),
                itemDtos,
                order.getCreatedAt()
        );
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

    public List<OrderItemDto> getItems() {
        return items;
    }

    public void setItems(List<OrderItemDto> items) {
        this.items = items;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
