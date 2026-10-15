package com.logistics.order.lifecycle;

import com.logistics.common.model.OrderStatus;
import com.logistics.order.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderLifecycleValidatorTest {

    private OrderLifecycleValidator validator;

    @BeforeEach
    void setUp() {
        validator = new OrderLifecycleValidator();
    }

    @Test
    @DisplayName("Initial order status must always be PENDING")
    void getInitialStatus_returnsPending() {
        assertThat(validator.getInitialStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @ParameterizedTest(name = "{0} is terminal: {1}")
    @CsvSource({
            "PENDING, false",
            "CONFIRMED, false",
            "ASSIGNED, false",
            "IN_TRANSIT, false",
            "DELIVERED, true",
            "CANCELLED, true"
    })
    @DisplayName("Check terminal status classification")
    void isTerminal(OrderStatus status, boolean expectedTerminal) {
        assertThat(validator.isTerminal(status)).isEqualTo(expectedTerminal);
    }

    @Test
    @DisplayName("isTerminal returns false for null")
    void isTerminal_null_returnsFalse() {
        assertThat(validator.isTerminal(null)).isFalse();
    }

    // =========================================================================
    // Valid transitions
    // =========================================================================

    @ParameterizedTest(name = "Valid transition: {0} -> {1}")
    @CsvSource({
            "PENDING, CONFIRMED",
            "PENDING, CANCELLED",
            "CONFIRMED, ASSIGNED",
            "CONFIRMED, CANCELLED",
            "ASSIGNED, IN_TRANSIT",
            "ASSIGNED, CONFIRMED",
            "ASSIGNED, CANCELLED",
            "IN_TRANSIT, DELIVERED",
            "IN_TRANSIT, CANCELLED"
    })
    @DisplayName("All 9 valid state transitions succeed")
    void validTransitions_success(OrderStatus from, OrderStatus to) {
        assertThat(validator.isValidTransition(from, to)).isTrue();
        assertThatCode(() -> validator.validateTransition(from, to))
                .doesNotThrowAnyException();
    }

    // =========================================================================
    // Invalid transitions (all other 27 combinations)
    // =========================================================================

    @ParameterizedTest(name = "Invalid transition: {0} -> {1}")
    @CsvSource({
            // Same state (from == to)
            "PENDING, PENDING",
            "CONFIRMED, CONFIRMED",
            "ASSIGNED, ASSIGNED",
            "IN_TRANSIT, IN_TRANSIT",
            "DELIVERED, DELIVERED",
            "CANCELLED, CANCELLED",

            // Invalid from PENDING
            "PENDING, ASSIGNED",
            "PENDING, IN_TRANSIT",
            "PENDING, DELIVERED",

            // Invalid from CONFIRMED
            "CONFIRMED, PENDING",
            "CONFIRMED, IN_TRANSIT",
            "CONFIRMED, DELIVERED",

            // Invalid from ASSIGNED
            "ASSIGNED, PENDING",
            "ASSIGNED, DELIVERED",

            // Invalid from IN_TRANSIT
            "IN_TRANSIT, PENDING",
            "IN_TRANSIT, CONFIRMED",
            "IN_TRANSIT, ASSIGNED",

            // Terminal DELIVERED cannot transition anywhere
            "DELIVERED, PENDING",
            "DELIVERED, CONFIRMED",
            "DELIVERED, ASSIGNED",
            "DELIVERED, IN_TRANSIT",
            "DELIVERED, CANCELLED",

            // Terminal CANCELLED cannot transition anywhere
            "CANCELLED, PENDING",
            "CANCELLED, CONFIRMED",
            "CANCELLED, ASSIGNED",
            "CANCELLED, IN_TRANSIT",
            "CANCELLED, DELIVERED"
    })
    @DisplayName("All 27 invalid state transitions are blocked with InvalidStateTransitionException")
    void invalidTransitions_blocked(OrderStatus from, OrderStatus to) {
        assertThat(validator.isValidTransition(from, to)).isFalse();
        assertThatThrownBy(() -> validator.validateTransition(from, to))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("Invalid order state transition");
    }

    // =========================================================================
    // Null handling
    // =========================================================================

    @Test
    @DisplayName("Null parameters in isValidTransition return false")
    void isValidTransition_nullHandling() {
        assertThat(validator.isValidTransition(null, OrderStatus.PENDING)).isFalse();
        assertThat(validator.isValidTransition(OrderStatus.PENDING, null)).isFalse();
        assertThat(validator.isValidTransition(null, null)).isFalse();
    }

    @Test
    @DisplayName("Null parameters in validateTransition throw IllegalArgumentException")
    void validateTransition_nullHandling() {
        assertThatThrownBy(() -> validator.validateTransition(null, OrderStatus.PENDING))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validateTransition(OrderStatus.PENDING, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // =========================================================================
    // Allowed next statuses inspection
    // =========================================================================

    @Test
    @DisplayName("Verify allowed next statuses for each state")
    void getAllowedNextStatuses_allStates() {
        assertThat(validator.getAllowedNextStatuses(OrderStatus.PENDING))
                .containsExactlyInAnyOrder(OrderStatus.CONFIRMED, OrderStatus.CANCELLED);

        assertThat(validator.getAllowedNextStatuses(OrderStatus.CONFIRMED))
                .containsExactlyInAnyOrder(OrderStatus.ASSIGNED, OrderStatus.CANCELLED);

        assertThat(validator.getAllowedNextStatuses(OrderStatus.ASSIGNED))
                .containsExactlyInAnyOrder(OrderStatus.IN_TRANSIT, OrderStatus.CONFIRMED, OrderStatus.CANCELLED);

        assertThat(validator.getAllowedNextStatuses(OrderStatus.IN_TRANSIT))
                .containsExactlyInAnyOrder(OrderStatus.DELIVERED, OrderStatus.CANCELLED);

        assertThat(validator.getAllowedNextStatuses(OrderStatus.DELIVERED)).isEmpty();
        assertThat(validator.getAllowedNextStatuses(OrderStatus.CANCELLED)).isEmpty();
        assertThat(validator.getAllowedNextStatuses(null)).isEmpty();
    }
}
