package com.logistics.shipment.model;

/**
 * Enumeration of action types triggering an assignment history record (PB-037).
 */
public enum AssignmentActionType {
    /**
     * Automatic assignment performed by the system driver selection pipeline.
     */
    AUTO_ASSIGN,

    /**
     * Manual assignment initiated by Operations dispatchers.
     */
    MANUAL_ASSIGN,

    /**
     * Reassignment from current driver to a new driver initiated by Operations.
     */
    REASSIGN,

    /**
     * Pickup confirmation initiated by the assigned driver (PB-039).
     */
    PICKUP,

    /**
     * Transit initiation by the assigned driver (PB-040).
     */
    START_TRANSIT,

    /**
     * Delivery confirmation initiated by the assigned driver (PB-041).
     */
    DELIVER,

    /**
     * Delivery failure reported by the assigned driver (PB-042).
     */
    DELIVERY_FAILED
}


