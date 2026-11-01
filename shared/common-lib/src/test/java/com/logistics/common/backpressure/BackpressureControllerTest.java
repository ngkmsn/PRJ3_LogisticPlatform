package com.logistics.common.backpressure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BackpressureControllerTest {

    private BackpressureConfig config;
    private BackpressureController controller;

    @BeforeEach
    void setUp() {
        config = BackpressureConfig.builder()
                .name("test-bp")
                .maxConcurrency(5)
                .warningConcurrency(3)
                .highLatencyMs(800)
                .recoveryLatencyMs(200)
                .minPacingDelayMs(20)
                .maxPacingDelayMs(100)
                .ewmaAlpha(0.5)
                .build();

        controller = new BackpressureController(config);
    }

    @Test
    @DisplayName("Normal processing under low load maintains NORMAL state and zero pacing delay")
    void normalProcessing_lowLoad() {
        assertThat(controller.getState()).isEqualTo(BackpressureState.NORMAL);
        assertThat(controller.getPacingDelayMs()).isEqualTo(0);

        boolean acquired = controller.tryAcquire();
        assertThat(acquired).isTrue();
        assertThat(controller.getInFlightCount()).isEqualTo(1);
        assertThat(controller.getState()).isEqualTo(BackpressureState.NORMAL);

        controller.recordExecution(50, true);
        assertThat(controller.getInFlightCount()).isEqualTo(0);
        assertThat(controller.getAverageLatencyMs()).isEqualTo(50.0);
        assertThat(controller.getState()).isEqualTo(BackpressureState.NORMAL);
    }

    @Test
    @DisplayName("Downstream latency elevation shifts state to THROTTLED with non-zero pacing delay")
    void highDownstreamLatency_triggersThrottling() {
        controller.tryAcquire();
        // Record very high downstream latency (1200ms > 800ms threshold)
        controller.recordExecution(1200, true);

        assertThat(controller.getState()).isEqualTo(BackpressureState.THROTTLED);
        long pacingDelay = controller.getPacingDelayMs();
        assertThat(pacingDelay).isGreaterThanOrEqualTo(config.getMinPacingDelayMs());
        assertThat(pacingDelay).isLessThanOrEqualTo(config.getMaxPacingDelayMs());
    }

    @Test
    @DisplayName("In-flight saturation triggers SHEDDING and strictly rejects tasks beyond maxConcurrency")
    void inFlightSaturation_triggersLoadShedding() {
        // maxConcurrency is 5
        for (int i = 0; i < 5; i++) {
            assertThat(controller.tryAcquire()).isTrue();
        }

        assertThat(controller.getInFlightCount()).isEqualTo(5);

        // 6th task must be rejected!
        boolean overflowAcquired = controller.tryAcquire();
        assertThat(overflowAcquired).isFalse();
        assertThat(controller.getState()).isEqualTo(BackpressureState.SHEDDING);

        BackpressureMetricsDto metrics = controller.getMetrics();
        assertThat(metrics.getTotalShed()).isEqualTo(1);
        assertThat(metrics.getCurrentInFlight()).isEqualTo(5);

        // Releasing permits allows new tasks
        controller.release();
        assertThat(controller.getInFlightCount()).isEqualTo(4);
        assertThat(controller.tryAcquire()).isTrue();
    }

    @Test
    @DisplayName("Downstream recovery smoothly restores NORMAL state once latency drops below recovery threshold")
    void downstreamRecovery_restoresNormalState() {
        // Elevate latency to trigger THROTTLED
        controller.tryAcquire();
        controller.recordExecution(1500, true);
        assertThat(controller.getState()).isEqualTo(BackpressureState.THROTTLED);

        // Subsequent fast downstream responses (100ms < 200ms)
        for (int i = 0; i < 5; i++) {
            controller.tryAcquire();
            controller.recordExecution(80, true);
        }

        assertThat(controller.getAverageLatencyMs()).isLessThanOrEqualTo(200.0);
        assertThat(controller.getState()).isEqualTo(BackpressureState.NORMAL);
        assertThat(controller.getPacingDelayMs()).isEqualTo(0);
    }

    @Test
    @DisplayName("Concurrent execution respects maxConcurrency without race condition leaks")
    void concurrentExecution_strictlyBounded() throws InterruptedException {
        int threadCount = 20;
        int maxConcurrency = 5;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger admitted = new AtomicInteger(0);
        AtomicInteger rejected = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    if (controller.tryAcquire()) {
                        admitted.incrementAndGet();
                        // Hold permit briefly
                        Thread.sleep(30);
                        controller.recordExecution(50, true);
                    } else {
                        rejected.incrementAndGet();
                    }
                } catch (InterruptedException ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        assertThat(admitted.get() + rejected.get()).isEqualTo(threadCount);
        assertThat(rejected.get()).isGreaterThan(0);
        assertThat(controller.getInFlightCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Invalid configuration parameters throw IllegalArgumentException")
    void invalidConfig_throwsException() {
        assertThatThrownBy(() -> new BackpressureConfig(null, 10, 5, 100, 50, 10, 50, 0.2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BackpressureConfig("test", -1, 5, 100, 50, 10, 50, 0.2))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

