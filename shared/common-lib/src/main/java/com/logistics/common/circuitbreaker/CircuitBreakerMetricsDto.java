package com.logistics.common.circuitbreaker;

import io.smallrye.faulttolerance.api.CircuitBreakerState;

import java.time.Instant;

/**
 * Telemetry and operational monitoring DTO for Circuit Breaker status and metrics (PB-054).
 */
public class CircuitBreakerMetricsDto {

    private String name;
    private String state;
    private long totalCalls;
    private long successfulCalls;
    private long failedCalls;
    private long rejectedCalls;
    private long circuitOpenCount;
    private int consecutiveFailures;
    private int failureThreshold;
    private long openDurationMs;
    private int successThreshold;
    private Instant lastStateChange;
    private Instant timestamp;

    public CircuitBreakerMetricsDto() {
        this.timestamp = Instant.now();
    }

    public CircuitBreakerMetricsDto(String name, CircuitBreakerState state,
                                   long totalCalls, long successfulCalls, long failedCalls,
                                   long rejectedCalls, long circuitOpenCount, int consecutiveFailures,
                                   int failureThreshold, long openDurationMs, int successThreshold,
                                   Instant lastStateChange) {
        this.name = name;
        this.state = state != null ? state.name() : CircuitBreakerState.CLOSED.name();
        this.totalCalls = totalCalls;
        this.successfulCalls = successfulCalls;
        this.failedCalls = failedCalls;
        this.rejectedCalls = rejectedCalls;
        this.circuitOpenCount = circuitOpenCount;
        this.consecutiveFailures = consecutiveFailures;
        this.failureThreshold = failureThreshold;
        this.openDurationMs = openDurationMs;
        this.successThreshold = successThreshold;
        this.lastStateChange = lastStateChange;
        this.timestamp = Instant.now();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public long getTotalCalls() {
        return totalCalls;
    }

    public void setTotalCalls(long totalCalls) {
        this.totalCalls = totalCalls;
    }

    public long getSuccessfulCalls() {
        return successfulCalls;
    }

    public void setSuccessfulCalls(long successfulCalls) {
        this.successfulCalls = successfulCalls;
    }

    public long getFailedCalls() {
        return failedCalls;
    }

    public void setFailedCalls(long failedCalls) {
        this.failedCalls = failedCalls;
    }

    public long getRejectedCalls() {
        return rejectedCalls;
    }

    public void setRejectedCalls(long rejectedCalls) {
        this.rejectedCalls = rejectedCalls;
    }

    public long getCircuitOpenCount() {
        return circuitOpenCount;
    }

    public void setCircuitOpenCount(long circuitOpenCount) {
        this.circuitOpenCount = circuitOpenCount;
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    public void setConsecutiveFailures(int consecutiveFailures) {
        this.consecutiveFailures = consecutiveFailures;
    }

    public int getFailureThreshold() {
        return failureThreshold;
    }

    public void setFailureThreshold(int failureThreshold) {
        this.failureThreshold = failureThreshold;
    }

    public long getOpenDurationMs() {
        return openDurationMs;
    }

    public void setOpenDurationMs(long openDurationMs) {
        this.openDurationMs = openDurationMs;
    }

    public int getSuccessThreshold() {
        return successThreshold;
    }

    public void setSuccessThreshold(int successThreshold) {
        this.successThreshold = successThreshold;
    }

    public Instant getLastStateChange() {
        return lastStateChange;
    }

    public void setLastStateChange(Instant lastStateChange) {
        this.lastStateChange = lastStateChange;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
