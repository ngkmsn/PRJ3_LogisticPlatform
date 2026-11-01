package com.logistics.common.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryRateLimiterStorageTest {

    private InMemoryRateLimiterStorage storage;

    @BeforeEach
    void setUp() {
        storage = new InMemoryRateLimiterStorage();
    }

    @Test
    @DisplayName("Requests within limit are permitted with correct remaining permits")
    void requestsWithinLimit_areAllowed() {
        RateLimitPolicy policy = RateLimitPolicy.authPolicy(3, 60);

        RateLimitResult r1 = storage.checkAndConsume("192.168.1.1", policy);
        assertThat(r1.isAllowed()).isTrue();
        assertThat(r1.getRemaining()).isEqualTo(2);
        assertThat(r1.getLimit()).isEqualTo(3);
        assertThat(r1.getRetryAfterSeconds()).isEqualTo(0);

        RateLimitResult r2 = storage.checkAndConsume("192.168.1.1", policy);
        assertThat(r2.isAllowed()).isTrue();
        assertThat(r2.getRemaining()).isEqualTo(1);

        RateLimitResult r3 = storage.checkAndConsume("192.168.1.1", policy);
        assertThat(r3.isAllowed()).isTrue();
        assertThat(r3.getRemaining()).isEqualTo(0);
    }

    @Test
    @DisplayName("Request exceeding limit is rejected with retryAfterSeconds > 0")
    void requestExceedingLimit_isRejected() {
        RateLimitPolicy policy = RateLimitPolicy.authPolicy(2, 60);

        storage.checkAndConsume("client-1", policy);
        storage.checkAndConsume("client-1", policy);

        RateLimitResult r3 = storage.checkAndConsume("client-1", policy);
        assertThat(r3.isAllowed()).isFalse();
        assertThat(r3.getRemaining()).isEqualTo(0);
        assertThat(r3.getRetryAfterSeconds()).isGreaterThan(0);
        assertThat(r3.getPolicyName()).isEqualTo("auth");
        assertThat(r3.getKey()).isEqualTo("client-1");
    }

    @Test
    @DisplayName("Different keys have independent rate limit quotas")
    void differentKeys_haveIndependentQuotas() {
        RateLimitPolicy policy = RateLimitPolicy.ordersPolicy(2, 60);

        storage.checkAndConsume("user-1", policy);
        storage.checkAndConsume("user-1", policy);
        assertThat(storage.checkAndConsume("user-1", policy).isAllowed()).isFalse();

        // user-2 should still have full quota
        RateLimitResult rUser2 = storage.checkAndConsume("user-2", policy);
        assertThat(rUser2.isAllowed()).isTrue();
        assertThat(rUser2.getRemaining()).isEqualTo(1);
    }

    @Test
    @DisplayName("Reset specific key clears its counter while other keys remain")
    void resetKey_clearsOnlySpecifiedKey() {
        RateLimitPolicy policy = RateLimitPolicy.defaultPolicy(1, 60);

        storage.checkAndConsume("key-a", policy);
        storage.checkAndConsume("key-b", policy);

        assertThat(storage.checkAndConsume("key-a", policy).isAllowed()).isFalse();
        assertThat(storage.checkAndConsume("key-b", policy).isAllowed()).isFalse();

        storage.reset("key-a");

        assertThat(storage.checkAndConsume("key-a", policy).isAllowed()).isTrue();
        assertThat(storage.checkAndConsume("key-b", policy).isAllowed()).isFalse();
    }

    @Test
    @DisplayName("Reset all clears every tracked key")
    void resetAll_clearsEveryKey() {
        RateLimitPolicy policy = RateLimitPolicy.defaultPolicy(1, 60);

        storage.checkAndConsume("k1", policy);
        storage.checkAndConsume("k2", policy);

        storage.resetAll();

        assertThat(storage.checkAndConsume("k1", policy).isAllowed()).isTrue();
        assertThat(storage.checkAndConsume("k2", policy).isAllowed()).isTrue();
    }

    @Test
    @DisplayName("Throws exception on invalid key or policy")
    void invalidArguments_throwException() {
        RateLimitPolicy policy = RateLimitPolicy.defaultPolicy(10, 60);
        assertThatThrownBy(() -> storage.checkAndConsume(null, policy))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.checkAndConsume("key", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

