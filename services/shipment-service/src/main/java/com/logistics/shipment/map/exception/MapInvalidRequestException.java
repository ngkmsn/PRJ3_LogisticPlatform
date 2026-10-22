package com.logistics.shipment.map.exception;

/**
 * Thrown when map request input is invalid (e.g., coordinates out of range, blank address).
 */
public class MapInvalidRequestException extends MapException {

    public MapInvalidRequestException(String message) {
        super(message, "VALIDATION");
    }

    public MapInvalidRequestException(String message, String provider) {
        super(message, provider);
    }
}
