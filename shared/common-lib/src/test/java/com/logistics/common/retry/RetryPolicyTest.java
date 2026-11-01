package com.logistics.common.retry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryPolicyTest {

    @Test
    @DisplayName("PB-052: Default policy has safe production defaults")
    void defaultPolicy_hasSafeDefaults() {
        RetryPolicy policy = RetryPolicy.defaultPolicy();

        assertThat(policy.getMaxRetries()).isEqualTo(3);
        assertThat(policy.getInitialBackoff()).isEqualTo(Duration.ofMillis(500));
        assertThat(policy.getMaxBackoff()).isEqualTo(Duration.ofSeconds(10));
        assertThat(policy.getMultiplier()).isEqualTo(2.0);
        assertThat(policy.getJitterFactor()).isEqualTo(0.2);
    }

    @Test
    @DisplayName("PB-052: Custom policy builds with valid parameters")
    void customPolicy_validParameters() {
        RetryPolicy policy = RetryPolicy.builder()
                .maxRetries(5)
                .initialBackoff(Duration.ofSeconds(1))
                .maxBackoff(Duration.ofSeconds(30))
                .multiplier(1.5)
                .jitterFactor(0.1)
                .build();

        assertThat(policy.getMaxRetries()).isEqualTo(5);
        assertThat(policy.getInitialBackoff()).isEqualTo(Duration.ofSeconds(1));
        assertThat(policy.getMaxBackoff()).isEqualTo(Duration.ofSeconds(30));
        assertThat(policy.getMultiplier()).isEqualTo(1.5);
        assertThat(policy.getJitterFactor()).isEqualTo(0.1);
    }

    @Test
    @DisplayName("PB-052: Rejects negative maxRetries")
    void rejectsNegativeMaxRetries() {
        assertThatThrownBy(() -> RetryPolicy.builder().maxRetries(-1).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxRetries must not be negative");
    }

    @Test
    @DisplayName("PB-052: Rejects zero or negative initialBackoff")
    void rejectsZeroOrNegativeInitialBackoff() {
        assertThatThrownBy(() -> RetryPolicy.builder().initialBackoff(Duration.ZERO).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("initialBackoff must be positive");

        assertThatThrownBy(() -> RetryPolicy.builder().initialBackoff(Duration.ofMillis(-100)).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("initialBackoff must be positive");
    }

    @Test
    @DisplayName("PB-052: Rejects maxBackoff less than initialBackoff")
    void rejectsMaxBackoffLessThanInitialBackoff() {
        assertThatThrownBy(() -> RetryPolicy.builder()
                .initialBackoff(Duration.ofSeconds(5))
                .maxBackoff(Duration.ofSeconds(2))
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxBackoff");
    }

    @Test
    @DisplayName("PB-052: Rejects multiplier less than 1.0")
    void rejectsMultiplierLessThanOne() {
        assertThatThrownBy(() -> RetryPolicy.builder().multiplier(0.9).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("multiplier must be >= 1.0");
    }

    @Test
    @DisplayName("PB-052: Rejects jitterFactor outside [0.0, 1.0]")
    void rejectsInvalidJitterFactor() {
        assertThatThrownBy(() -> RetryPolicy.builder().jitterFactor(-0.1).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jitterFactor must be between 0.0 and 1.0");

        assertThatThrownBy(() -> RetryPolicy.builder().jitterFactor(1.1).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jitterFactor must be between 0.0 and 1.0");
    }
}
