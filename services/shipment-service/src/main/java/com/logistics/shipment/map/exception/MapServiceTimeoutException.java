package com.logistics.shipment.map.exception;

/**
 * Thrown when map provider request times out.
 */
public class MapServiceTimeoutException extends MapException {

    public MapServiceTimeoutException(String message, String provider) {
        super(message, provider);
    }

    public MapServiceTimeoutException(String message, Throwable cause, String provider) {
        super(message, cause, provider);
    }
}
