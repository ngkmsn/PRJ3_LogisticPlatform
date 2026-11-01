package com.logistics.common.retry;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe operational metrics registry tracking retry behavior across operations.
 * Allows monitoring teams to observe retry volumes, exhaustion rates, and transient failures.
 */
public class RetryMetrics {

    private static final RetryMetrics GLOBAL_INSTANCE = new RetryMetrics();

    private final AtomicLong totalInvocations = new AtomicLong(0);
    private final AtomicLong successfulFirstAttempt = new AtomicLong(0);
    private final AtomicLong successfulAfterRetry = new AtomicLong(0);
    private final AtomicLong failedExhausted = new AtomicLong(0);
    private final AtomicLong failedNonRetryable = new AtomicLong(0);
    private final AtomicLong totalRetriesPerformed = new AtomicLong(0);

    private final ConcurrentMap<String, AtomicLong> retriesByOperation = new ConcurrentHashMap<>();

    public static RetryMetrics global() {
        return GLOBAL_INSTANCE;
    }

    public void recordInvocation() {
        totalInvocations.incrementAndGet();
    }

    public void recordSuccess(int attempts) {
        if (attempts <= 1) {
            successfulFirstAttempt.incrementAndGet();
        } else {
            successfulAfterRetry.incrementAndGet();
        }
    }

    public void recordRetry(String operationName) {
        totalRetriesPerformed.incrementAndGet();
        if (operationName != null) {
            retriesByOperation.computeIfAbsent(operationName, k -> new AtomicLong(0)).incrementAndGet();
        }
    }

    public void recordExhausted() {
        failedExhausted.incrementAndGet();
    }

    public void recordNonRetryable() {
        failedNonRetryable.incrementAndGet();
    }

    public long getTotalInvocations() {
        return totalInvocations.get();
    }

    public long getSuccessfulFirstAttempt() {
        return successfulFirstAttempt.get();
    }

    public long getSuccessfulAfterRetry() {
        return successfulAfterRetry.get();
    }

    public long getFailedExhausted() {
        return failedExhausted.get();
    }

    public long getFailedNonRetryable() {
        return failedNonRetryable.get();
    }

    public long getTotalRetriesPerformed() {
        return totalRetriesPerformed.get();
    }

    public long getRetriesForOperation(String operationName) {
        AtomicLong count = retriesByOperation.get(operationName);
        return count != null ? count.get() : 0L;
    }

    public void reset() {
        totalInvocations.set(0);
        successfulFirstAttempt.set(0);
        successfulAfterRetry.set(0);
        failedExhausted.set(0);
        failedNonRetryable.set(0);
        totalRetriesPerformed.set(0);
        retriesByOperation.clear();
    }
}
