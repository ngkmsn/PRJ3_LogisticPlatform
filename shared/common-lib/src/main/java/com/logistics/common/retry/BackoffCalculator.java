package com.logistics.common.retry;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Computes backoff duration using exponential progression, randomized jitter,
 * and downstream Retry-After header compliance.
 * <p>
 * Progression formula:
 *   Base delay: min(maxBackoff, initialBackoff * (multiplier ^ (attempt - 1)))
 *   Jitter: Base delay * [1 - jitterFactor, 1 + jitterFactor]
 *   Downstream enforcement: max(computedDelay, retryAfter) up to maxBackoff
 */
public class BackoffCalculator {

    private final RetryPolicy policy;

    public BackoffCalculator(RetryPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
    }

    public Duration computeBackoff(int attempt) {
        return computeBackoff(attempt, null, ThreadLocalRandom.current().nextDouble());
    }

    public Duration computeBackoff(int attempt, Duration downstreamRetryAfter) {
        return computeBackoff(attempt, downstreamRetryAfter, ThreadLocalRandom.current().nextDouble());
    }

    public Duration computeBackoff(int attempt, Duration downstreamRetryAfter, double randomFactor) {
        int normalizedAttempt = Math.max(1, attempt);

        long initialMs = policy.getInitialBackoff().toMillis();
        long maxMs = policy.getMaxBackoff().toMillis();
        double multiplier = policy.getMultiplier();

        // 1. Exponential delay: initial * multiplier^(attempt - 1)
        double rawDelayMs = initialMs * Math.pow(multiplier, normalizedAttempt - 1);
        long cappedDelayMs = Math.min((long) rawDelayMs, maxMs);

        // 2. Randomized jitter
        long jitteredDelayMs = cappedDelayMs;
        double jitterFactor = policy.getJitterFactor();
        if (jitterFactor > 0.0) {
            double clampedRandom = Math.max(0.0, Math.min(1.0, randomFactor));
            double delta = cappedDelayMs * jitterFactor;
            double min = cappedDelayMs - delta;
            double jittered = min + (clampedRandom * 2.0 * delta);
            jitteredDelayMs = Math.max(1, Math.round(jittered));
        }

        // 3. Respect downstream Retry-After (if provided and positive)
        long finalDelayMs = jitteredDelayMs;
        if (downstreamRetryAfter != null && !downstreamRetryAfter.isNegative() && !downstreamRetryAfter.isZero()) {
            long downstreamMs = downstreamRetryAfter.toMillis();
            finalDelayMs = Math.max(finalDelayMs, downstreamMs);
        }

        // 4. Bound at maxBackoff
        finalDelayMs = Math.min(finalDelayMs, maxMs);

        return Duration.ofMillis(finalDelayMs);
    }

    public RetryPolicy getPolicy() {
        return policy;
    }
}
