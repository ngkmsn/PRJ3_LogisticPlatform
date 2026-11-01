package com.logistics.shipment.map.exception;

import com.logistics.common.retry.RetryAfterProvider;

import java.time.Duration;

/**
 * Thrown when request rate limit to map provider is exceeded.
 */
public class MapRateLimitException extends MapException implements RetryAfterProvider {

    private final Duration retryAfter;

    public MapRateLimitException(String message, String provider) {
        this(message, provider, null);
    }

    public MapRateLimitException(String message, String provider, Duration retryAfter) {
        super(message, provider);
        this.retryAfter = retryAfter;
    }

    @Override
    public Duration getRetryAfter() {
        return retryAfter;
    }
}

