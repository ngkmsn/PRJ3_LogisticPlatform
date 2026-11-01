package com.logistics.common.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe In-Memory Rate Limiter Storage using an exact Sliding Window algorithm.
 * Guarantees zero boundary bursts, precise token expiry, and high concurrency without external dependencies.
 */
public class InMemoryRateLimiterStorage implements RateLimiterStorage {

    private static final Logger log = LoggerFactory.getLogger(InMemoryRateLimiterStorage.class);

    private final Map<String, KeyState> keyStates = new ConcurrentHashMap<>();

    private static class KeyState {
        final Deque<Long> timestamps = new ArrayDeque<>();
    }

    @Override
    public RateLimitResult checkAndConsume(String key, RateLimitPolicy policy) {
        if (key == null || key.isBlank() || policy == null) {
            throw new IllegalArgumentException("Key and policy must not be null or blank");
        }

        long nowMs = System.currentTimeMillis();
        long windowMs = policy.getWindowSeconds() * 1000L;
        long cutoff = nowMs - windowMs;
        long limit = policy.getLimit();

        KeyState state = keyStates.computeIfAbsent(key, k -> new KeyState());

        synchronized (state) {
            // Evict expired timestamps outside the sliding window
            while (!state.timestamps.isEmpty() && state.timestamps.peekFirst() <= cutoff) {
                state.timestamps.pollFirst();
            }

            int currentCount = state.timestamps.size();
            if (currentCount < limit) {
                state.timestamps.addLast(nowMs);
                long remaining = limit - (currentCount + 1);
                long resetEpoch = (nowMs + windowMs) / 1000L;
                return new RateLimitResult(true, limit, remaining, resetEpoch, 0, policy.getName(), key);
            } else {
                long oldest = state.timestamps.peekFirst();
                long retryAfterMs = Math.max(0, (oldest + windowMs) - nowMs);
                long retryAfterSeconds = Math.max(1, (long) Math.ceil(retryAfterMs / 1000.0));
                long resetEpoch = (nowMs + retryAfterMs) / 1000L;

                return RateLimitResult.rejected(limit, 0, resetEpoch, retryAfterSeconds, policy.getName(), key);
            }
        }
    }

    @Override
    public void reset(String key) {
        if (key != null) {
            keyStates.remove(key);
            log.debug("Reset rate limit key: {}", key);
        }
    }

    @Override
    public void resetAll() {
        keyStates.clear();
        log.info("Reset all in-memory rate limit counters");
    }

    @Override
    public boolean isHealthy() {
        return true;
    }

    @Override
    public String getStorageType() {
        return "IN_MEMORY";
    }

    public int getTrackedKeyCount() {
        return keyStates.size();
    }
}

