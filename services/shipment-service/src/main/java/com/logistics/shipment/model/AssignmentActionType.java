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
    REASSIGN
}
