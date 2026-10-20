package com.logistics.shipment.eligibility.service;

import com.logistics.shipment.eligibility.model.AssignmentCriteria;
import com.logistics.shipment.eligibility.model.EligibleDriver;

import java.util.List;

/**
 * Service orchestrating driver eligibility evaluation for assignment requests.
 * Produces a filtered candidate pool of drivers matching order requirements.
 */
public interface DriverEligibilityService {

    /**
     * Finds and filters candidate drivers matching the specified assignment criteria.
     * Guaranteed to be fail-safe: returns an empty list if no eligible drivers are found
     * or if a technical failure occurs.
     *
     * @param criteria assignment criteria containing order and location details
     * @return candidate list of eligible drivers (never null)
     */
    List<EligibleDriver> findEligibleDrivers(AssignmentCriteria criteria);
}
