package com.logistics.common.retry;

import com.logistics.common.exception.LogisticsPlatformException;

import java.time.Duration;

/**
 * Exception thrown when an operation has failed after exhausting all configured retry attempts.
 * Carries structured diagnostics including operation name, attempts executed, total duration,
 * and the root cause exception for audit, telemetry, and DLQ routing (PB-053).
 */
public class RetryExhaustedException extends LogisticsPlatformException {

    public static final String ERROR_CODE = "RETRY_EXHAUSTED";

    private final String operationName;
    private final int attempts;
    private final Duration totalDuration;

    public RetryExhaustedException(String operationName, int attempts, Throwable cause) {
        this(operationName, attempts, Duration.ZERO, cause);
    }

    public RetryExhaustedException(String operationName, int attempts, Duration totalDuration, Throwable cause) {
        super(ERROR_CODE, String.format("Operation '%s' exhausted all %d retry attempts (total time %dms). Last error: %s",
                operationName, attempts, totalDuration != null ? totalDuration.toMillis() : 0,
                cause != null ? cause.getMessage() : "Unknown"), cause);
        this.operationName = operationName;
        this.attempts = attempts;
        this.totalDuration = totalDuration != null ? totalDuration : Duration.ZERO;
    }

    public String getOperationName() {
        return operationName;
    }

    public int getAttempts() {
        return attempts;
    }

    public Duration getTotalDuration() {
        return totalDuration;
    }
}
