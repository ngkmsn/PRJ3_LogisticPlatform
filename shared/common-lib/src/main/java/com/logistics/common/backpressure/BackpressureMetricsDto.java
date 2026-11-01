package com.logistics.common.backpressure;

import java.time.Instant;

/**
 * Data Transfer Object for Backpressure telemetry, in-flight tracking, and operational observation.
 */
public class BackpressureMetricsDto {

    private String name;
    private BackpressureState state;
    private int currentInFlight;
    private int maxConcurrency;
    private int warningConcurrency;
    private double averageLatencyMs;
    private long totalSubmitted;
    private long totalProcessed;
    private long totalThrottled;
    private long totalShed;
    private Instant timestamp;

    public BackpressureMetricsDto() {
        this.timestamp = Instant.now();
    }

    public BackpressureMetricsDto(String name, BackpressureState state, int currentInFlight,
                                  int maxConcurrency, int warningConcurrency, double averageLatencyMs,
                                  long totalSubmitted, long totalProcessed, long totalThrottled,
                                  long totalShed) {
        this.name = name;
        this.state = state;
        this.currentInFlight = currentInFlight;
        this.maxConcurrency = maxConcurrency;
        this.warningConcurrency = warningConcurrency;
        this.averageLatencyMs = averageLatencyMs;
        this.totalSubmitted = totalSubmitted;
        this.totalProcessed = totalProcessed;
        this.totalThrottled = totalThrottled;
        this.totalShed = totalShed;
        this.timestamp = Instant.now();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BackpressureState getState() {
        return state;
    }

    public void setState(BackpressureState state) {
        this.state = state;
    }

    public int getCurrentInFlight() {
        return currentInFlight;
    }

    public void setCurrentInFlight(int currentInFlight) {
        this.currentInFlight = currentInFlight;
    }

    public int getMaxConcurrency() {
        return maxConcurrency;
    }

    public void setMaxConcurrency(int maxConcurrency) {
        this.maxConcurrency = maxConcurrency;
    }

    public int getWarningConcurrency() {
        return warningConcurrency;
    }

    public void setWarningConcurrency(int warningConcurrency) {
        this.warningConcurrency = warningConcurrency;
    }

    public double getAverageLatencyMs() {
        return averageLatencyMs;
    }

    public void setAverageLatencyMs(double averageLatencyMs) {
        this.averageLatencyMs = averageLatencyMs;
    }

    public long getTotalSubmitted() {
        return totalSubmitted;
    }

    public void setTotalSubmitted(long totalSubmitted) {
        this.totalSubmitted = totalSubmitted;
    }

    public long getTotalProcessed() {
        return totalProcessed;
    }

    public void setTotalProcessed(long totalProcessed) {
        this.totalProcessed = totalProcessed;
    }

    public long getTotalThrottled() {
        return totalThrottled;
    }

    public void setTotalThrottled(long totalThrottled) {
        this.totalThrottled = totalThrottled;
    }

    public long getTotalShed() {
        return totalShed;
    }

    public void setTotalShed(long totalShed) {
        this.totalShed = totalShed;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}

