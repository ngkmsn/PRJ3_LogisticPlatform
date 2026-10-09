package com.logistics.common.model;

/**
 * Standard lifecycle statuses for logistics orders.
 */
public enum OrderStatus {

    /**
     * Order newly created by customer and waiting for acceptance or payment confirmation.
     */
    PENDING,

    /**
     * Order confirmed and accepted for fulfillment.
     */
    CONFIRMED,

    /**
     * Order assigned to a driver or carrier for pickup.
     */
    ASSIGNED,

    /**
     * Order picked up and currently in transit to destination.
     */
    IN_TRANSIT,

    /**
     * Order successfully delivered to recipient.
     */
    DELIVERED,

    /**
     * Order cancelled before or during fulfillment.
     */
    CANCELLED;

    public static OrderStatus fromString(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return OrderStatus.valueOf(status.trim().toUpperCase());
    }
}
