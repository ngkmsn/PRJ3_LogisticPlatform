package com.logistics.common.backpressure;

/**
 * Operating states of the Backpressure Controller based on downstream responsiveness and in-flight load.
 */
public enum BackpressureState {
    /**
     * Normal operation: Downstream is healthy and latency is low. Requests and messages process at full speed.
     */
    NORMAL,

    /**
     * Throttled operation: Downstream latency is elevated or in-flight concurrency is near capacity.
     * Pacing delays and adaptive throttling are applied to slow down intake.
     */
    THROTTLED,

    /**
     * Load shedding: Downstream is severely congested or in-flight concurrency reached hard maximum.
     * New tasks are temporarily shed or deferred to protect in-progress work from cascading failure.
     */
    SHEDDING
}

