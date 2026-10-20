package com.logistics.shipment.eligibility.pipeline;

import com.logistics.common.model.DriverAvailability;
import com.logistics.shipment.eligibility.model.AssignmentCriteria;
import com.logistics.shipment.eligibility.model.EligibleDriver;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Stage 2 in Driver Eligibility Pipeline (PB-027).
 * Filters out candidates who are offline, inactive, or not ready to accept orders.
 *
 * Rules:
 * 1. Operational status must be non-null and equal to "ACTIVE" (case-insensitive).
 *    (e.g., "INACTIVE", "OFFLINE", "SUSPENDED", "BUSY", "DEACTIVATED" are rejected).
 * 2. Availability state must be non-null and equal to DriverAvailability.AVAILABLE.
 *    (e.g., DriverAvailability.UNAVAILABLE or null are rejected).
 * 3. Metadata readiness flags (if present) must not indicate offline or unready state.
 *    (e.g., "online" == false, "isOnline" == false, "ready" == false, "acceptingOrders" == false are rejected).
 */
@ApplicationScoped
public class DriverAvailabilityFilter implements DriverEligibilityFilter {

    private static final Logger log = LoggerFactory.getLogger(DriverAvailabilityFilter.class);

    public DriverAvailabilityFilter() {
    }

    @Override
    public List<EligibleDriver> filter(List<EligibleDriver> candidates, AssignmentCriteria criteria) {
        if (candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }

        String orderId = criteria != null ? criteria.getOrderId() : "N/A";

        return candidates.stream()
                .filter(driver -> isDriverAvailable(driver, orderId))
                .collect(Collectors.toList());
    }

    private boolean isDriverAvailable(EligibleDriver driver, String orderId) {
        if (driver == null) {
            log.debug("DriverAvailabilityFilter: rejected null driver candidate for order '{}'", orderId);
            return false;
        }

        String driverId = driver.getDriverId();
        if (driverId == null || driverId.trim().isEmpty()) {
            log.debug("DriverAvailabilityFilter: rejected driver with missing or blank driverId for order '{}'", orderId);
            return false;
        }

        // 1. Check Operational Status: must be ACTIVE
        String status = driver.getStatus();
        if (status == null || !"ACTIVE".equalsIgnoreCase(status.trim())) {
            log.info("DriverAvailabilityFilter: rejected driver '{}' for order '{}': operational status is not ACTIVE (was: '{}')",
                    driverId, orderId, status);
            return false;
        }

        // 2. Check Driver Availability enum: must be AVAILABLE
        DriverAvailability availability = driver.getAvailability();
        if (availability != DriverAvailability.AVAILABLE) {
            log.info("DriverAvailabilityFilter: rejected driver '{}' for order '{}': availability is not AVAILABLE (was: '{}')",
                    driverId, orderId, availability);
            return false;
        }

        // 3. Check Metadata readiness flags if present
        Map<String, Object> metadata = driver.getMetadata();
        if (metadata != null) {
            if (isExplicitlyFalse(metadata.get("online")) || isExplicitlyFalse(metadata.get("isOnline"))) {
                log.info("DriverAvailabilityFilter: rejected driver '{}' for order '{}': driver is offline according to metadata",
                        driverId, orderId);
                return false;
            }
            if (isExplicitlyFalse(metadata.get("ready")) || isExplicitlyFalse(metadata.get("isReady"))) {
                log.info("DriverAvailabilityFilter: rejected driver '{}' for order '{}': driver marked not ready in metadata",
                        driverId, orderId);
                return false;
            }
            if (isExplicitlyFalse(metadata.get("acceptingOrders")) || isExplicitlyFalse(metadata.get("isAcceptingOrders"))) {
                log.info("DriverAvailabilityFilter: rejected driver '{}' for order '{}': driver not accepting orders in metadata",
                        driverId, orderId);
                return false;
            }
        }

        log.debug("DriverAvailabilityFilter: driver '{}' passed availability check for order '{}'", driverId, orderId);
        return true;
    }

    private boolean isExplicitlyFalse(Object value) {
        if (value instanceof Boolean b) {
            return !b;
        }
        if (value instanceof String s) {
            return "false".equalsIgnoreCase(s.trim());
        }
        return false;
    }

    @Override
    public int getOrder() {
        return 10; // Runs immediately after baseline validation (order 0)
    }

    @Override
    public String getName() {
        return "DriverAvailabilityFilter";
    }
}
