package com.logistics.common.retry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryExecutorTest {

    private RetryMetrics metrics;
    private RetryExecutor executor;

    @BeforeEach
    void setUp() {
        metrics = new RetryMetrics();
        executor = new RetryExecutor(metrics);
    }

    @Test
    @DisplayName("PB-052: Operation succeeds on first attempt (0 retries)")
    void execute_succeedsOnFirstAttempt() throws Exception {
        RetryPolicy policy = RetryPolicy.defaultPolicy();
        AtomicInteger invocationCount = new AtomicInteger(0);

        String result = executor.execute("first-try-op", policy, () -> {
            invocationCount.incrementAndGet();
            return "SUCCESS_DATA";
        });

        assertThat(result).isEqualTo("SUCCESS_DATA");
        assertThat(invocationCount.get()).isEqualTo(1);
        assertThat(metrics.getTotalInvocations()).isEqualTo(1);
        assertThat(metrics.getSuccessfulFirstAttempt()).isEqualTo(1);
        assertThat(metrics.getSuccessfulAfterRetry()).isEqualTo(0);
        assertThat(metrics.getTotalRetriesPerformed()).isEqualTo(0);
    }

    @Test
    @DisplayName("PB-052: Transient error succeeds after 2 retries")
    void execute_succeedsAfterTransientRetries() throws Exception {
        RetryPolicy policy = RetryPolicy.builder()
                .maxRetries(3)
                .initialBackoff(Duration.ofMillis(10))
                .maxBackoff(Duration.ofMillis(100))
                .multiplier(1.5)
                .jitterFactor(0.0)
                .build();

        AtomicInteger invocationCount = new AtomicInteger(0);

        String result = executor.execute("transient-retry-op", policy, () -> {
            int attempt = invocationCount.incrementAndGet();
            if (attempt < 3) {
                throw new ConnectException("Simulated connection timeout on attempt " + attempt);
            }
            return "RECOVERED";
        });

        assertThat(result).isEqualTo("RECOVERED");
        assertThat(invocationCount.get()).isEqualTo(3);
        assertThat(metrics.getSuccessfulFirstAttempt()).isEqualTo(0);
        assertThat(metrics.getSuccessfulAfterRetry()).isEqualTo(1);
        assertThat(metrics.getTotalRetriesPerformed()).isEqualTo(2);
        assertThat(metrics.getRetriesForOperation("transient-retry-op")).isEqualTo(2);
    }

    @Test
    @DisplayName("PB-052: Operation exhausts retries and throws RetryExhaustedException")
    void execute_exhaustsRetries_throwsRetryExhaustedException() {
        RetryPolicy policy = RetryPolicy.builder()
                .maxRetries(2)
                .initialBackoff(Duration.ofMillis(5))
                .maxBackoff(Duration.ofMillis(20))
                .multiplier(1.5)
                .build();

        AtomicInteger invocationCount = new AtomicInteger(0);

        assertThatThrownBy(() -> executor.execute("exhaust-op", policy, () -> {
            invocationCount.incrementAndGet();
            throw new IOException("Persistent downstream network crash");
        }))
                .isInstanceOf(RetryExhaustedException.class)
                .hasMessageContaining("exhaust-op")
                .hasMessageContaining("exhausted all 3 retry attempts");

        // 1 initial attempt + 2 retries = 3 attempts total
        assertThat(invocationCount.get()).isEqualTo(3);
        assertThat(metrics.getFailedExhausted()).isEqualTo(1);
        assertThat(metrics.getTotalRetriesPerformed()).isEqualTo(2);
    }

    @Test
    @DisplayName("PB-052: Non-retryable error fails immediately on first attempt without any retry")
    void execute_nonRetryableError_failsImmediately() {
        RetryPolicy policy = RetryPolicy.builder()
                .maxRetries(5)
                .initialBackoff(Duration.ofMillis(5))
                .build();

        AtomicInteger invocationCount = new AtomicInteger(0);

        assertThatThrownBy(() -> executor.execute("invalid-input-op", policy, () -> {
            invocationCount.incrementAndGet();
            throw new IllegalArgumentException("Invalid order amount: -50");
        }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid order amount: -50");

        assertThat(invocationCount.get()).isEqualTo(1);
        assertThat(metrics.getFailedNonRetryable()).isEqualTo(1);
        assertThat(metrics.getTotalRetriesPerformed()).isEqualTo(0);
    }

    @Test
    @DisplayName("PB-052: Idempotent execution does not produce duplicate side effects across retries")
    void execute_idempotentBehaviorAcrossRetries() throws Exception {
        RetryPolicy policy = RetryPolicy.builder()
                .maxRetries(3)
                .initialBackoff(Duration.ofMillis(10))
                .build();

        AtomicInteger networkAttempts = new AtomicInteger(0);
        AtomicInteger databaseSideEffectCount = new AtomicInteger(0);

        // Simulated idempotent operation: database record written once under unique key, network response fails once
        String result = executor.execute("idempotent-op", policy, () -> {
            int attempt = networkAttempts.incrementAndGet();
            if (databaseSideEffectCount.get() == 0) {
                // First time only side effect
                databaseSideEffectCount.incrementAndGet();
            }

            if (attempt == 1) {
                throw new ConnectException("Transient network blip after database write");
            }
            return "ORDER_PROCESSED";
        });

        assertThat(result).isEqualTo("ORDER_PROCESSED");
        assertThat(networkAttempts.get()).isEqualTo(2);
        // Side effect happened only once despite 2 executions
        assertThat(databaseSideEffectCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("PB-052: Asynchronous non-blocking retry succeeds without blocking worker thread")
    void executeAsync_nonBlockingRetry_succeeds() throws Exception {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        try {
            RetryPolicy policy = RetryPolicy.builder()
                    .maxRetries(2)
                    .initialBackoff(Duration.ofMillis(10))
                    .maxBackoff(Duration.ofMillis(50))
                    .multiplier(1.5)
                    .build();

            AtomicInteger attempts = new AtomicInteger(0);

            CompletableFuture<String> future = executor.executeAsync("async-op", policy, scheduler, () -> {
                int count = attempts.incrementAndGet();
                if (count == 1) {
                    CompletableFuture<String> failed = new CompletableFuture<>();
                    failed.completeExceptionally(new ConnectException("Async network error"));
                    return failed;
                }
                return CompletableFuture.completedFuture("ASYNC_SUCCESS");
            });

            String result = future.get(3, TimeUnit.SECONDS);
            assertThat(result).isEqualTo("ASYNC_SUCCESS");
            assertThat(attempts.get()).isEqualTo(2);
            assertThat(metrics.getSuccessfulAfterRetry()).isEqualTo(1);

        } finally {
            scheduler.shutdownNow();
        }
    }
}
