package com.logistics.shipment.eligibility.pipeline;

import com.logistics.shipment.eligibility.model.AssignmentCriteria;
import com.logistics.shipment.eligibility.model.EligibleDriver;
import com.logistics.shipment.repository.AssignmentRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Stage 3 in Driver Eligibility Pipeline (PB-028).
 * Evaluates current driver workload, enforces hard limits, applies soft limit penalties,
 * rewards idle drivers, and prioritizes candidates with optimal workloads.
 *
 * Rules:
 * 1. Active orders workload calculation:
 *    - Reuses driver.currentWorkload if present.
 *    - If null, queries active assignments (status = ASSIGNED, excluding CANCELLED and FAILED) from AssignmentRepository.
 *    - If incomplete/unresolvable, safely defaults to 0.
 * 2. Data validity:
 *    - Rejects invalid negative workloads (< 0).
 * 3. Hard Limit:
 *    - Rejects drivers exceeding hard limit (workload > hardLimit, or workload >= hardLimit if rejectAtHardLimit is enabled).
 * 4. Soft Limit & Priority Penalty:
 *    - Drivers approaching or touching soft limit (softLimit <= workload <= hardLimit) are kept in candidate pool
 *      but have their score/priority penalized.
 * 5. Idle Drivers:
 *    - Drivers with 0 active orders receive an idle bonus to prioritize them for new assignments.
 * 6. Ordering:
 *    - Candidates are sorted in descending order of score, ensuring lowest workload / highest priority drivers are selected first.
 */
@ApplicationScoped
public class DriverWorkloadFilter implements DriverEligibilityFilter {

    private static final Logger log = LoggerFactory.getLogger(DriverWorkloadFilter.class);

    public static final int DEFAULT_HARD_LIMIT = 3;
    public static final int DEFAULT_SOFT_LIMIT = 2;
    public static final double DEFAULT_SOFT_LIMIT_PENALTY = 30.0;
    public static final double DEFAULT_HARD_LIMIT_PENALTY = 60.0;
    public static final double DEFAULT_IDLE_BONUS = 20.0;

    @ConfigProperty(name = "logistics.assignment.driver.workload.hard-limit", defaultValue = "3")
    int hardLimit = DEFAULT_HARD_LIMIT;

    @ConfigProperty(name = "logistics.assignment.driver.workload.soft-limit", defaultValue = "2")
    int softLimit = DEFAULT_SOFT_LIMIT;

    @ConfigProperty(name = "logistics.assignment.driver.workload.soft-limit-penalty", defaultValue = "30.0")
    double softLimitPenalty = DEFAULT_SOFT_LIMIT_PENALTY;

    @ConfigProperty(name = "logistics.assignment.driver.workload.hard-limit-penalty", defaultValue = "60.0")
    double hardLimitPenalty = DEFAULT_HARD_LIMIT_PENALTY;

    @ConfigProperty(name = "logistics.assignment.driver.workload.idle-bonus", defaultValue = "20.0")
    double idleBonus = DEFAULT_IDLE_BONUS;

    @ConfigProperty(name = "logistics.assignment.driver.workload.reject-at-hard-limit", defaultValue = "false")
    boolean rejectAtHardLimit = false;

    @Inject
    AssignmentRepository assignmentRepository;

    public DriverWorkloadFilter() {
    }

    public DriverWorkloadFilter(int hardLimit, int softLimit) {
        this.hardLimit = hardLimit;
        this.softLimit = softLimit;
    }

    public DriverWorkloadFilter(AssignmentRepository assignmentRepository, int hardLimit, int softLimit) {
        this.assignmentRepository = assignmentRepository;
        this.hardLimit = hardLimit;
        this.softLimit = softLimit;
    }

    public DriverWorkloadFilter(AssignmentRepository assignmentRepository, int hardLimit, int softLimit, boolean rejectAtHardLimit) {
        this.assignmentRepository = assignmentRepository;
        this.hardLimit = hardLimit;
        this.softLimit = softLimit;
        this.rejectAtHardLimit = rejectAtHardLimit;
    }

