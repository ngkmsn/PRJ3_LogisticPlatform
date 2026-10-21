package com.logistics.shipment.exception;

/**
 * Thrown when the authoritative driver data provider service (e.g. User Service) is unreachable or fails.
 */
public class DriverDataProviderUnavailableException extends RuntimeException {

    public DriverDataProviderUnavailableException(String message) {
        super(message);
    }

    public DriverDataProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
