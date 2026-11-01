package com.logistics.common.ratelimit;

/**
 * Storage contract for persisting and evaluating rate limit state.
 * Implementations may be local in-memory or distributed across replicas (e.g. Redis).
 */
public interface RateLimiterStorage {

    /**
     * Atomically checks if the given key is allowed under the policy and consumes 1 permit if allowed.
     *
     * @param key the unique identifier (e.g. IP or User ID)
     * @param policy the rate limit policy to evaluate
     * @return evaluation result with allowed flag, remaining permits, and retry headers
     */
    RateLimitResult checkAndConsume(String key, RateLimitPolicy policy);

    /**
     * Resets rate limit counters for a specific key.
     *
     * @param key the key to reset
     */
    void reset(String key);

    /**
     * Clears all tracked rate limit keys.
     */
    void resetAll();

    /**
     * Checks if the storage backend is healthy and responding.
     *
     * @return true if healthy, false otherwise
     */
    boolean isHealthy();

    /**
     * Returns the storage identifier (e.g. "IN_MEMORY", "REDIS").
     */
    String getStorageType();
}

