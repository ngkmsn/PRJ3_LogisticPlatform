package com.logistics.common.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventRateLimiterTest {

    @Test
    @DisplayName("tryAcquire consumes available permits and rejects when burst is exhausted")
    void tryAcquire_consumesPermitsUntilExhausted() {
        EventRateLimiter limiter = new EventRateLimiter(5.0, 3.0);

        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();
        // 4th call should fail because burst capacity is 3.0
        assertThat(limiter.tryAcquire()).isFalse();
    }

    @Test
    @DisplayName("tryAcquire with timeout waits and succeeds once tokens refill")
    void tryAcquireWithTimeout_waitsAndSucceeds() throws InterruptedException {
        // 10 permits/sec => 1 permit every 100ms
        EventRateLimiter limiter = new EventRateLimiter(10.0, 1.0);

        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();

        // Wait up to 500ms
        boolean acquired = limiter.tryAcquire(500, TimeUnit.MILLISECONDS);
        assertThat(acquired).isTrue();
    }

    @Test
    @DisplayName("acquireAsync completes asynchronously without blocking")
    void acquireAsync_completesSuccessfully() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        try {
            EventRateLimiter limiter = new EventRateLimiter(20.0, 1.0);

            assertThat(limiter.tryAcquire()).isTrue();
            // Now exhausted, call async
            CompletableFuture<Void> future = limiter.acquireAsync(executor);
            future.join(); // should complete within ~50ms
            assertThat(future.isDone()).isTrue();
        } finally {
            executor.shutdown();
        }
    }

    @Test
    @DisplayName("Invalid parameters throw IllegalArgumentException")
    void invalidParameters_throwException() {
        assertThatThrownBy(() -> new EventRateLimiter(-1.0, 5.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EventRateLimiter(10.0, 0.5))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

