package com.logistics.shipment.eligibility.service;

import com.logistics.shipment.eligibility.model.AssignmentCriteria;
import com.logistics.shipment.eligibility.model.EligibleDriver;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Default implementation of {@link DriverSelectionService} (PB-030).
 *
 * Integrates:
 * 1. Eligibility evaluation (PB-026 Candidate Retrieval, PB-027 Availability, PB-028 Workload, PB-029 Proximity).
 * 2. Multi-factor composite scoring combining workload and proximity.
 * 3. Deterministic tie-breaker rules (distance -> workload -> alphabetical driverId).
 * 4. Graceful handling when no drivers are eligible.
 */
@ApplicationScoped
@DefaultBean
public class DefaultDriverSelectionService implements DriverSelectionService {

    private static final Logger log = LoggerFactory.getLogger(DefaultDriverSelectionService.class);

    public static final double DEFAULT_DISTANCE_PENALTY_PER_KM = 2.0;
    public static final double DEFAULT_STALE_LOCATION_PENALTY = 15.0;
    public static final double DEFAULT_MISSING_LOCATION_PENALTY = 50.0;
    public static final double SCORE_EPSILON = 1e-4;

    @ConfigProperty(name = "logistics.assignment.scoring.distance-penalty-per-km", defaultValue = "2.0")
    double distancePenaltyPerKm = DEFAULT_DISTANCE_PENALTY_PER_KM;

    @ConfigProperty(name = "logistics.assignment.scoring.stale-location-penalty", defaultValue = "15.0")
    double staleLocationPenalty = DEFAULT_STALE_LOCATION_PENALTY;

    @ConfigProperty(name = "logistics.assignment.scoring.missing-location-penalty", defaultValue = "50.0")
    double missingLocationPenalty = DEFAULT_MISSING_LOCATION_PENALTY;

    @Inject
    DriverEligibilityService driverEligibilityService;

    public DefaultDriverSelectionService() {
    }

    public DefaultDriverSelectionService(DriverEligibilityService driverEligibilityService) {
        this.driverEligibilityService = driverEligibilityService;
    }

    public DefaultDriverSelectionService(DriverEligibilityService driverEligibilityService,
                                         double distancePenaltyPerKm,
                                         double staleLocationPenalty,
                                         double missingLocationPenalty) {
        this.driverEligibilityService = driverEligibilityService;
        this.distancePenaltyPerKm = distancePenaltyPerKm;
        this.staleLocationPenalty = staleLocationPenalty;
        this.missingLocationPenalty = missingLocationPenalty;
    }

    @Override
    public Optional<EligibleDriver> selectBestDriver(AssignmentCriteria criteria) {
        List<EligibleDriver> ranked = rankEligibleDrivers(criteria);
        if (ranked.isEmpty()) {
            log.info("No eligible drivers available for selection for orderId='{}'",
                    criteria != null ? criteria.getOrderId() : "N/A");
            return Optional.empty();
        }

        EligibleDriver bestDriver = ranked.get(0);
        log.info("Selected best driver '{}' (fullName='{}', score={}, distanceKm={}) for orderId='{}'",
                bestDriver.getDriverId(), bestDriver.getFullName(), bestDriver.getScore(),
                bestDriver.getDistanceKm(), criteria != null ? criteria.getOrderId() : "N/A");

        return Optional.of(bestDriver);
    }

    @Override
    public List<EligibleDriver> rankEligibleDrivers(AssignmentCriteria criteria) {
        if (criteria == null || criteria.getOrderId() == null || criteria.getOrderId().trim().isEmpty()) {
            log.warn("Invalid assignment criteria for ranking: null or empty orderId.");
            return Collections.emptyList();
        }

        // 1. Run eligibility pipeline (PB-026 Candidate Retrieval, PB-027 Availability, PB-028 Workload, PB-029 Proximity)
        List<EligibleDriver> eligibleCandidates = driverEligibilityService.findEligibleDrivers(criteria);
        if (eligibleCandidates == null || eligibleCandidates.isEmpty()) {
            log.info("Zero candidate drivers qualified through eligibility pipeline for orderId='{}'", criteria.getOrderId());
            return Collections.emptyList();
        }

        List<EligibleDriver> rankedList = new ArrayList<>(eligibleCandidates.size());

        // 2. Compute composite ranking score for each candidate
        for (EligibleDriver driver : eligibleCandidates) {
            if (driver == null || driver.getDriverId() == null) {
                continue;
            }
            double finalScore = calculateRankScore(driver, criteria);
            driver.setScore(finalScore);
            rankedList.add(driver);
        }

        // 3. Sort candidates using deterministic multi-level comparator
        rankedList.sort(createDriverRankingComparator());

        log.info("Successfully ranked {} eligible drivers for orderId='{}'", rankedList.size(), criteria.getOrderId());
        return rankedList;
    }