    @Override
    public List<EligibleDriver> filter(List<EligibleDriver> candidates, AssignmentCriteria criteria) {
        if (candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }

        String orderId = criteria != null ? criteria.getOrderId() : "N/A";
        List<EligibleDriver> surviving = new ArrayList<>();

        for (EligibleDriver driver : candidates) {
            if (driver == null || driver.getDriverId() == null || driver.getDriverId().trim().isEmpty()) {
                log.debug("DriverWorkloadFilter: skipped null or unidentifiable driver candidate for order '{}'", orderId);
                continue;
            }

            if (evaluateDriverWorkload(driver, orderId)) {
                surviving.add(driver);
            }
        }

        // Prioritize candidates: sort surviving drivers by score descending (lowest workload / highest priority first)
        surviving.sort(Comparator.comparingDouble(EligibleDriver::getScore).reversed());

        log.info("DriverWorkloadFilter: evaluated {} candidates for order '{}'; {} drivers remain after workload filtering",
                candidates.size(), orderId, surviving.size());

        return surviving;
    }

    private boolean evaluateDriverWorkload(EligibleDriver driver, String orderId) {
        String driverId = driver.getDriverId();

        // 1. Resolve and calculate current active workload
        Integer workload = driver.getCurrentWorkload();
        if (workload == null) {
            if (assignmentRepository != null) {
                try {
                    long activeCount = assignmentRepository.countActiveAssignmentsByDriverId(driverId);
                    workload = (int) activeCount;
                    driver.setCurrentWorkload(workload);
                    log.debug("DriverWorkloadFilter: resolved active workload from DB for driver '{}': {} orders",
                            driverId, workload);
                } catch (Exception e) {
                    log.warn("DriverWorkloadFilter: failed to query active assignments for driver '{}': {}. Defaulting workload to 0.",
                            driverId, e.getMessage());
                    workload = 0;
                    driver.setCurrentWorkload(0);
                }
            } else {
                // Incomplete workload data without DB fallback: safely default to 0
                log.debug("DriverWorkloadFilter: incomplete workload data for driver '{}', defaulting to 0", driverId);
                workload = 0;
                driver.setCurrentWorkload(0);
            }
        }

        // 2. Validate workload data integrity
        if (workload < 0) {
            log.warn("DriverWorkloadFilter: rejected driver '{}' for order '{}' due to invalid negative workload ({})",
                    driverId, orderId, workload);
            return false;
        }

        // 3. Resolve effective limits (respecting individual driver metadata overrides if present)
        int effectiveHardLimit = resolveHardLimit(driver);
        int effectiveSoftLimit = resolveSoftLimit(driver, effectiveHardLimit);

        // 4. Hard Limit Evaluation
        if (workload > effectiveHardLimit) {
            log.info("DriverWorkloadFilter: rejected driver '{}' for order '{}': exceeded hard workload limit (current: {}, hardLimit: {})",
                    driverId, orderId, workload, effectiveHardLimit);
            return false;
        }

        if (rejectAtHardLimit && workload >= effectiveHardLimit) {
            log.info("DriverWorkloadFilter: rejected driver '{}' for order '{}': reached hard workload limit with strict rejection enabled (current: {}, hardLimit: {})",
                    driverId, orderId, workload, effectiveHardLimit);
            return false;
        }

        // 5. Soft Limit & Priority Evaluation
        double initialScore = driver.getScore();

        if (workload == 0) {
            // Driver has NO active orders: awarded idle bonus for highest dispatching priority
            double newScore = initialScore + idleBonus;
            driver.setScore(newScore);
            driver.addMetadata("workloadStatus", "IDLE");
            driver.addMetadata("idleBonusApplied", true);
            driver.addMetadata("workloadBonus", idleBonus);
            log.debug("DriverWorkloadFilter: driver '{}' is idle (workload 0). Awarded idle bonus +{}, score: {} -> {}",
                    driverId, idleBonus, initialScore, newScore);

        } else if (workload < effectiveSoftLimit) {
            // Driver workload is well within limits: normal priority, no penalty
            driver.addMetadata("workloadStatus", "NORMAL");
            log.debug("DriverWorkloadFilter: driver '{}' has normal workload ({}/{}). No penalty applied.",
                    driverId, workload, effectiveHardLimit);

        } else if (workload == effectiveSoftLimit) {
            // Driver reached soft limit threshold: kept in candidate pool, but priority is reduced
            double newScore = initialScore - softLimitPenalty;
            driver.setScore(newScore);
            driver.addMetadata("workloadStatus", "SOFT_LIMIT_REACHED");
            driver.addMetadata("softLimitReached", true);
            driver.addMetadata("softLimitPenalty", softLimitPenalty);
            log.info("DriverWorkloadFilter: driver '{}' reached soft limit ({}/{}). Applied penalty -{}, score: {} -> {}",
                    driverId, workload, effectiveHardLimit, softLimitPenalty, initialScore, newScore);

        } else if (workload > effectiveSoftLimit && workload < effectiveHardLimit) {
            // Driver approaching hard limit: kept in candidate pool, higher penalty
            double penalty = softLimitPenalty * 1.5;
            double newScore = initialScore - penalty;
            driver.setScore(newScore);
            driver.addMetadata("workloadStatus", "SOFT_LIMIT_EXCEEDED");
            driver.addMetadata("softLimitReached", true);
            driver.addMetadata("softLimitPenalty", penalty);
            log.info("DriverWorkloadFilter: driver '{}' approaching hard limit ({}/{}). Applied penalty -{}, score: {} -> {}",
                    driverId, workload, effectiveHardLimit, penalty, initialScore, newScore);

        } else {
            // Driver at hard limit (workload == effectiveHardLimit when rejectAtHardLimit is false)
            // Kept in candidate pool as last resort, heavily penalized
            double newScore = initialScore - hardLimitPenalty;
            driver.setScore(newScore);
            driver.addMetadata("workloadStatus", "HARD_LIMIT_REACHED");
            driver.addMetadata("hardLimitReached", true);
            driver.addMetadata("hardLimitPenalty", hardLimitPenalty);
            log.info("DriverWorkloadFilter: driver '{}' at hard limit threshold ({}/{}). Applied heavy penalty -{}, score: {} -> {}",
                    driverId, workload, effectiveHardLimit, hardLimitPenalty, initialScore, newScore);
        }

        return true;
    }

