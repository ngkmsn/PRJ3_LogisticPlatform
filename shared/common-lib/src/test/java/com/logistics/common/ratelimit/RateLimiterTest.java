package com.logistics.common.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {

    private InMemoryRateLimiterStorage storage;
    private RateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        storage = new InMemoryRateLimiterStorage();
        rateLimiter = new RateLimiter(storage);
    }

    @Test
    @DisplayName("RateLimiter evaluates policies and records telemetry metrics")
    void checkAndConsume_recordsMetrics() {
        RateLimitPolicy authPolicy = RateLimitPolicy.authPolicy(2, 60);

        RateLimitResult r1 = rateLimiter.checkAndConsume("ip-1", authPolicy);
        RateLimitResult r2 = rateLimiter.checkAndConsume("ip-1", authPolicy);
        RateLimitResult r3 = rateLimiter.checkAndConsume("ip-1", authPolicy);

        assertThat(r1.isAllowed()).isTrue();
        assertThat(r2.isAllowed()).isTrue();
        assertThat(r3.isAllowed()).isFalse();

        RateLimitMetricsDto metrics = rateLimiter.getMetrics();
        assertThat(metrics.getTotalRequests()).isEqualTo(3);
        assertThat(metrics.getAllowedRequests()).isEqualTo(2);
        assertThat(metrics.getRejectedRequests()).isEqualTo(1);
        assertThat(metrics.getStorageType()).isEqualTo("IN_MEMORY");
        assertThat(metrics.isStorageHealthy()).isTrue();

        assertThat(metrics.getPolicies()).containsKey("auth");
        assertThat(metrics.getPolicies().get("auth").getAllowed()).isEqualTo(2);
        assertThat(metrics.getPolicies().get("auth").getRejected()).isEqualTo(1);
    }

    @Test
    @DisplayName("Parallel requests are throttled atomically without race conditions")
    void concurrentRequests_areThrottledAtomically() throws InterruptedException {
        int threads = 20;
        long limit = 5;
        RateLimitPolicy policy = RateLimitPolicy.ordersPolicy(limit, 60);

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        AtomicInteger allowedCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    RateLimitResult result = rateLimiter.checkAndConsume("same-user", policy);
                    if (result.isAllowed()) {
                        allowedCount.incrementAndGet();
                    } else {
                        rejectedCount.incrementAndGet();
                    }
                } catch (InterruptedException ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        assertThat(allowedCount.get()).isEqualTo((int) limit);
        assertThat(rejectedCount.get()).isEqualTo(threads - (int) limit);
        assertThat(rateLimiter.getMetrics().getAllowedRequests()).isEqualTo(limit);
        assertThat(rateLimiter.getMetrics().getRejectedRequests()).isEqualTo(threads - limit);
    }

    @Test
    @DisplayName("Reset all resets both storage and metrics")
    void resetAll_clearsBothStorageAndMetrics() {
        RateLimitPolicy policy = RateLimitPolicy.defaultPolicy(1, 60);
        rateLimiter.checkAndConsume("client-x", policy);
        rateLimiter.checkAndConsume("client-x", policy);

        assertThat(rateLimiter.getMetrics().getTotalRequests()).isEqualTo(2);

        rateLimiter.resetAll();

        assertThat(rateLimiter.getMetrics().getTotalRequests()).isEqualTo(0);
        assertThat(rateLimiter.getMetrics().getAllowedRequests()).isEqualTo(0);
        assertThat(rateLimiter.getMetrics().getRejectedRequests()).isEqualTo(0);
        assertThat(rateLimiter.checkAndConsume("client-x", policy).isAllowed()).isTrue();
    }
}

