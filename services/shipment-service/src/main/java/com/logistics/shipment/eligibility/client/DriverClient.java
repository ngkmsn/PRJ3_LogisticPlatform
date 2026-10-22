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

    /**
     * Finds and validates a driver by their user account identifier.
     *
     * @param userId user account identifier
     * @return Optional containing EligibleDriver if found and active, empty otherwise
     */
    default Optional<EligibleDriver> findDriverByUserId(String userId) {
        return findDriverById(userId);
    }

    /**
     * Retrieves all drivers from authoritative User Service for Operations monitoring (PB-046).
     *
     * @param authHeader authorization header with Bearer token
     * @return list of drivers, never null
     */
    default List<EligibleDriver> getAllDrivers(String authHeader) {
        return List.of();
    }

    /**
     * Retrieves aggregated driver operational metrics from authoritative User Service (PB-048).
     *
     * @param authHeader authorization header with Bearer token
     * @return Optional containing DriverOperationalMetricsDto if available, empty otherwise
     */
    default java.util.Optional<com.logistics.common.dto.DriverOperationalMetricsDto> getDriverMetrics(String authHeader) {
        return java.util.Optional.empty();
    }
}
