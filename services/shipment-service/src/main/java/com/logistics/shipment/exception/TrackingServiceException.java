package com.logistics.shipment.exception;

public class TrackingServiceException extends RuntimeException {

    public TrackingServiceException(String message) {
        super(message);
    }

    public TrackingServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
