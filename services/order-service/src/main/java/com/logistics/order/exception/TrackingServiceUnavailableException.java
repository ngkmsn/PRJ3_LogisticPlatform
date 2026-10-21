package com.logistics.order.exception;

/**
 * Exception thrown when the Tracking Service (shipment-service) is unreachable or unavailable.
 */
public class TrackingServiceUnavailableException extends RuntimeException {

    public TrackingServiceUnavailableException(String message) {
        super(message);
    }

    public TrackingServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
