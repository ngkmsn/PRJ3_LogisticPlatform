package com.logistics.common.ratelimit;

/**
 * Failure handling strategy when the rate limit state store (e.g. Redis) is unavailable or encounters errors.
 */
public enum FailPolicy {
    /**
     * Fail open: Allow requests to proceed when rate limit storage fails, prioritizing system availability.
     */
    FAIL_OPEN,

    /**
     * Fail closed: Reject requests when rate limit storage fails, prioritizing system protection and security.
     */
    FAIL_CLOSED
}

