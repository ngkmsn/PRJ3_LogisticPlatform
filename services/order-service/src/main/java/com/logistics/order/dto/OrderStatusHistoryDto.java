package com.logistics.order.dto;

import com.logistics.common.model.OrderStatus;
import com.logistics.order.entity.OrderStatusHistory;

import java.time.Instant;

public class OrderStatusHistoryDto {

    private String id;
    private String orderId;
    private String orderNumber;
    private OrderStatus previousStatus;
    private OrderStatus newStatus;
    private String changedBy;
    private String changedByRole;
    private String reason;
    private Instant createdAt;

    public OrderStatusHistoryDto() {
    }

    public OrderStatusHistoryDto(String id, String orderId, String orderNumber,
                                 OrderStatus previousStatus, OrderStatus newStatus,
                                 String changedBy, String changedByRole, String reason,
                                 Instant createdAt) {
        this.id = id;
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.changedBy = changedBy;
        this.changedByRole = changedByRole;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public static OrderStatusHistoryDto from(OrderStatusHistory entity) {
        if (entity == null) {
            return null;
        }
        return new OrderStatusHistoryDto(
                entity.getId(),
                entity.getOrder() != null ? entity.getOrder().getId() : null,
                entity.getOrderNumber(),
                entity.getPreviousStatus(),
                entity.getNewStatus(),
                entity.getChangedBy(),
                entity.getChangedByRole(),
                entity.getReason(),
                entity.getCreatedAt()
        );
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

    public OrderStatus getPreviousStatus() {
        return previousStatus;
    }

    public void setPreviousStatus(OrderStatus previousStatus) {
        this.previousStatus = previousStatus;
    }

    public OrderStatus getNewStatus() {
        return newStatus;
    }

    public void setNewStatus(OrderStatus newStatus) {
        this.newStatus = newStatus;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(String changedBy) {
        this.changedBy = changedBy;
    }

    public String getChangedByRole() {
        return changedByRole;
    }

    public void setChangedByRole(String changedByRole) {
        this.changedByRole = changedByRole;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
