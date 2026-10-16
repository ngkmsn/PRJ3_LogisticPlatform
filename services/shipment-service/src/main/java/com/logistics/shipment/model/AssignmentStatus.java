package com.logistics.shipment.model;

/**
 * Status of an assignment within the shipment and dispatching domain.
 */
public enum AssignmentStatus {
    PENDING,
    SEARCHING_DRIVER,
    ASSIGNED,
    CANCELLED,
    FAILED
}
