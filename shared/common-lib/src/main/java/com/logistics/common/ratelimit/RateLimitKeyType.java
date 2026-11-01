package com.logistics.common.ratelimit;

/**
 * Defines the identity scope used to partition and isolate rate limit counters.
 */
public enum RateLimitKeyType {
    /**
     * Prefer authenticated User ID if present (from JWT or header); fallback to Client IP.
     */
    IP_AND_USER,

    /**
     * Always limit strictly by Client IP regardless of authentication status.
     */
    IP_ONLY,

    /**
     * Limit only by User ID. Unauthenticated requests are rejected or handled by default policy.
     */
    USER_ONLY,

    /**
     * Limit by API Key or client service identifier.
     */
    CLIENT_ID
}

