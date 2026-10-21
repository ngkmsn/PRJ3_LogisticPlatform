package com.logistics.shipment.exception;

/**
 * Thrown when a requested driver is not found.
 */
public class DriverNotFoundException extends RuntimeException {

    public DriverNotFoundException(String message) {
        super(message);
    }
}
