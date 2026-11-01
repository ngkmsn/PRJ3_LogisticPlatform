package com.logistics.common.retry;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Immutable configuration defining the retry policy for operations, including
 * maximum retry attempts, exponential backoff parameters, randomized jitter, and
 * exception classification.
 */
public final class RetryPolicy {

    public static final int DEFAULT_MAX_RETRIES = 3;
    public static final Duration DEFAULT_INITIAL_BACKOFF = Duration.ofMillis(500);
    public static final Duration DEFAULT_MAX_BACKOFF = Duration.ofSeconds(10);
    public static final double DEFAULT_MULTIPLIER = 2.0;
    public static final double DEFAULT_JITTER_FACTOR = 0.2;

    private final int maxRetries;
    private final Duration initialBackoff;
    private final Duration maxBackoff;
    private final double multiplier;
    private final double jitterFactor;
    private final Predicate<Throwable> retryPredicate;

    private RetryPolicy(Builder builder) {
        this.maxRetries = builder.maxRetries;
        this.initialBackoff = builder.initialBackoff;
        this.maxBackoff = builder.maxBackoff;
        this.multiplier = builder.multiplier;
        this.jitterFactor = builder.jitterFactor;
        this.retryPredicate = builder.retryPredicate != null ? builder.retryPredicate : ErrorClassifier::isRetryable;
    }

    public static RetryPolicy defaultPolicy() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public Duration getInitialBackoff() {
        return initialBackoff;
    }

    public Duration getMaxBackoff() {
        return maxBackoff;
    }

    public double getMultiplier() {
        return multiplier;
    }

    public double getJitterFactor() {
        return jitterFactor;
    }

    public boolean isRetryable(Throwable throwable) {
        return retryPredicate.test(throwable);
    }

    public Predicate<Throwable> getRetryPredicate() {
        return retryPredicate;
    }

    @Override
    public String toString() {
        return "RetryPolicy{" +
                "maxRetries=" + maxRetries +
                ", initialBackoff=" + initialBackoff +
                ", maxBackoff=" + maxBackoff +
                ", multiplier=" + multiplier +
                ", jitterFactor=" + jitterFactor +
                '}';
    }

    public static final class Builder {
        private int maxRetries = DEFAULT_MAX_RETRIES;
        private Duration initialBackoff = DEFAULT_INITIAL_BACKOFF;
        private Duration maxBackoff = DEFAULT_MAX_BACKOFF;
        private double multiplier = DEFAULT_MULTIPLIER;
        private double jitterFactor = DEFAULT_JITTER_FACTOR;
        private Predicate<Throwable> retryPredicate = ErrorClassifier::isRetryable;

        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder initialBackoff(Duration initialBackoff) {
            this.initialBackoff = initialBackoff;
            return this;
        }

        public Builder initialBackoffMillis(long millis) {
            this.initialBackoff = Duration.ofMillis(millis);
            return this;
        }

        public Builder maxBackoff(Duration maxBackoff) {
            this.maxBackoff = maxBackoff;
            return this;
        }

        public Builder maxBackoffMillis(long millis) {
            this.maxBackoff = Duration.ofMillis(millis);
            return this;
        }

        public Builder multiplier(double multiplier) {
            this.multiplier = multiplier;
            return this;
        }

        public Builder jitterFactor(double jitterFactor) {
            this.jitterFactor = jitterFactor;
            return this;
        }

        public Builder retryPredicate(Predicate<Throwable> retryPredicate) {
            this.retryPredicate = retryPredicate;
            return this;
        }

        public RetryPolicy build() {
            validate();
            return new RetryPolicy(this);
        }

        private void validate() {
            if (maxRetries < 0) {
                throw new IllegalArgumentException("maxRetries must not be negative: " + maxRetries);
            }
            if (initialBackoff == null || initialBackoff.isNegative() || initialBackoff.isZero()) {
                throw new IllegalArgumentException("initialBackoff must be positive: " + initialBackoff);
            }
            if (maxBackoff == null || maxBackoff.compareTo(initialBackoff) < 0) {
                throw new IllegalArgumentException("maxBackoff (" + maxBackoff + ") must be >= initialBackoff (" + initialBackoff + ")");
            }
            if (multiplier < 1.0) {
                throw new IllegalArgumentException("multiplier must be >= 1.0: " + multiplier);
            }
            if (jitterFactor < 0.0 || jitterFactor > 1.0) {
                throw new IllegalArgumentException("jitterFactor must be between 0.0 and 1.0: " + jitterFactor);
            }
            Objects.requireNonNull(retryPredicate, "retryPredicate must not be null");
        }
    }
}
