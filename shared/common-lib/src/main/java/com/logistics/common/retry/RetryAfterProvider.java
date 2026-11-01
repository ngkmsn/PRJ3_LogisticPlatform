package com.logistics.common.retry;

import java.time.Duration;

/**
 * Interface implemented by exceptions or response wrappers that convey downstream
 * Retry-After guidance (e.g. from HTTP 429 or 503 Retry-After headers).
 */
public interface RetryAfterProvider {
    Duration getRetryAfter();
}
