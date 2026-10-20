package com.logistics.shipment.eligibility.pipeline;

import com.logistics.shipment.eligibility.model.AssignmentCriteria;
import com.logistics.shipment.eligibility.model.EligibleDriver;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Baseline filter stage that validates candidate integrity:
 * verifies driverId is non-null/non-empty and that operational status is ACTIVE.
 */
@ApplicationScoped
public class BaseEligibilityFilter implements DriverEligibilityFilter {

    private static final Logger log = LoggerFactory.getLogger(BaseEligibilityFilter.class);

    @Override
    public List<EligibleDriver> filter(List<EligibleDriver> candidates, AssignmentCriteria criteria) {
        if (candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }

        return candidates.stream()
                .filter(driver -> {
                    if (driver == null || driver.getDriverId() == null || driver.getDriverId().trim().isEmpty()) {
                        log.debug("BaseEligibilityFilter: rejected candidate with null/empty driverId");
                        return false;
                    }
                    if (driver.getStatus() != null && !"ACTIVE".equalsIgnoreCase(driver.getStatus().trim())) {
                        log.debug("BaseEligibilityFilter: rejected candidate '{}' with non-ACTIVE status '{}'",
                                driver.getDriverId(), driver.getStatus());
                        return false;
                    }
                    return true;
                })
                .collect(Collectors.toList());
    }

    @Override
    public int getOrder() {
        return 0; // Baseline validation runs first
    }
}
