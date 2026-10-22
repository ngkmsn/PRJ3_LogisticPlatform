package com.logistics.shipment.map.exception;

/**
 * Base exception for all Map Service related failures.
 */
public class MapException extends RuntimeException {

    private final String provider;

    public MapException(String message) {
        super(message);
        this.provider = "UNKNOWN";
    }

    public MapException(String message, String provider) {
        super(message);
        this.provider = provider != null ? provider : "UNKNOWN";
    }

    public MapException(String message, Throwable cause, String provider) {
        super(message, cause);
        this.provider = provider != null ? provider : "UNKNOWN";
    }

    public String getProvider() {
        return provider;
    }
}