    private int resolveHardLimit(EligibleDriver driver) {
        if (driver.getMetadata() != null) {
            Object customLimit = driver.getMetadata().get("hardLimit");
            if (customLimit == null) {
                customLimit = driver.getMetadata().get("maxOrders");
            }
            if (customLimit == null) {
                customLimit = driver.getMetadata().get("capacity");
            }
            if (customLimit instanceof Number n) {
                return n.intValue();
            }
            if (customLimit instanceof String s) {
                try {
                    return Integer.parseInt(s.trim());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return hardLimit > 0 ? hardLimit : DEFAULT_HARD_LIMIT;
    }

    private int resolveSoftLimit(EligibleDriver driver, int effectiveHardLimit) {
        if (driver.getMetadata() != null) {
            Object customLimit = driver.getMetadata().get("softLimit");
            if (customLimit instanceof Number n) {
                return Math.min(n.intValue(), effectiveHardLimit);
            }
            if (customLimit instanceof String s) {
                try {
                    return Math.min(Integer.parseInt(s.trim()), effectiveHardLimit);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        int resolved = softLimit > 0 ? softLimit : DEFAULT_SOFT_LIMIT;
        return Math.min(resolved, effectiveHardLimit);
    }

    @Override
    public int getOrder() {
        return 20; // Stage 3: runs after BaseEligibilityFilter (0) and DriverAvailabilityFilter (10)
    }

    @Override
    public String getName() {
        return "DriverWorkloadFilter";
    }

    // Getters and setters for test configuration
    public int getHardLimit() {
        return hardLimit;
    }

    public void setHardLimit(int hardLimit) {
        this.hardLimit = hardLimit;
    }

    public int getSoftLimit() {
        return softLimit;
    }

    public void setSoftLimit(int softLimit) {
        this.softLimit = softLimit;
    }

    public double getSoftLimitPenalty() {
        return softLimitPenalty;
    }

    public void setSoftLimitPenalty(double softLimitPenalty) {
        this.softLimitPenalty = softLimitPenalty;
    }

    public double getHardLimitPenalty() {
        return hardLimitPenalty;
    }

    public void setHardLimitPenalty(double hardLimitPenalty) {
        this.hardLimitPenalty = hardLimitPenalty;
    }

    public double getIdleBonus() {
        return idleBonus;
    }

    public void setIdleBonus(double idleBonus) {
        this.idleBonus = idleBonus;
    }

    public boolean isRejectAtHardLimit() {
        return rejectAtHardLimit;
    }

    public void setRejectAtHardLimit(boolean rejectAtHardLimit) {
        this.rejectAtHardLimit = rejectAtHardLimit;
    }
}
