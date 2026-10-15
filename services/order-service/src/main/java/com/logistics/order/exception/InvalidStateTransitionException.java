package com.logistics.order.exception;

import com.logistics.common.model.OrderStatus;

/**
 * Exception thrown when an attempt is made to transition an order from its current
 * status to an invalid or disallowed target status.
 */
public class InvalidStateTransitionException extends RuntimeException {

    private final OrderStatus fromStatus;
    private final OrderStatus toStatus;

    public InvalidStateTransitionException(OrderStatus fromStatus, OrderStatus toStatus) {
        super(String.format("Invalid order state transition from '%s' to '%s'.",
                fromStatus != null ? fromStatus.name() : "NULL",
                toStatus != null ? toStatus.name() : "NULL"));
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
    }

    public InvalidStateTransitionException(OrderStatus fromStatus, OrderStatus toStatus, String detail) {
        super(String.format("Invalid order state transition from '%s' to '%s': %s",
                fromStatus != null ? fromStatus.name() : "NULL",
                toStatus != null ? toStatus.name() : "NULL",
                detail));
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
    }

    public InvalidStateTransitionException(String message) {
        super(message);
        this.fromStatus = null;
        this.toStatus = null;
    }

    public OrderStatus getFromStatus() {
        return fromStatus;
    }

    public OrderStatus getToStatus() {
        return toStatus;
    }
}
