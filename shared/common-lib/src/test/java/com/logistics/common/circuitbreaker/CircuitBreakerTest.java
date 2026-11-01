package com.logistics.common.circuitbreaker;

import io.smallrye.faulttolerance.api.CircuitBreakerState;
import org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CircuitBreakerTest {

    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        CircuitBreakerConfig config = CircuitBreakerConfig.builder()
                .failureThreshold(3)
                .openDuration(Duration.ofMillis(100))
                .successThreshold(2)
                .trialCalls(2)
                .build();
        circuitBreaker = new CircuitBreaker("test-service", config);
    }

    @Test
    @DisplayName("Circuit starts in CLOSED state with zero failure count")
    void initialState_isClosed() {
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreakerState.CLOSED);
        assertThat(circuitBreaker.isClosed()).isTrue();
        assertThat(circuitBreaker.isOpen()).isFalse();
        assertThat(circuitBreaker.isHalfOpen()).isFalse();

        CircuitBreakerMetricsDto metrics = circuitBreaker.getMetrics();
        assertThat(metrics.getTotalCalls()).isZero();
        assertThat(metrics.getCircuitOpenCount()).isZero();
    }

    @Test
    @DisplayName("Successful calls execute normally and keep circuit CLOSED")
    void execute_successfulCalls_remainClosed() throws Exception {
        String result = circuitBreaker.execute(() -> "success");
        assertThat(result).isEqualTo("success");
        assertThat(circuitBreaker.isClosed()).isTrue();

        CircuitBreakerMetricsDto metrics = circuitBreaker.getMetrics();
        assertThat(metrics.getTotalCalls()).isEqualTo(1);
        assertThat(metrics.getSuccessfulCalls()).isEqualTo(1);
        assertThat(metrics.getFailedCalls()).isZero();
    }

    @Test
    @DisplayName("Circuit trips to OPEN after failureThreshold consecutive transient errors")
    void consecutiveFailures_tripsToOpen() {
        // Attempt 1: ConnectException (transient)
        assertThatThrownBy(() -> circuitBreaker.execute(() -> {
            throw new ConnectException("Connection refused");
        })).isInstanceOf(ConnectException.class);
        assertThat(circuitBreaker.isClosed()).isTrue();

        // Attempt 2: SocketTimeoutException (transient)
        assertThatThrownBy(() -> circuitBreaker.execute(() -> {
            throw new SocketTimeoutException("Read timed out");
        })).isInstanceOf(SocketTimeoutException.class);
        assertThat(circuitBreaker.isClosed()).isTrue();

        // Attempt 3: IOException (transient) -> reaches threshold of 3 -> trips to OPEN
        assertThatThrownBy(() -> circuitBreaker.execute(() -> {
            throw new IOException("Remote host closed connection");
        })).isInstanceOf(IOException.class);

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreakerState.OPEN);
        assertThat(circuitBreaker.isOpen()).isTrue();

        CircuitBreakerMetricsDto metrics = circuitBreaker.getMetrics();
        assertThat(metrics.getCircuitOpenCount()).isEqualTo(1);
        assertThat(metrics.getFailedCalls()).isEqualTo(3);
    }

    @Test
    @DisplayName("When circuit is OPEN, calls are immediately rejected without executing the action")
    void openState_rejectsCallsWithoutExecutingAction() {
        // Force circuit to OPEN with 3 failures
        for (int i = 0; i < 3; i++) {
            circuitBreaker.recordFailure(new ConnectException("Downstream down"));
        }
        assertThat(circuitBreaker.isOpen()).isTrue();

        AtomicBoolean actionExecuted = new AtomicBoolean(false);

        // Next call should immediately throw CircuitBreakerOpenException without invoking action
        assertThatThrownBy(() -> circuitBreaker.execute(() -> {
            actionExecuted.set(true);
            return "should_not_run";
        })).isInstanceOf(CircuitBreakerOpenException.class)
                .hasMessageContaining("test-service")
                .hasMessageContaining("OPEN");

        assertThat(actionExecuted.get()).isFalse();
        assertThat(circuitBreaker.getMetrics().getRejectedCalls()).isEqualTo(1);
    }

    @Test
    @DisplayName("When circuit is OPEN, fallback supplier is executed safely")
    void openState_withFallback_executesFallback() throws Exception {
        for (int i = 0; i < 3; i++) {
            circuitBreaker.recordFailure(new ConnectException("Downstream down"));
        }
        assertThat(circuitBreaker.isOpen()).isTrue();

        String result = circuitBreaker.execute(() -> "primary_result", () -> "fallback_result");

        assertThat(result).isEqualTo("fallback_result");
        assertThat(circuitBreaker.getMetrics().getRejectedCalls()).isEqualTo(1);
    }

    @Test
    @DisplayName("Business / validation errors are NOT counted as dependency failures and do NOT trip circuit")
    void businessErrors_doNotTripCircuit() {
        // 5 consecutive validation / client errors
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> circuitBreaker.execute(() -> {
                throw new IllegalArgumentException("Invalid client input: field missing");
            })).isInstanceOf(IllegalArgumentException.class);
        }

        // Circuit must still be CLOSED because business errors reflect client mistakes, not dependency outage
        assertThat(circuitBreaker.isClosed()).isTrue();
        assertThat(circuitBreaker.getMetrics().getCircuitOpenCount()).isZero();
    }

    @Test
    @DisplayName("Transitions to HALF_OPEN after openDuration, and recovers to CLOSED after successThreshold successes")
    void halfOpen_recoversToClosed_afterSuccessThreshold() throws Exception {
        // Trip circuit to OPEN
        for (int i = 0; i < 3; i++) {
            circuitBreaker.recordFailure(new ConnectException("Downstream down"));
        }
        assertThat(circuitBreaker.isOpen()).isTrue();

        // Wait for open duration (100ms in test config)
        Thread.sleep(120);

        // Calling getState() should now transition to HALF_OPEN
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreakerState.HALF_OPEN);
        assertThat(circuitBreaker.isHalfOpen()).isTrue();

        // First trial call succeeds
        String res1 = circuitBreaker.execute(() -> "probe-1");
        assertThat(res1).isEqualTo("probe-1");
        assertThat(circuitBreaker.isHalfOpen()).isTrue(); // Needs 2 successes

        // Second trial call succeeds -> achieves successThreshold of 2 -> recovers to CLOSED
        String res2 = circuitBreaker.execute(() -> "probe-2");
        assertThat(res2).isEqualTo("probe-2");
        assertThat(circuitBreaker.isClosed()).isTrue();
    }

    @Test
    @DisplayName("When in HALF_OPEN, if a probe call fails, circuit trips immediately back to OPEN")
    void halfOpen_probeFailure_tripsBackToOpen() throws Exception {
        // Trip circuit to OPEN
        for (int i = 0; i < 3; i++) {
            circuitBreaker.recordFailure(new ConnectException("Downstream down"));
        }
        Thread.sleep(120);
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreakerState.HALF_OPEN);

        // Probe call fails with transient error
        assertThatThrownBy(() -> circuitBreaker.execute(() -> {
            throw new ConnectException("Still down");
        })).isInstanceOf(ConnectException.class);

        // Circuit immediately trips back to OPEN
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreakerState.OPEN);
        assertThat(circuitBreaker.isOpen()).isTrue();
        assertThat(circuitBreaker.getMetrics().getCircuitOpenCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Manual reset restores circuit to CLOSED state")
    void reset_restoresToClosed() {
        for (int i = 0; i < 3; i++) {
            circuitBreaker.recordFailure(new ConnectException("Downstream down"));
        }
        assertThat(circuitBreaker.isOpen()).isTrue();

        circuitBreaker.reset();

        assertThat(circuitBreaker.isClosed()).isTrue();
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreakerState.CLOSED);
    }

    @Test
    @DisplayName("CircuitBreakerRegistry creates, retrieves, and resets named circuit breakers")
    void registry_managesCircuitBreakers() {
        CircuitBreakerRegistry registry = new CircuitBreakerRegistry();

        CircuitBreaker cb1 = registry.getOrCreate("client-a");
        CircuitBreaker cb2 = registry.getOrCreate("client-a");
        assertThat(cb1).isSameAs(cb2);

        cb1.recordFailure(new ConnectException("Fail"));
        cb1.recordFailure(new ConnectException("Fail"));
        cb1.recordFailure(new ConnectException("Fail"));
        assertThat(cb1.isOpen()).isTrue();

        boolean reset = registry.reset("client-a");
        assertThat(reset).isTrue();
        assertThat(cb1.isClosed()).isTrue();

        assertThat(registry.getAll()).hasSize(1);
        assertThat(registry.getAllMetrics()).hasSize(1);
    }
}