    @Override
    public double calculateRankScore(EligibleDriver driver, AssignmentCriteria criteria) {
        // Base score initialized from PB-028 WorkloadFilter (reflects workload bonuses and penalties)
        double score = driver.getScore();

        Double distanceKm = driver.getDistanceKm();
        if (distanceKm != null) {
            // Apply linear distance penalty
            double distancePenalty = distanceKm * distancePenaltyPerKm;
            score -= distancePenalty;
            driver.addMetadata("distancePenalty", distancePenalty);

            // Apply stale location penalty if GPS coordinates are outdated
            if (Boolean.TRUE.equals(driver.getMetadata().get("locationStale"))) {
                score -= staleLocationPenalty;
                driver.addMetadata("staleLocationPenaltyApplied", staleLocationPenalty);
            }
        } else {
            // Driver is missing valid GPS coordinates: apply missing location penalty
            score -= missingLocationPenalty;
            driver.addMetadata("missingLocationPenaltyApplied", missingLocationPenalty);
        }

        driver.addMetadata("compositeRankScore", score);
        return score;
    }

    /**
     * Constructs a stable, deterministic comparator for ranking candidate drivers:
     * 1. Primary: Composite Score descending (highest score first).
     * 2. Tie-Breaker 1: Distance ascending (closest pickup distance first).
     * 3. Tie-Breaker 2: Workload ascending (least loaded driver first).
     * 4. Tie-Breaker 3: DriverId natural alphabetical order (deterministic fallback).
     */
    private Comparator<EligibleDriver> createDriverRankingComparator() {
        return (d1, d2) -> {
            // Level 1: Composite Score (Descending)
            double diff = d1.getScore() - d2.getScore();
            if (Math.abs(diff) > SCORE_EPSILON) {
                return diff > 0 ? -1 : 1;
            }

            // Level 2: Proximity / Distance (Ascending)
            Double dist1 = d1.getDistanceKm();
            Double dist2 = d2.getDistanceKm();
            if (dist1 != null && dist2 != null) {
                int distCompare = Double.compare(dist1, dist2);
                if (distCompare != 0) {
                    return distCompare;
                }
            } else if (dist1 != null) {
                return -1; // Valid distance prioritized over null
            } else if (dist2 != null) {
                return 1;
            }

            // Level 3: Workload (Ascending)
            int workload1 = d1.getCurrentWorkload() != null ? d1.getCurrentWorkload() : 0;
            int workload2 = d2.getCurrentWorkload() != null ? d2.getCurrentWorkload() : 0;
            int workloadCompare = Integer.compare(workload1, workload2);
            if (workloadCompare != 0) {
                return workloadCompare;
            }

            // Level 4: Alphabetical DriverId (Ascending) for 100% deterministic reproducibility
            String id1 = d1.getDriverId() != null ? d1.getDriverId() : "";
            String id2 = d2.getDriverId() != null ? d2.getDriverId() : "";
            return id1.compareTo(id2);
        };
    }

    // Getters and Setters for configuration and testing
    public double getDistancePenaltyPerKm() {
        return distancePenaltyPerKm;
    }

    public void setDistancePenaltyPerKm(double distancePenaltyPerKm) {
        this.distancePenaltyPerKm = distancePenaltyPerKm;
    }

    public double getStaleLocationPenalty() {
        return staleLocationPenalty;
    }

    public void setStaleLocationPenalty(double staleLocationPenalty) {
        this.staleLocationPenalty = staleLocationPenalty;
    }

    public double getMissingLocationPenalty() {
        return missingLocationPenalty;
    }

    public void setMissingLocationPenalty(double missingLocationPenalty) {
        this.missingLocationPenalty = missingLocationPenalty;
    }
}
