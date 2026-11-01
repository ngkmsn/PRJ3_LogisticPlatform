package com.logistics.common.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unified Rate Limiter facade combining storage, policy evaluation, telemetry, and structured logging.
 */
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    private final RateLimiterStorage storage;
    private final RateLimitMetrics metrics;

    public RateLimiter(RateLimiterStorage storage) {
        this.storage = (storage != null) ? storage : new InMemoryRateLimiterStorage();
        this.metrics = new RateLimitMetrics();
    }

    public RateLimitResult checkAndConsume(String key, RateLimitPolicy policy) {
        if (key == null || key.isBlank()) {
            key = "anonymous";
        }
        if (policy == null) {
            policy = RateLimitPolicy.defaultPolicy(60, 60);
        }

        RateLimitResult result = storage.checkAndConsume(key, policy);
        metrics.recordDecision(result);

        if (!result.isAllowed()) {
            log.warn("PB-055 [RATE_LIMIT_EXCEEDED] client='{}', policy='{}', limit={}, retryAfterSeconds={}, resetEpoch={}",
                    result.getKey(), result.getPolicyName(), result.getLimit(),
                    result.getRetryAfterSeconds(), result.getResetEpochSeconds());
        } else {
            log.debug("PB-055 [RATE_LIMIT_ALLOWED] client='{}', policy='{}', remaining={}",
                    result.getKey(), result.getPolicyName(), result.getRemaining());
        }

        return result;
    }

    public void reset(String key) {
        storage.reset(key);
    }

    public void resetAll() {
        storage.resetAll();
        metrics.reset();
    }

    public RateLimitMetricsDto getMetrics() {
        return metrics.snapshot(storage);
    }

    public RateLimiterStorage getStorage() {
        return storage;
    }

    public RateLimitMetrics getMetricsCollector() {
        return metrics;
    }
}

