package com.logistics.shipment.exception;

/**
 * Thrown when an active assignment already exists for an order,
 * preventing duplicate active assignments and maintaining data integrity.
 */
public class DuplicateAssignmentException extends RuntimeException {

    private final String orderId;
    private final String existingAssignmentId;

    public DuplicateAssignmentException(String message) {
        super(message);
        this.orderId = null;
        this.existingAssignmentId = null;
    }

    public DuplicateAssignmentException(String orderId, String existingAssignmentId, String message) {
        super(message);
        this.orderId = orderId;
        this.existingAssignmentId = existingAssignmentId;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getExistingAssignmentId() {
        return existingAssignmentId;
    }
}
