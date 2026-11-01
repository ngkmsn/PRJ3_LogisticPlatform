package com.logistics.common.retry;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;

/**
 * Standard error classifier for determining whether an operation failure or downstream
 * HTTP response is transient (eligible for retry) or permanent (non-retryable).
 * <p>
 * Ensures business validation errors and client request defects are never retried infinitely,
 * preventing cascading failures and resource exhaustion.
 */
public final class ErrorClassifier {

    private ErrorClassifier() {
    }

    /**
     * Determines whether the given Throwable represents a transient, retryable failure.
     * Recursively inspects the cause chain.
     *
     * @param throwable the thrown error
     * @return true if transient and retryable; false if permanent or null
     */
    public static boolean isRetryable(Throwable throwable) {
        if (throwable == null) {
            return false;
        }

        Throwable current = throwable;
        while (current != null) {
            // 1. Explicit Non-Retryable Types (Client/Business bugs)
            if (isExplicitNonRetryable(current)) {
                return false;
            }

            // 2. Explicit Retryable Types (Network, Timeout, Concurrency)
            if (isExplicitRetryable(current)) {
                return true;
            }

            current = current.getCause();
        }

        return false;
    }

    /**
     * Alias for isRetryable, indicating that the error represents a transient dependency outage.
     */
    public static boolean isTransientError(Throwable throwable) {
        return isRetryable(throwable);
    }

    /**
     * Checks if an exception represents a client-side programming error, validation defect,
     * or non-recoverable business invariant violation.
     */
    private static boolean isExplicitNonRetryable(Throwable t) {
        String className = t.getClass().getName();

        // Standard Java runtime / validation errors
        if (t instanceof IllegalArgumentException
                || t instanceof NullPointerException
                || t instanceof IndexOutOfBoundsException
                || t instanceof UnsupportedOperationException
                || t instanceof SecurityException) {
            return true;
        }

        // Jackson / JSON malformed payload or unmarshalling failures
        if (className.contains("JsonProcessingException")
                || className.contains("JsonParseException")
                || className.contains("MismatchedInputException")
                || className.contains("InvalidFormatException")) {
            return true;
        }

        // Domain validation exceptions
        if (className.contains("ValidationException")
                || className.contains("InvalidEventPayloadException")
                || className.contains("ConstraintViolationException")) {
            return true;
        }

        return false;
    }

    /**
     * Checks if an exception represents a transient I/O, network blip, timeout, or lock conflict.
     */
    private static boolean isExplicitRetryable(Throwable t) {
        String className = t.getClass().getName();

        // Network and HTTP Client timeouts & connectivity issues
        if (t instanceof HttpTimeoutException
                || t instanceof SocketTimeoutException
                || t instanceof ConnectException
                || t instanceof UnknownHostException
                || t instanceof NoRouteToHostException
                || t instanceof SocketException
                || t instanceof InterruptedIOException) {
            return true;
        }

        // General I/O errors (e.g. Broken pipe, Connection reset by peer)
        if (t instanceof IOException) {
            return true;
        }

        // Concurrency / Database transient lock acquisitions
        if (className.contains("LockTimeoutException")
                || className.contains("PessimisticLockException")
                || className.contains("OptimisticLockException")
                || className.contains("CannotAcquireLockException")
                || className.contains("TransientDataAccessException")
                || className.contains("SQLTransientException")) {
            return true;
        }

        // Custom Map or downstream exceptions indicating service unavailability
        if (className.contains("MapServiceUnavailableException")
                || className.contains("MapServiceTimeoutException")
                || className.contains("MapRateLimitException")) {
            return true;
        }

        return false;
    }

    /**
     * Determines whether an HTTP status code returned from a downstream service is retryable.
     *
     * @param statusCode the HTTP response status code
     * @return true if retryable (e.g. 408, 429, 502, 503, 504); false for client errors (400, 401, 403, 404, 409, 422)
     */
    public static boolean isRetryableHttpStatus(int statusCode) {
        // 408 Request Timeout
        if (statusCode == 408) {
            return true;
        }
        // 429 Too Many Requests (Rate limit backpressure)
        if (statusCode == 429) {
            return true;
        }
        // 500 Internal Server Error (often transient in microservice meshes)
        if (statusCode == 500) {
            return true;
        }
        // 502 Bad Gateway
        if (statusCode == 502) {
            return true;
        }
        // 503 Service Unavailable
        if (statusCode == 503) {
            return true;
        }
        // 504 Gateway Timeout
        if (statusCode == 504) {
            return true;
        }

        // Client errors (400, 401, 403, 404, 405, 409, 422) are strictly non-retryable
        return false;
    }
}
