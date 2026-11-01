package com.logistics.common.backpressure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Adaptive Backpressure Controller for regulating in-flight concurrency,
 * detecting downstream degradation via EWMA latency, and shedding load when saturated.
 */
public class BackpressureController {

    private static final Logger log = LoggerFactory.getLogger(BackpressureController.class);

    private final BackpressureConfig config;
    private final AtomicInteger inFlightCount = new AtomicInteger(0);
    private final AtomicLong totalSubmitted = new AtomicLong(0);
    private final AtomicLong totalProcessed = new AtomicLong(0);
    private final AtomicLong totalThrottled = new AtomicLong(0);
    private final AtomicLong totalShed = new AtomicLong(0);

    private volatile double ewmaLatencyMs = 0.0;
    private volatile BackpressureState state = BackpressureState.NORMAL;
    private final ReentrantLock stateLock = new ReentrantLock();

    public BackpressureController(BackpressureConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("BackpressureConfig must not be null");
        }
        this.config = config;
    }

    /**
     * Attempts to acquire an execution permit.
     *
     * @return true if permitted, false if load shedding is triggered
     */
    public boolean tryAcquire() {
        int current = inFlightCount.get();
        if (current >= config.getMaxConcurrency()) {
            state = BackpressureState.SHEDDING;
            totalShed.incrementAndGet();
            log.warn("PB-056 [LOAD_SHEDDING] Controller '{}' saturated! inFlight={}/{}, avgLatency={.1f}ms, rejecting task",
                    config.getName(), current, config.getMaxConcurrency(), ewmaLatencyMs);
            return false;
        }

        int newInFlight = inFlightCount.incrementAndGet();
        totalSubmitted.incrementAndGet();

        updateState(newInFlight);
        return true;
    }

    /**
     * Attempts to acquire a permit, waiting up to the specified timeout if concurrency is full.
     */
    public boolean tryAcquire(long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
        while (System.currentTimeMillis() <= deadline) {
            if (tryAcquire()) {
                return true;
            }
            Thread.sleep(Math.min(20, Math.max(1, deadline - System.currentTimeMillis())));
        }
        return false;
    }

    /**
     * Releases one in-flight slot without recording latency (e.g. on acquisition failure).
     */
    public void release() {
        inFlightCount.updateAndGet(c -> Math.max(0, c - 1));
        updateState(inFlightCount.get());
    }

    /**
     * Records task completion, updates downstream EWMA latency, and releases permit.
     *
     * @param durationMs latency of the completed downstream operation
     * @param success whether the operation succeeded
     */
    public void recordExecution(long durationMs, boolean success) {
        release();
        totalProcessed.incrementAndGet();

        stateLock.lock();
        try {
            if (ewmaLatencyMs <= 0.0) {
                ewmaLatencyMs = Math.max(0, durationMs);
            } else {
                ewmaLatencyMs = (config.getEwmaAlpha() * durationMs) + ((1.0 - config.getEwmaAlpha()) * ewmaLatencyMs);
            }
            updateState(inFlightCount.get());
        } finally {
            stateLock.unlock();
        }
    }

    private void updateState(int inFlight) {
        stateLock.lock();
        try {
            if (inFlight >= config.getMaxConcurrency()) {
                state = BackpressureState.SHEDDING;
            } else if (inFlight >= config.getWarningConcurrency() || ewmaLatencyMs >= config.getHighLatencyMs()) {
                if (state != BackpressureState.THROTTLED) {
                    log.info("PB-056 [BACKPRESSURE_THROTTLED] Controller '{}' entered THROTTLED state (inFlight={}/{}, avgLatency={.1f}ms)",
                            config.getName(), inFlight, config.getMaxConcurrency(), ewmaLatencyMs);
                }
                state = BackpressureState.THROTTLED;
                totalThrottled.incrementAndGet();
            } else if (ewmaLatencyMs <= config.getRecoveryLatencyMs() && inFlight < config.getWarningConcurrency()) {
                if (state != BackpressureState.NORMAL) {
                    log.info("PB-056 [BACKPRESSURE_RECOVERED] Controller '{}' recovered to NORMAL state (inFlight={}/{}, avgLatency={.1f}ms)",
                            config.getName(), inFlight, config.getMaxConcurrency(), ewmaLatencyMs);
                }
                state = BackpressureState.NORMAL;
            }
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Computes the adaptive pacing delay to inject when downstream is experiencing backpressure.
     *
     * @return pacing delay in milliseconds (0 when NORMAL)
     */
    public long getPacingDelayMs() {
        if (state == BackpressureState.NORMAL) {
            return 0;
        }

        int inFlight = inFlightCount.get();
        int warning = config.getWarningConcurrency();
        int max = config.getMaxConcurrency();

        double concurrencyRatio = (max > warning) ? Math.max(0.0, Math.min(1.0, (double) (inFlight - warning) / (max - warning))) : 0.5;
        double latencyRatio = (config.getHighLatencyMs() > config.getRecoveryLatencyMs())
                ? Math.max(0.0, Math.min(1.0, (ewmaLatencyMs - config.getRecoveryLatencyMs()) / (config.getHighLatencyMs() - config.getRecoveryLatencyMs())))
                : 0.5;

        double combinedRatio = Math.max(concurrencyRatio, latencyRatio);
        long min = config.getMinPacingDelayMs();
        long maxDelay = config.getMaxPacingDelayMs();

        return (long) (min + combinedRatio * (maxDelay - min));
    }

    /**
     * Applies pacing delay if currently throttled to slow down intake rate.
     */
    public void applyPacingDelayIfThrottled() {
        long delay = getPacingDelayMs();
        if (delay > 0) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void reset() {
        stateLock.lock();
        try {
            inFlightCount.set(0);
            totalSubmitted.set(0);
            totalProcessed.set(0);
            totalThrottled.set(0);
            totalShed.set(0);
            ewmaLatencyMs = 0.0;
            state = BackpressureState.NORMAL;
        } finally {
            stateLock.unlock();
        }
    }

    public BackpressureMetricsDto getMetrics() {
        return new BackpressureMetricsDto(
                config.getName(),
                state,
                inFlightCount.get(),
                config.getMaxConcurrency(),
                config.getWarningConcurrency(),
                ewmaLatencyMs,
                totalSubmitted.get(),
                totalProcessed.get(),
                totalThrottled.get(),
                totalShed.get()
        );
    }

    public BackpressureConfig getConfig() {
        return config;
    }

    public BackpressureState getState() {
        return state;
    }

    public int getInFlightCount() {
        return inFlightCount.get();
    }

    public double getAverageLatencyMs() {
        return ewmaLatencyMs;
    }
}

