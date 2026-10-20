package com.logistics.shipment.eligibility.pipeline;

import com.logistics.shipment.eligibility.model.AssignmentCriteria;
import com.logistics.shipment.eligibility.model.EligibleDriver;
import com.logistics.shipment.eligibility.model.GeoLocation;
import com.logistics.shipment.eligibility.util.GeoDistanceCalculator;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Stage 4 in Driver Eligibility Pipeline (PB-029).
 * Computes geographic proximity (Haversine distance in km) between eligible candidate drivers
 * and the order's pickup location.
 *
 * Rules:
 * 1. Consistent Measurement: All distances are computed in kilometers (km) with 2 decimal precision.
 * 2. Missing/Invalid Location Handling:
 *    - Drivers without valid coordinates are marked with distanceKm = null (never 0.0 to prevent false-proximity bias).
 *    - Orders without valid pickup location log a warning and retain candidates with distanceKm = null.
 * 3. Stale Location Evaluation:
 *    - Locations older than maxAgeMinutes are flagged as stale (locationStale = true) in driver metadata.
 */
@ApplicationScoped
public class DriverProximityFilter implements DriverEligibilityFilter {

    private static final Logger log = LoggerFactory.getLogger(DriverProximityFilter.class);

    public static final int DEFAULT_MAX_AGE_MINUTES = 30;

    @ConfigProperty(name = "logistics.assignment.driver.location.max-age-minutes", defaultValue = "30")
    int maxAgeMinutes = DEFAULT_MAX_AGE_MINUTES;

    public DriverProximityFilter() {
    }

    public DriverProximityFilter(int maxAgeMinutes) {
        this.maxAgeMinutes = maxAgeMinutes;
    }

    @Override
    public List<EligibleDriver> filter(List<EligibleDriver> candidates, AssignmentCriteria criteria) {
        if (candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }

        String orderId = criteria != null ? criteria.getOrderId() : "N/A";
        GeoLocation pickupLocation = criteria != null ? criteria.getPickupGeoLocation() : null;

        if (pickupLocation == null || !pickupLocation.isValid()) {
            log.warn("DriverProximityFilter: no valid pickup coordinates for order '{}' (raw location: '{}'). Proximity calculation skipped.",
                    orderId, criteria != null ? criteria.getPickupLocation() : "null");

            for (EligibleDriver driver : candidates) {
                if (driver != null) {
                    driver.setDistanceKm(null);
                    driver.addMetadata("pickupLocationValid", false);
                    driver.addMetadata("proximityCalculated", false);
                }
            }
            return candidates;
        }

        Instant now = Instant.now();
        log.info("DriverProximityFilter: calculating proximity to pickup ({}, {}) for {} candidates of order '{}'",
                pickupLocation.getLatitude(), pickupLocation.getLongitude(), candidates.size(), orderId);

        for (EligibleDriver driver : candidates) {
            if (driver == null || driver.getDriverId() == null) {
                continue;
            }

            GeoLocation driverLocation = driver.getCurrentGeoLocation();

            if (driverLocation == null || !driverLocation.isValid()) {
                // Driver location is missing or invalid: MUST NOT set to 0.0
                driver.setDistanceKm(null);
                driver.addMetadata("locationValid", false);
                driver.addMetadata("proximityCalculated", false);
                log.debug("DriverProximityFilter: driver '{}' has missing/invalid coordinates. Distance set to null.",
                        driver.getDriverId());
                continue;
            }

            // Check location freshness / staleness
            if (driverLocation.getTimestamp() != null) {
                long ageMinutes = Duration.between(driverLocation.getTimestamp(), now).toMinutes();
                if (ageMinutes > maxAgeMinutes) {
                    driver.addMetadata("locationStale", true);
                    driver.addMetadata("locationAgeMinutes", ageMinutes);
                    log.debug("DriverProximityFilter: driver '{}' location is stale ({} minutes old > max {})",
                            driver.getDriverId(), ageMinutes, maxAgeMinutes);
                } else {
                    driver.addMetadata("locationStale", false);
                    driver.addMetadata("locationAgeMinutes", ageMinutes);
                }
            }

            // Calculate Haversine distance in kilometers
            Double distanceKm = GeoDistanceCalculator.calculateDistanceKm(driverLocation, pickupLocation);
            driver.setDistanceKm(distanceKm);
            driver.addMetadata("locationValid", true);
            driver.addMetadata("proximityCalculated", true);
            driver.addMetadata("distanceUnit", "KM");

            log.debug("DriverProximityFilter: driver '{}' is {} km away from pickup for order '{}'",
                    driver.getDriverId(), distanceKm, orderId);
        }

        return candidates;
    }

    @Override
    public int getOrder() {
        return 30; // Stage 4: runs after BaseEligibilityFilter (0), DriverAvailabilityFilter (10), DriverWorkloadFilter (20)
    }

    @Override
    public String getName() {
        return "DriverProximityFilter";
    }

    public int getMaxAgeMinutes() {
        return maxAgeMinutes;
    }

    public void setMaxAgeMinutes(int maxAgeMinutes) {
        this.maxAgeMinutes = maxAgeMinutes;
    }
}
