package com.logistics.common.circuitbreaker;

import io.smallrye.faulttolerance.api.CircuitBreakerState;
import org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Standard Circuit Breaker implementation for the Logistics Platform (PB-054).
 * Coordinates with MicroProfile and SmallRye Fault Tolerance state model (CLOSED, OPEN, HALF_OPEN)
 * and uses ErrorClassifier from PB-052 to differentiate transient dependency faults from business errors.
 */
public class CircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreaker.class);

    private final String name;
    private final CircuitBreakerConfig config;

    private final AtomicReference<CircuitBreakerState> state = new AtomicReference<>(CircuitBreakerState.CLOSED);
    private final AtomicReference<Instant> lastStateChange = new AtomicReference<>(Instant.now());

    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicInteger consecutiveSuccesses = new AtomicInteger(0);
    private final AtomicInteger halfOpenTrials = new AtomicInteger(0);

    // Operational telemetry metrics
    private final AtomicLong totalCalls = new AtomicLong(0);
    private final AtomicLong successfulCalls = new AtomicLong(0);
    private final AtomicLong failedCalls = new AtomicLong(0);
    private final AtomicLong rejectedCalls = new AtomicLong(0);
    private final AtomicLong circuitOpenCount = new AtomicLong(0);

    public CircuitBreaker(String name) {
        this(name, CircuitBreakerConfig.defaultConfig());
    }

    public CircuitBreaker(String name, CircuitBreakerConfig config) {
        this.name = Objects.requireNonNull(name, "Circuit breaker name cannot be null");
        this.config = Objects.requireNonNull(config, "Circuit breaker config cannot be null");
    }

    public String getName() {
        return name;
    }

    public CircuitBreakerConfig getConfig() {
        return config;
    }

    /**
     * Retrieves the current state of the circuit breaker.
     * Evaluates automatic transition from OPEN to HALF_OPEN when open duration has elapsed.
     */
    public CircuitBreakerState getState() {
        CircuitBreakerState current = state.get();
        if (current == CircuitBreakerState.OPEN) {
            Instant openTime = lastStateChange.get();
            if (Instant.now().isAfter(openTime.plus(config.getOpenDuration()))) {
                if (state.compareAndSet(CircuitBreakerState.OPEN, CircuitBreakerState.HALF_OPEN)) {
                    lastStateChange.set(Instant.now());
                    halfOpenTrials.set(0);
                    consecutiveSuccesses.set(0);
                    log.info("PB-054 [CIRCUIT_HALF_OPEN] Circuit breaker '{}' transitioned from OPEN to HALF_OPEN after {}ms. Probing downstream health.",
                            name, config.getOpenDuration().toMillis());
                    return CircuitBreakerState.HALF_OPEN;
                }
            }
        }
        return state.get();
    }

    public boolean isOpen() {
        return getState() == CircuitBreakerState.OPEN;
    }

    public boolean isClosed() {
        return getState() == CircuitBreakerState.CLOSED;
    }

    public boolean isHalfOpen() {
        return getState() == CircuitBreakerState.HALF_OPEN;
    }

    /**
     * Executes the protected action under the Circuit Breaker policy.
     * Throws CircuitBreakerOpenException if the circuit is currently OPEN.
     */
    public <T> T execute(Callable<T> action) throws Exception {
        return execute(action, null);
    }

    /**
     * Executes the protected action with an optional fallback supplier when the circuit is OPEN.
     */
    public <T> T execute(Callable<T> action, Supplier<T> fallback) throws Exception {
        totalCalls.incrementAndGet();

        CircuitBreakerState currentState = getState();

        if (currentState == CircuitBreakerState.OPEN) {
            rejectedCalls.incrementAndGet();
            log.warn("PB-054 [CIRCUIT_CALL_REJECTED] Call to '{}' rejected: circuit is OPEN. Short-circuiting call to protect resources.", name);
            if (fallback != null) {
                return fallback.get();
            }
            throw new CircuitBreakerOpenException(String.format("Circuit breaker '%s' is OPEN. Downstream dependency is currently unavailable.", name));
        }

        if (currentState == CircuitBreakerState.HALF_OPEN) {
            int trial = halfOpenTrials.incrementAndGet();
            if (trial > config.getTrialCalls()) {
                rejectedCalls.incrementAndGet();
                log.warn("PB-054 [CIRCUIT_CALL_REJECTED] Call to '{}' rejected in HALF_OPEN: trial concurrency limit ({}) reached.", name, config.getTrialCalls());
                if (fallback != null) {
                    return fallback.get();
                }
                throw new CircuitBreakerOpenException(String.format("Circuit breaker '%s' is HALF_OPEN. Trial calls limit reached.", name));
            }
        }

        try {
            T result = action.call();
            recordSuccess();
            return result;
        } catch (Throwable t) {
            recordFailure(t);
            if (t instanceof Exception e) {
                throw e;
            }
            throw new RuntimeException(t);
        }
    }

    /**
     * Explicitly records a successful call to the dependency.
     */
    public void recordSuccess() {
        successfulCalls.incrementAndGet();
        CircuitBreakerState current = getState();

        if (current == CircuitBreakerState.HALF_OPEN) {
            int successes = consecutiveSuccesses.incrementAndGet();
            log.info("PB-054 [CIRCUIT_PROBE_SUCCESS] Circuit breaker '{}' trial call succeeded ({}/{}).",
                    name, successes, config.getSuccessThreshold());

            if (successes >= config.getSuccessThreshold()) {
                if (state.compareAndSet(CircuitBreakerState.HALF_OPEN, CircuitBreakerState.CLOSED)) {
                    consecutiveFailures.set(0);
                    consecutiveSuccesses.set(0);
                    halfOpenTrials.set(0);
                    lastStateChange.set(Instant.now());
                    log.info("PB-054 [CIRCUIT_CLOSED] Circuit breaker '{}' recovered from HALF_OPEN to CLOSED. Downstream service is healthy.", name);
                }
            }
        } else if (current == CircuitBreakerState.CLOSED) {
            consecutiveFailures.set(0);
        }
    }

    /**
     * Explicitly records a failed call to the dependency.
     * Evaluates failure predicate to avoid tripping circuit on client/business errors.
     */
    public void recordFailure(Throwable throwable) {
        failedCalls.incrementAndGet();

        // 1. Check if error is a real dependency failure (network, timeout, 5xx)
        if (!config.isDependencyFailure(throwable)) {
            log.debug("PB-054 [CIRCUIT_IGNORE_ERROR] Error '{}' in '{}' is classified as non-dependency/business error. Not counting towards circuit trip.",
                    throwable != null ? throwable.getClass().getSimpleName() : "null", name);
            return;
        }

        CircuitBreakerState current = getState();

        if (current == CircuitBreakerState.HALF_OPEN) {
            // Any transient failure in HALF_OPEN trips immediately back to OPEN
            if (state.compareAndSet(CircuitBreakerState.HALF_OPEN, CircuitBreakerState.OPEN)) {
                lastStateChange.set(Instant.now());
                circuitOpenCount.incrementAndGet();
                log.warn("PB-054 [CIRCUIT_OPEN] Probe call failed in HALF_OPEN for '{}' ({}). Tripping back to OPEN for {}ms.",
                        name, throwable != null ? throwable.getMessage() : "unknown", config.getOpenDuration().toMillis());
            }
        } else if (current == CircuitBreakerState.CLOSED) {
            int failures = consecutiveFailures.incrementAndGet();
            log.warn("PB-054 [CIRCUIT_FAILURE] Consecutive failure ({}/{}) recorded for '{}': {}",
                    failures, config.getFailureThreshold(), name, throwable != null ? throwable.getMessage() : "unknown");

            if (failures >= config.getFailureThreshold()) {
                if (state.compareAndSet(CircuitBreakerState.CLOSED, CircuitBreakerState.OPEN)) {
                    lastStateChange.set(Instant.now());
                    circuitOpenCount.incrementAndGet();
                    log.error("PB-054 [CIRCUIT_OPEN] Circuit breaker '{}' tripped from CLOSED to OPEN after {} consecutive failures! Rejecting calls for {}ms.",
                            name, failures, config.getOpenDuration().toMillis());
                }
            }
        }
    }

    /**
     * Manually resets the circuit breaker back to CLOSED state.
     */
    public void reset() {
        state.set(CircuitBreakerState.CLOSED);
        consecutiveFailures.set(0);
        consecutiveSuccesses.set(0);
        halfOpenTrials.set(0);
        lastStateChange.set(Instant.now());
        log.info("PB-054 [CIRCUIT_RESET] Circuit breaker '{}' manually reset to CLOSED.", name);
    }

    /**
     * Returns a snapshot of current telemetry metrics.
     */
    public CircuitBreakerMetricsDto getMetrics() {
        return new CircuitBreakerMetricsDto(
                name,
                getState(),
                totalCalls.get(),
                successfulCalls.get(),
                failedCalls.get(),
                rejectedCalls.get(),
                circuitOpenCount.get(),
                consecutiveFailures.get(),
                config.getFailureThreshold(),
                config.getOpenDuration().toMillis(),
                config.getSuccessThreshold(),
                lastStateChange.get()
        );
    }
}
