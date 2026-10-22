package com.logistics.shipment.map.limiter;

import com.logistics.shipment.map.config.MapConfiguration;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Token bucket rate limiter to protect downstream map providers from request spikes
 * and adhere to vendor API rate limits.
 */
@ApplicationScoped
public class MapRateLimiter {

    private final MapConfiguration configuration;
    private final AtomicLong lastRefillTimestamp = new AtomicLong(System.currentTimeMillis());
    private final AtomicInteger availableTokens;

    @Inject
    public MapRateLimiter(MapConfiguration configuration) {
        this.configuration = configuration;
        this.availableTokens = new AtomicInteger(configuration.getRateLimitPermitsPerSecond());
    }

    /**
     * Attempts to acquire 1 permit. Returns true if acquired, false if rate limit exceeded.
     */
    public boolean tryAcquire() {
        refillTokens();
        while (true) {
            int current = availableTokens.get();
            if (current <= 0) {
                return false;
            }
            if (availableTokens.compareAndSet(current, current - 1)) {
                return true;
            }
        }
    }

    private void refillTokens() {
        long now = System.currentTimeMillis();
        long last = lastRefillTimestamp.get();
        if (now - last >= 1000) {
            if (lastRefillTimestamp.compareAndSet(last, now)) {
                int maxTokens = configuration.getRateLimitPermitsPerSecond();
                availableTokens.set(maxTokens);
            }
        }
    }
}
