package com.logistics.common.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RedisRateLimiterStorageTest {

    @Test
    @DisplayName("When Redis is unavailable and FailPolicy is FAIL_OPEN without fallback, request is permitted")
    void redisUnavailable_failOpen_allowsRequest() {
        // Connect to a non-existent port where no Redis is running
        RedisClient offlineClient = new RedisClient("localhost", 59999, 100);
        RedisRateLimiterStorage storage = new RedisRateLimiterStorage(offlineClient, FailPolicy.FAIL_OPEN, null);

        RateLimitPolicy policy = RateLimitPolicy.authPolicy(5, 60);
        RateLimitResult result = storage.checkAndConsume("test-client", policy);

        assertThat(result.isAllowed()).isTrue();
        assertThat(storage.getErrorCount()).isGreaterThan(0);
        assertThat(storage.isHealthy()).isFalse();
    }

    @Test
    @DisplayName("When Redis is unavailable and FailPolicy is FAIL_CLOSED, request is rejected")
    void redisUnavailable_failClosed_rejectsRequest() {
        RedisClient offlineClient = new RedisClient("localhost", 59999, 100);
        RedisRateLimiterStorage storage = new RedisRateLimiterStorage(offlineClient, FailPolicy.FAIL_CLOSED, null);

        RateLimitPolicy policy = RateLimitPolicy.ordersPolicy(5, 60);
        RateLimitResult result = storage.checkAndConsume("test-client", policy);

        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getRetryAfterSeconds()).isGreaterThan(0);
        assertThat(storage.getErrorCount()).isGreaterThan(0);
    }

    @Test
    @DisplayName("When Redis is unavailable but in-memory fallback is present, fallback provides rate limiting")
    void redisUnavailable_fallsBackToInMemory() {
        RedisClient offlineClient = new RedisClient("localhost", 59999, 100);
        InMemoryRateLimiterStorage fallback = new InMemoryRateLimiterStorage();
        RedisRateLimiterStorage storage = new RedisRateLimiterStorage(offlineClient, FailPolicy.FAIL_OPEN, fallback);

        RateLimitPolicy policy = RateLimitPolicy.authPolicy(2, 60);

        RateLimitResult r1 = storage.checkAndConsume("ip-1", policy);
        RateLimitResult r2 = storage.checkAndConsume("ip-1", policy);
        RateLimitResult r3 = storage.checkAndConsume("ip-1", policy);

        // Fallback in-memory should enforce the limit of 2!
        assertThat(r1.isAllowed()).isTrue();
        assertThat(r2.isAllowed()).isTrue();
        assertThat(r3.isAllowed()).isFalse();
        assertThat(storage.getErrorCount()).isEqualTo(3);
    }
}

