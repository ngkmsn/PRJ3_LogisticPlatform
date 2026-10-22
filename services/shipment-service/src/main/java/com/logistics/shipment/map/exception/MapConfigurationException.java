package com.logistics.shipment.map.exception;

/**
 * Thrown when map provider configuration is missing or invalid (e.g., missing API key or endpoint).
 */
public class MapConfigurationException extends MapException {

    public MapConfigurationException(String message) {
        super(message, "CONFIG");
    }

    public MapConfigurationException(String message, String provider) {
        super(message, provider);
    }
}
