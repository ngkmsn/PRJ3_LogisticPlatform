package com.logistics.common.circuitbreaker;

import com.logistics.common.retry.ErrorClassifier;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Configuration for CircuitBreaker (PB-054).
 */
public class CircuitBreakerConfig {

    private final int failureThreshold;
    private final Duration openDuration;
    private final int successThreshold;
    private final int trialCalls;
    private final Predicate<Throwable> failurePredicate;

    private CircuitBreakerConfig(Builder builder) {
        this.failureThreshold = builder.failureThreshold;
        this.openDuration = builder.openDuration;
        this.successThreshold = builder.successThreshold;
        this.trialCalls = builder.trialCalls;
        this.failurePredicate = builder.failurePredicate != null ? builder.failurePredicate : ErrorClassifier::isTransientError;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static CircuitBreakerConfig defaultConfig() {
        return builder().build();
    }

    public int getFailureThreshold() {
        return failureThreshold;
    }

    public Duration getOpenDuration() {
        return openDuration;
    }

    public int getSuccessThreshold() {
        return successThreshold;
    }

    public int getTrialCalls() {
        return trialCalls;
    }

    public boolean isDependencyFailure(Throwable throwable) {
        return failurePredicate.test(throwable);
    }

    public static class Builder {
        private int failureThreshold = 3;
        private Duration openDuration = Duration.ofSeconds(10);
        private int successThreshold = 2;
        private int trialCalls = 2;
        private Predicate<Throwable> failurePredicate = ErrorClassifier::isTransientError;

        public Builder failureThreshold(int failureThreshold) {
            this.failureThreshold = Math.max(1, failureThreshold);
            return this;
        }

        public Builder openDuration(Duration openDuration) {
            this.openDuration = (openDuration != null && !openDuration.isNegative() && !openDuration.isZero())
                    ? openDuration
                    : Duration.ofSeconds(10);
            return this;
        }

        public Builder successThreshold(int successThreshold) {
            this.successThreshold = Math.max(1, successThreshold);
            return this;
        }

        public Builder trialCalls(int trialCalls) {
            this.trialCalls = Math.max(1, trialCalls);
            return this;
        }

        public Builder failurePredicate(Predicate<Throwable> failurePredicate) {
            this.failurePredicate = failurePredicate;
            return this;
        }

        public CircuitBreakerConfig build() {
            return new CircuitBreakerConfig(this);
        }
    }
}
