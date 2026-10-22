package com.logistics.shipment.map.exception;

/**
 * Thrown when request rate limit to map provider is exceeded.
 */
public class MapRateLimitException extends MapException {

    public MapRateLimitException(String message, String provider) {
        super(message, provider);
    }
}
