package com.logistics.common.retry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class BackoffCalculatorTest {

    @Test
    @DisplayName("PB-052: Computes exact exponential backoff without jitter")
    void computesExponentialBackoff_withoutJitter() {
        RetryPolicy policy = RetryPolicy.builder()
                .initialBackoff(Duration.ofMillis(500))
                .maxBackoff(Duration.ofMillis(10000))
                .multiplier(2.0)
                .jitterFactor(0.0)
                .build();

        BackoffCalculator calculator = new BackoffCalculator(policy);

        // Attempt 1: 500 * 2^0 = 500ms
        assertThat(calculator.computeBackoff(1, null, 0.5)).isEqualTo(Duration.ofMillis(500));
        // Attempt 2: 500 * 2^1 = 1000ms
        assertThat(calculator.computeBackoff(2, null, 0.5)).isEqualTo(Duration.ofMillis(1000));
        // Attempt 3: 500 * 2^2 = 2000ms
        assertThat(calculator.computeBackoff(3, null, 0.5)).isEqualTo(Duration.ofMillis(2000));
        // Attempt 4: 500 * 2^3 = 4000ms
        assertThat(calculator.computeBackoff(4, null, 0.5)).isEqualTo(Duration.ofMillis(4000));
        // Attempt 5: 500 * 2^4 = 8000ms
        assertThat(calculator.computeBackoff(5, null, 0.5)).isEqualTo(Duration.ofMillis(8000));
        // Attempt 6: 500 * 2^5 = 16000ms -> capped at maxBackoff (10000ms)
        assertThat(calculator.computeBackoff(6, null, 0.5)).isEqualTo(Duration.ofMillis(10000));
    }

    @Test
    @DisplayName("PB-052: Computes backoff with jitter within ±jitterFactor bounds")
    void computesJitterWithinBounds() {
        // Base: 1000ms, jitter: 0.2 (20% -> [800ms, 1200ms])
        RetryPolicy policy = RetryPolicy.builder()
                .initialBackoff(Duration.ofMillis(1000))
                .maxBackoff(Duration.ofSeconds(10))
                .multiplier(2.0)
                .jitterFactor(0.2)
                .build();

        BackoffCalculator calculator = new BackoffCalculator(policy);

        // randomFactor = 0.0 (minimum boundary) -> 1000 - 200 = 800ms
        Duration minBackoff = calculator.computeBackoff(1, null, 0.0);
        assertThat(minBackoff).isEqualTo(Duration.ofMillis(800));

        // randomFactor = 1.0 (maximum boundary) -> 1000 + 200 = 1200ms
        Duration maxBackoff = calculator.computeBackoff(1, null, 1.0);
        assertThat(maxBackoff).isEqualTo(Duration.ofMillis(1200));

        // randomFactor = 0.5 (middle) -> 1000ms
        Duration midBackoff = calculator.computeBackoff(1, null, 0.5);
        assertThat(midBackoff).isEqualTo(Duration.ofMillis(1000));
    }

    @Test
    @DisplayName("PB-052: Respects downstream Retry-After header when provided")
    void respectsDownstreamRetryAfter() {
        RetryPolicy policy = RetryPolicy.builder()
                .initialBackoff(Duration.ofMillis(500))
                .maxBackoff(Duration.ofSeconds(10))
                .multiplier(2.0)
                .jitterFactor(0.0)
                .build();

        BackoffCalculator calculator = new BackoffCalculator(policy);

        // Computed delay for attempt 1 is 500ms, but downstream requests 3000ms
        Duration delay = calculator.computeBackoff(1, Duration.ofMillis(3000), 0.5);
        assertThat(delay).isEqualTo(Duration.ofMillis(3000));

        // If downstream asks for less than calculated delay (e.g. 200ms vs 500ms calculated),
        // our calculated delay (500ms) is respected
        Duration delayCalculatedHigher = calculator.computeBackoff(1, Duration.ofMillis(200), 0.5);
        assertThat(delayCalculatedHigher).isEqualTo(Duration.ofMillis(500));

        // If downstream asks for 15000ms (exceeding our maxBackoff of 10000ms), bounded by maxBackoff
        Duration delayCappedAtMax = calculator.computeBackoff(1, Duration.ofMillis(15000), 0.5);
        assertThat(delayCappedAtMax).isEqualTo(Duration.ofMillis(10000));
    }
}
