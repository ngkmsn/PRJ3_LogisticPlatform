package com.logistics.common.retry;

import java.time.Duration;
import java.time.Instant;

/**
 * Diagnostic context and telemetry tracking for an active retry execution.
 */
public class RetryContext {

    public enum Status {
        IN_PROGRESS,
        SUCCESS,
        RETRYING,
        EXHAUSTED,
        NON_RETRYABLE_ERROR
    }

    private final String operationName;
    private final int maxRetries;
    private final Instant startedAt;

    private int attempt = 0;
    private Duration totalDelay = Duration.ZERO;
    private Duration lastDelay = Duration.ZERO;
    private Throwable lastThrowable;
    private Status status = Status.IN_PROGRESS;

    public RetryContext(String operationName, int maxRetries) {
        this.operationName = operationName;
        this.maxRetries = maxRetries;
        this.startedAt = Instant.now();
    }

    public void incrementAttempt() {
        this.attempt++;
    }

    public void recordDelay(Duration delay) {
        if (delay != null) {
            this.lastDelay = delay;
            this.totalDelay = this.totalDelay.plus(delay);
        }
    }

    public void recordSuccess() {
        this.status = Status.SUCCESS;
    }

    public void recordExhausted(Throwable t) {
        this.status = Status.EXHAUSTED;
        this.lastThrowable = t;
    }

    public void recordNonRetryable(Throwable t) {
        this.status = Status.NON_RETRYABLE_ERROR;
        this.lastThrowable = t;
    }

    public void recordRetrying(Throwable t) {
        this.status = Status.RETRYING;
        this.lastThrowable = t;
    }

    public String getOperationName() {
        return operationName;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public int getAttempt() {
        return attempt;
    }

    public Duration getTotalDelay() {
        return totalDelay;
    }

    public Duration getLastDelay() {
        return lastDelay;
    }

    public Throwable getLastThrowable() {
        return lastThrowable;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Duration getElapsedTime() {
        return Duration.between(startedAt, Instant.now());
    }

    @Override
    public String toString() {
        return String.format("[op='%s', attempt=%d/%d, status=%s, totalDelay=%dms, elapsed=%dms, lastError='%s']",
                operationName, attempt, maxRetries, status,
                totalDelay.toMillis(), getElapsedTime().toMillis(),
                lastThrowable != null ? lastThrowable.getMessage() : "none");
    }
}
