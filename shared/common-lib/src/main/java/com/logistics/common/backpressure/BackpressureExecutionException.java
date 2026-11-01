package com.logistics.common.backpressure;

/**
 * Exception thrown when a request or message cannot be processed because backpressure load shedding is active.
 */
public class BackpressureExecutionException extends RuntimeException {

    private final BackpressureState state;
    private final int inFlight;
    private final int maxConcurrency;

    public BackpressureExecutionException(String message, BackpressureState state, int inFlight, int maxConcurrency) {
        super(message);
        this.state = state;
        this.inFlight = inFlight;
        this.maxConcurrency = maxConcurrency;
    }

    public BackpressureState getState() {
        return state;
    }

    public int getInFlight() {
        return inFlight;
    }

    public int getMaxConcurrency() {
        return maxConcurrency;
    }
}

