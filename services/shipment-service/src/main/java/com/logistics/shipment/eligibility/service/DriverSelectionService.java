package com.logistics.shipment.eligibility.service;

import com.logistics.shipment.eligibility.model.AssignmentCriteria;
import com.logistics.shipment.eligibility.model.EligibleDriver;

import java.util.List;
import java.util.Optional;

/**
 * Service responsible for ranking eligible candidate drivers and selecting the single
 * best-fit driver for an order assignment (PB-030).
 */
public interface DriverSelectionService {

    /**
     * Selects the single best-fit driver from the surviving eligible candidate pool.
     * Evaluates candidates through the eligibility pipeline, ranks them by composite score,
     * applies deterministic tie-breakers, and returns the top-ranked driver.
     *
     * @param criteria assignment criteria including orderId, pickup coordinates, service type
     * @return Optional containing the best driver, or Optional.empty() if no driver is eligible
     */
    Optional<EligibleDriver> selectBestDriver(AssignmentCriteria criteria);

    /**
     * Evaluates and ranks all eligible drivers in descending order of priority.
     * The first element in the returned list corresponds to the best-fit driver.
     *
     * @param criteria assignment criteria
     * @return ordered list of eligible drivers, or empty list if none match
     */
    List<EligibleDriver> rankEligibleDrivers(AssignmentCriteria criteria);

    /**
     * Calculates the composite ranking score for a candidate driver.
     * Combines workload score, distance penalty, stale location penalty, and missing location penalty.
     *
     * @param driver   candidate driver
     * @param criteria assignment criteria
     * @return composite score (higher is better)
     */
    double calculateRankScore(EligibleDriver driver, AssignmentCriteria criteria);
}
