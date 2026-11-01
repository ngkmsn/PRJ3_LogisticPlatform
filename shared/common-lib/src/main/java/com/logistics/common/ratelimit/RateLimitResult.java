package com.logistics.common.ratelimit;

import java.time.Instant;

/**
 * Result evaluation of a rate limit check containing decision, limits, and retry metadata.
 */
public class RateLimitResult {

    private final boolean allowed;
    private final long limit;
    private final long remaining;
    private final long resetEpochSeconds;
    private final long retryAfterSeconds;
    private final String policyName;
    private final String key;

    public RateLimitResult(boolean allowed, long limit, long remaining, long resetEpochSeconds,
                           long retryAfterSeconds, String policyName, String key) {
        this.allowed = allowed;
        this.limit = limit;
        this.remaining = Math.max(0, remaining);
        this.resetEpochSeconds = resetEpochSeconds;
        this.retryAfterSeconds = Math.max(0, retryAfterSeconds);
        this.policyName = policyName;
        this.key = key;
    }

    public static RateLimitResult allowed(long limit, long remaining, long windowSeconds, String policyName, String key) {
        long reset = Instant.now().getEpochSecond() + windowSeconds;
        return new RateLimitResult(true, limit, remaining, reset, 0, policyName, key);
    }

    public static RateLimitResult rejected(long limit, long remaining, long resetEpochSeconds,
                                           long retryAfterSeconds, String policyName, String key) {
        return new RateLimitResult(false, limit, remaining, resetEpochSeconds, retryAfterSeconds, policyName, key);
    }

    public boolean isAllowed() {
        return allowed;
    }

    public long getLimit() {
        return limit;
    }

    public long getRemaining() {
        return remaining;
    }

    public long getResetEpochSeconds() {
        return resetEpochSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public String getPolicyName() {
        return policyName;
    }

    public String getKey() {
        return key;
    }

    @Override
    public String toString() {
        return "RateLimitResult{" +
                "allowed=" + allowed +
                ", limit=" + limit +
                ", remaining=" + remaining +
                ", resetEpochSeconds=" + resetEpochSeconds +
                ", retryAfterSeconds=" + retryAfterSeconds +
                ", policyName='" + policyName + '\'' +
                ", key='" + key + '\'' +
                '}';
    }
}

