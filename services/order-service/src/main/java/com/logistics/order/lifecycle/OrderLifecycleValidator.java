package com.logistics.order.lifecycle;

import com.logistics.common.model.OrderStatus;
import com.logistics.order.exception.InvalidStateTransitionException;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Validates state transitions in the Order lifecycle state machine.
 *
 * Lifecycle flow:
 * PENDING    -> CONFIRMED, CANCELLED
 * CONFIRMED  -> ASSIGNED, CANCELLED
 * ASSIGNED   -> IN_TRANSIT, CONFIRMED (unassign/re-dispatch), CANCELLED
 * IN_TRANSIT -> DELIVERED, CANCELLED
 * DELIVERED  -> [Terminal state, no transitions allowed]
 * CANCELLED  -> [Terminal state, no transitions allowed]
 */
@ApplicationScoped
public class OrderLifecycleValidator {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS;

    static {
        Map<OrderStatus, Set<OrderStatus>> map = new EnumMap<>(OrderStatus.class);

        // PENDING -> CONFIRMED, CANCELLED
        map.put(OrderStatus.PENDING, Collections.unmodifiableSet(EnumSet.of(
                OrderStatus.CONFIRMED,
                OrderStatus.CANCELLED
        )));

        // CONFIRMED -> ASSIGNED, CANCELLED
        map.put(OrderStatus.CONFIRMED, Collections.unmodifiableSet(EnumSet.of(
                OrderStatus.ASSIGNED,
                OrderStatus.CANCELLED
        )));

        // ASSIGNED -> IN_TRANSIT, CONFIRMED (unassign / re-dispatch), CANCELLED
        map.put(OrderStatus.ASSIGNED, Collections.unmodifiableSet(EnumSet.of(
                OrderStatus.IN_TRANSIT,
                OrderStatus.CONFIRMED,
                OrderStatus.CANCELLED
        )));

        // IN_TRANSIT -> DELIVERED, CANCELLED
        map.put(OrderStatus.IN_TRANSIT, Collections.unmodifiableSet(EnumSet.of(
                OrderStatus.DELIVERED,
                OrderStatus.CANCELLED
        )));

        // DELIVERED -> Terminal state
        map.put(OrderStatus.DELIVERED, Collections.emptySet());

        // CANCELLED -> Terminal state
        map.put(OrderStatus.CANCELLED, Collections.emptySet());

        ALLOWED_TRANSITIONS = Collections.unmodifiableMap(map);
    }

    /**
     * Checks if a transition from one status to another is valid.
     */
    public boolean isValidTransition(OrderStatus from, OrderStatus to) {
        if (from == null || to == null) {
            return false;
        }
        if (from == to) {
            return false;
        }
        Set<OrderStatus> nextValidStatuses = ALLOWED_TRANSITIONS.get(from);
        return nextValidStatuses != null && nextValidStatuses.contains(to);
    }

    /**
     * Validates a transition from one status to another, throwing {@link InvalidStateTransitionException}
     * with detailed explanation if invalid.
     */
    public void validateTransition(OrderStatus from, OrderStatus to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Current status and target status must not be null");
        }
        if (from == to) {
            throw new InvalidStateTransitionException(from, to,
                    "Order is already in status " + from.name());
        }
        if (isTerminal(from)) {
            throw new InvalidStateTransitionException(from, to,
                    "Order in terminal status '" + from.name() + "' cannot transition to any new status");
        }
        if (!isValidTransition(from, to)) {
            Set<OrderStatus> allowed = getAllowedNextStatuses(from);
            String allowedStr = allowed.isEmpty() ? "none (terminal state)" : allowed.toString();
            throw new InvalidStateTransitionException(from, to,
                    String.format("Transition not permitted. Allowed next statuses from '%s': %s",
                            from.name(), allowedStr));
        }
    }

    /**
     * Returns the set of valid next statuses for a given status.
     */
    public Set<OrderStatus> getAllowedNextStatuses(OrderStatus current) {
        if (current == null) {
            return Collections.emptySet();
        }
        return ALLOWED_TRANSITIONS.getOrDefault(current, Collections.emptySet());
    }

    /**
     * Checks if a status is a terminal state (cannot transition further).
     */
    public boolean isTerminal(OrderStatus status) {
        if (status == null) {
            return false;
        }
        return status == OrderStatus.DELIVERED || status == OrderStatus.CANCELLED;
    }

    /**
     * Returns the mandatory initial status for newly created orders.
     */
    public OrderStatus getInitialStatus() {
        return OrderStatus.PENDING;
    }
}
