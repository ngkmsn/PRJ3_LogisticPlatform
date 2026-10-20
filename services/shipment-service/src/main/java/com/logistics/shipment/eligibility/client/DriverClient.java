package com.logistics.shipment.eligibility.client;

import com.logistics.shipment.eligibility.model.AssignmentCriteria;
import com.logistics.shipment.eligibility.model.EligibleDriver;

import java.util.List;
import java.util.Optional;

/**
 * Service boundary interface for querying driver data from external services (e.g. User Service)
 * or local read models/caches. Strictly prevents direct access to other service databases.
 */
public interface DriverClient {

    /**
     * Retrieves potential candidate drivers for the given criteria.
     *
     * @param criteria assignment criteria
     * @return candidate list of drivers, never null
     */
    List<EligibleDriver> getCandidateDrivers(AssignmentCriteria criteria);

    /**
     * Finds and validates a driver by their identifier for manual assignment (PB-035).
     *
     * @param driverId driver identifier
     * @return Optional containing EligibleDriver if found and active, empty otherwise
     */
    Optional<EligibleDriver> findDriverById(String driverId);
}
