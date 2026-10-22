package com.logistics.order.dto;

import com.logistics.common.model.OrderStatus;
import com.logistics.order.entity.Order;

import java.time.Instant;

/**
 * Event payload contract for ORDER_CANCELLED domain event.
 * Published via Transactional Outbox to notify customer of cancellation.
 */
public class OrderCancelledEventPayload {

    private String orderId;
    private String orderNumber;
    private String customerId;
    private String recipientName;
    private String recipientPhone;
    private String deliveryAddress;
    private OrderStatus status;
    private String cancelReason;
    private Instant cancelledAt;

    public OrderCancelledEventPayload() {
    }

    public OrderCancelledEventPayload(String orderId, String orderNumber, String customerId,
                                      String recipientName, String recipientPhone, String deliveryAddress,
                                      OrderStatus status, String cancelReason, Instant cancelledAt) {
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.customerId = customerId;
        this.recipientName = recipientName;
        this.recipientPhone = recipientPhone;
        this.deliveryAddress = deliveryAddress;
        this.status = status;
        this.cancelReason = cancelReason;
        this.cancelledAt = cancelledAt;
    }

    public static OrderCancelledEventPayload from(Order order, String cancelReason) {
        if (order == null) {
            return null;
        }
        return new OrderCancelledEventPayload(
                order.getId(),
                order.getOrderNumber(),
                order.getCustomerId(),
                order.getRecipientName(),
                order.getRecipientPhone(),
                order.getDeliveryAddress(),
                OrderStatus.CANCELLED,
                cancelReason,
                order.getUpdatedAt() != null ? order.getUpdatedAt() : Instant.now()
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

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(Instant cancelledAt) {
        this.cancelledAt = cancelledAt;
    }
}
