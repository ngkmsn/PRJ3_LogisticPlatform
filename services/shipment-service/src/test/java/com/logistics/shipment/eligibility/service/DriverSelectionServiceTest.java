package com.logistics.shipment.eligibility.service;

import com.logistics.common.model.DriverAvailability;
import com.logistics.shipment.eligibility.model.AssignmentCriteria;
import com.logistics.shipment.eligibility.model.EligibleDriver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DriverSelectionServiceTest {

    @Mock
    DriverEligibilityService eligibilityService;

    private DefaultDriverSelectionService selectionService;

    private AssignmentCriteria criteria;

    @BeforeEach
    void setUp() {
        selectionService = new DefaultDriverSelectionService(eligibilityService, 2.0, 15.0, 50.0);

        criteria = AssignmentCriteria.builder()
                .orderId("ord-100")
                .orderNumber("ORD-100")
                .pickupCoordinates(10.7769, 106.7009) // District 1, HCMC
                .deliveryAddress("District 7, HCMC")
                .serviceType("EXPRESS")
                .totalAmount(BigDecimal.valueOf(150000))
                .build();
    }

    private EligibleDriver createDriver(String driverId, String name, double initialScore,
                                        Double distanceKm, int workload) {
        EligibleDriver driver = new EligibleDriver(
                driverId, "usr-" + driverId, name, "0900000001", "VAN", "59A-12345",
                DriverAvailability.AVAILABLE, "ACTIVE"
        );
        driver.setScore(initialScore);
        driver.setDistanceKm(distanceKm);
        driver.setCurrentWorkload(workload);
        return driver;
    }

    @Test
    @DisplayName("Select best driver from multiple candidates: driver with highest composite score is selected")
    void testSelectBestDriver_amongMultipleCandidates() {
        // Driver 1: Workload score = 120 (idle), distance = 5.0 km -> Composite = 120 - (5.0 * 2) = 110.0
        EligibleDriver driver1 = createDriver("drv-1", "Nguyen Van An", 120.0, 5.0, 0);

        // Driver 2: Workload score = 100 (normal), distance = 2.0 km -> Composite = 100 - (2.0 * 2) = 96.0
        EligibleDriver driver2 = createDriver("drv-2", "Le Van Binh", 100.0, 2.0, 1);

        // Driver 3: Workload score = 70 (soft limit), distance = 1.0 km -> Composite = 70 - (1.0 * 2) = 68.0
        EligibleDriver driver3 = createDriver("drv-3", "Tran Van Cuong", 70.0, 1.0, 2);

        when(eligibilityService.findEligibleDrivers(criteria))
                .thenReturn(Arrays.asList(driver1, driver2, driver3));

        Optional<EligibleDriver> selected = selectionService.selectBestDriver(criteria);

        assertThat(selected).isPresent();
        assertThat(selected.get().getDriverId()).isEqualTo("drv-1");
        assertThat(selected.get().getScore()).isEqualTo(110.0);
    }

    @Test
    @DisplayName("Driver closer to pickup wins if workload scores are identical")
    void testProximityWinsWhenWorkloadIsIdentical() {
        // Both idle (score 120)
        EligibleDriver driverFar = createDriver("drv-far", "Driver Far", 120.0, 10.0, 0); // 120 - 20 = 100
        EligibleDriver driverClose = createDriver("drv-close", "Driver Close", 120.0, 2.0, 0); // 120 - 4 = 116

        when(eligibilityService.findEligibleDrivers(criteria))
                .thenReturn(Arrays.asList(driverFar, driverClose));

        Optional<EligibleDriver> selected = selectionService.selectBestDriver(criteria);

        assertThat(selected).isPresent();
        assertThat(selected.get().getDriverId()).isEqualTo("drv-close");
        assertThat(selected.get().getScore()).isEqualTo(116.0);
    }

    @Test
    @DisplayName("Driver exceeding hard limit is already filtered out by pipeline, never selected even if 0.1km from pickup")
    void testOverloadedDriver_neverSelectedEvenIfClosest() {
        // Driver 1: Closest (0.1km) but was excluded by eligibility pipeline (workload > hardLimit)
        // Pipeline only returns surviving eligible drivers:
        EligibleDriver survivingDriver = createDriver("drv-eligible", "Eligible Driver", 100.0, 4.0, 1); // 100 - 8 = 92

        when(eligibilityService.findEligibleDrivers(criteria))
                .thenReturn(Collections.singletonList(survivingDriver));

        Optional<EligibleDriver> selected = selectionService.selectBestDriver(criteria);

        assertThat(selected).isPresent();
        assertThat(selected.get().getDriverId()).isEqualTo("drv-eligible");
    }

    @Test
    @DisplayName("Deterministic Tie-Breaker Level 1: Proximity breaks tie when scores are equal")
    void testTieBreaker_distanceBreaksTie() {
        // Manually crafted equal score
        EligibleDriver driver1 = createDriver("drv-A", "Driver A", 104.0, 2.0, 1); // 104 - 4 = 100.0
        EligibleDriver driver2 = createDriver("drv-B", "Driver B", 110.0, 5.0, 1); // 110 - 10 = 100.0

        when(eligibilityService.findEligibleDrivers(criteria))
                .thenReturn(Arrays.asList(driver2, driver1));

        List<EligibleDriver> ranked = selectionService.rankEligibleDrivers(criteria);

        assertThat(ranked).hasSize(2);
        // Both have score 100.0, driver1 has distance 2.0 vs driver2 distance 5.0 -> driver1 wins
        assertThat(ranked.get(0).getDriverId()).isEqualTo("drv-A");
        assertThat(ranked.get(1).getDriverId()).isEqualTo("drv-B");
    }

    @Test
    @DisplayName("Deterministic Tie-Breaker Level 2: Workload breaks tie when scores and distances are equal")
    void testTieBreaker_workloadBreaksTie() {
        // Equal scores and equal distances
        EligibleDriver busyDriver = createDriver("drv-busy", "Busy Driver", 100.0, 3.0, 2);
        EligibleDriver idleDriver = createDriver("drv-idle", "Idle Driver", 100.0, 3.0, 0);

        when(eligibilityService.findEligibleDrivers(criteria))
                .thenReturn(Arrays.asList(busyDriver, idleDriver));

        List<EligibleDriver> ranked = selectionService.rankEligibleDrivers(criteria);

        assertThat(ranked).hasSize(2);
        // Lower workload (0 vs 2) wins
        assertThat(ranked.get(0).getDriverId()).isEqualTo("drv-idle");
        assertThat(ranked.get(1).getDriverId()).isEqualTo("drv-busy");
    }

    @Test
    @DisplayName("Deterministic Tie-Breaker Level 3: Natural driverId order breaks tie when scores, distances, and workloads are equal")
    void testTieBreaker_alphabeticalDriverIdFallback() {
        EligibleDriver driverZ = createDriver("drv-Z", "Driver Z", 100.0, 3.0, 1);
        EligibleDriver driverA = createDriver("drv-A", "Driver A", 100.0, 3.0, 1);
        EligibleDriver driverM = createDriver("drv-M", "Driver M", 100.0, 3.0, 1);

        when(eligibilityService.findEligibleDrivers(criteria))
                .thenReturn(Arrays.asList(driverZ, driverA, driverM));

        List<EligibleDriver> ranked = selectionService.rankEligibleDrivers(criteria);

        assertThat(ranked).hasSize(3);
        assertThat(ranked.get(0).getDriverId()).isEqualTo("drv-A");
        assertThat(ranked.get(1).getDriverId()).isEqualTo("drv-M");
        assertThat(ranked.get(2).getDriverId()).isEqualTo("drv-Z");
    }

    @Test
    @DisplayName("Missing location applies penalty and ranks driver behind valid location drivers")
    void testMissingLocation_appliesPenaltyAndRanksLower() {
        // Driver 1: has valid GPS (dist = 3.0km) -> 100 - 6 = 94.0
        EligibleDriver driverWithGps = createDriver("drv-gps", "Has GPS", 100.0, 3.0, 1);

        // Driver 2: missing GPS (dist = null) -> 100 - missingPenalty(50.0) = 50.0
        EligibleDriver driverNoGps = createDriver("drv-nogps", "No GPS", 100.0, null, 1);

        when(eligibilityService.findEligibleDrivers(criteria))
                .thenReturn(Arrays.asList(driverNoGps, driverWithGps));

        List<EligibleDriver> ranked = selectionService.rankEligibleDrivers(criteria);

        assertThat(ranked.get(0).getDriverId()).isEqualTo("drv-gps");
        assertThat(ranked.get(1).getDriverId()).isEqualTo("drv-nogps");
        assertThat(ranked.get(1).getScore()).isEqualTo(50.0);
        assertThat(ranked.get(1).getMetadata()).containsKey("missingLocationPenaltyApplied");
    }

    @Test
    @DisplayName("Stale GPS location incurs stale penalty")
    void testStaleLocation_incursStalePenalty() {
        EligibleDriver freshDriver = createDriver("drv-fresh", "Fresh GPS", 100.0, 5.0, 1); // 100 - 10 = 90.0

        EligibleDriver staleDriver = createDriver("drv-stale", "Stale GPS", 100.0, 5.0, 1);
        staleDriver.addMetadata("locationStale", true); // 100 - 10 - 15 = 75.0

        when(eligibilityService.findEligibleDrivers(criteria))
                .thenReturn(Arrays.asList(staleDriver, freshDriver));

        List<EligibleDriver> ranked = selectionService.rankEligibleDrivers(criteria);

        assertThat(ranked.get(0).getDriverId()).isEqualTo("drv-fresh");
        assertThat(ranked.get(1).getDriverId()).isEqualTo("drv-stale");
        assertThat(ranked.get(1).getScore()).isEqualTo(75.0);
    }

    @Test
    @DisplayName("Returns Optional.empty() when candidate list from pipeline is empty")
    void testEmptyCandidateList_returnsEmptyOptional() {
        when(eligibilityService.findEligibleDrivers(criteria)).thenReturn(Collections.emptyList());

        Optional<EligibleDriver> result = selectionService.selectBestDriver(criteria);
        assertThat(result).isEmpty();

        List<EligibleDriver> ranked = selectionService.rankEligibleDrivers(criteria);
        assertThat(ranked).isEmpty();
    }

    @Test
    @DisplayName("Returns Optional.empty() when criteria is null or has empty orderId")
    void testInvalidCriteria_returnsEmpty() {
        Optional<EligibleDriver> res1 = selectionService.selectBestDriver(null);
        assertThat(res1).isEmpty();

        AssignmentCriteria emptyCriteria = new AssignmentCriteria();
        Optional<EligibleDriver> res2 = selectionService.selectBestDriver(emptyCriteria);
        assertThat(res2).isEmpty();
    }
}
