package com.logistics.shipment.map.exception;

/**
 * Thrown when map provider is unreachable or returns 5xx error.
 */
public class MapServiceUnavailableException extends MapException {

    public MapServiceUnavailableException(String message, String provider) {
        super(message, provider);
    }

    public MapServiceUnavailableException(String message, Throwable cause, String provider) {
        super(message, cause, provider);
    }
}
