package com.logistics.shipment.eligibility.pipeline;

import com.logistics.common.model.DriverAvailability;
import com.logistics.shipment.eligibility.model.AssignmentCriteria;
import com.logistics.shipment.eligibility.model.EligibleDriver;
import com.logistics.shipment.repository.AssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DriverWorkloadFilterTest {

    @Mock
    private AssignmentRepository assignmentRepository;

    private DriverWorkloadFilter filter;
    private AssignmentCriteria criteria;

    @BeforeEach
    void setUp() {
        // hardLimit = 3, softLimit = 2
        filter = new DriverWorkloadFilter(assignmentRepository, 3, 2);
        criteria = AssignmentCriteria.builder()
                .orderId("ord-workload-test")
                .orderNumber("ORD-WL-001")
                .pickupLocation("123 Le Loi, District 1, HCMC")
                .deliveryAddress("456 Nguyen Trai, District 5, HCMC")
                .serviceType("EXPRESS")
                .totalAmount(BigDecimal.valueOf(150000))
                .build();
    }

    private EligibleDriver createDriver(String id, String name, Integer workload, double initialScore) {
        EligibleDriver driver = new EligibleDriver(
                id,
                "user-" + id,
                name,
                "0901234567",
                "MOTORCYCLE",
                "59A-12345",
                DriverAvailability.AVAILABLE,
                "ACTIVE"
        );
        driver.setCurrentWorkload(workload);
        driver.setScore(initialScore);
        return driver;
    }

    @Test
    @DisplayName("Empty or null candidate list returns safe empty list without error")
    void filter_emptyOrNullList_returnsEmptyList() {
        assertThat(filter.filter(null, criteria)).isEmpty();
        assertThat(filter.filter(Collections.emptyList(), criteria)).isEmpty();
    }

    @Test
    @DisplayName("Driver with 0 active orders receives idle bonus and highest dispatch priority")
    void filter_driverWithZeroActiveOrders_receivesIdleBonus() {
        EligibleDriver idleDriver = createDriver("d-idle", "Idle Driver", 0, 100.0);

        List<EligibleDriver> result = filter.filter(List.of(idleDriver), criteria);

        assertThat(result).hasSize(1);
        EligibleDriver evaluated = result.get(0);
        // Initial score 100 + default idleBonus 20 = 120
        assertThat(evaluated.getScore()).isEqualTo(120.0);
        assertThat(evaluated.getMetadata()).containsEntry("workloadStatus", "IDLE");
        assertThat(evaluated.getMetadata()).containsEntry("idleBonusApplied", true);
    }

    @Test
    @DisplayName("Driver with workload within normal limits (< softLimit) is retained with normal status")
    void filter_driverWithWorkloadWithinLimits_retainedNormally() {
        // softLimit is 2, workload is 1
        EligibleDriver normalDriver = createDriver("d-normal", "Normal Driver", 1, 100.0);

        List<EligibleDriver> result = filter.filter(List.of(normalDriver), criteria);

        assertThat(result).hasSize(1);
        EligibleDriver evaluated = result.get(0);
        assertThat(evaluated.getScore()).isEqualTo(100.0); // No penalty, no bonus
        assertThat(evaluated.getMetadata()).containsEntry("workloadStatus", "NORMAL");
    }

    @Test
    @DisplayName("Driver reaching exact soft limit threshold is retained but priority is penalized")
    void filter_driverReachingSoftLimit_retainedWithPenalty() {
        // softLimit is 2, workload is exactly 2
        EligibleDriver softLimitDriver = createDriver("d-soft", "Soft Limit Driver", 2, 100.0);

        List<EligibleDriver> result = filter.filter(List.of(softLimitDriver), criteria);

        assertThat(result).hasSize(1);
        EligibleDriver evaluated = result.get(0);
        // Initial score 100 - default softLimitPenalty 30 = 70
        assertThat(evaluated.getScore()).isEqualTo(70.0);
        assertThat(evaluated.getMetadata()).containsEntry("workloadStatus", "SOFT_LIMIT_REACHED");
        assertThat(evaluated.getMetadata()).containsEntry("softLimitReached", true);
        assertThat(evaluated.getMetadata()).containsEntry("softLimitPenalty", 30.0);
    }

    @Test
    @DisplayName("Driver reaching exact hard limit: retained with heavy penalty when rejectAtHardLimit is false")
    void filter_driverAtHardLimit_retainedWithHeavyPenalty_whenNotStrict() {
        // hardLimit is 3, workload is exactly 3 (at limit, not exceeding)
        filter.setRejectAtHardLimit(false);
        EligibleDriver hardLimitDriver = createDriver("d-hard", "Hard Limit Driver", 3, 100.0);

        List<EligibleDriver> result = filter.filter(List.of(hardLimitDriver), criteria);

        assertThat(result).hasSize(1);
        EligibleDriver evaluated = result.get(0);
        // Initial score 100 - default hardLimitPenalty 60 = 40
        assertThat(evaluated.getScore()).isEqualTo(40.0);
        assertThat(evaluated.getMetadata()).containsEntry("workloadStatus", "HARD_LIMIT_REACHED");
    }

    @Test
    @DisplayName("Driver reaching exact hard limit: rejected when strict rejectAtHardLimit is true")
    void filter_driverAtHardLimit_rejected_whenStrict() {
        // hardLimit is 3, workload is exactly 3
        filter.setRejectAtHardLimit(true);
        EligibleDriver hardLimitDriver = createDriver("d-hard", "Hard Limit Driver", 3, 100.0);

        List<EligibleDriver> result = filter.filter(List.of(hardLimitDriver), criteria);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Driver exceeding maximum hard limit (> hardLimit) is strictly rejected")
    void filter_driverExceedingHardLimit_strictlyRejected() {
        // hardLimit is 3, workload is 4 (> 3)
        EligibleDriver overloadedDriver = createDriver("d-over", "Overloaded Driver", 4, 100.0);
        EligibleDriver normalDriver = createDriver("d-norm", "Normal Driver", 1, 100.0);

        List<EligibleDriver> result = filter.filter(List.of(overloadedDriver, normalDriver), criteria);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDriverId()).isEqualTo("d-norm");
    }

    @Test
    @DisplayName("Soft limit priority reduction: candidates are ordered by score descending (optimal workload first)")
    void filter_priorityReduction_ordersCandidatesByScore() {
        // d0: workload 0 -> score 100 + 20 = 120
        EligibleDriver d0 = createDriver("d-0", "Driver Zero", 0, 100.0);
        // d1: workload 1 (< softLimit) -> score 100
        EligibleDriver d1 = createDriver("d-1", "Driver One", 1, 100.0);
        // d2: workload 2 (== softLimit) -> score 100 - 30 = 70
        EligibleDriver d2 = createDriver("d-2", "Driver Two", 2, 100.0);
        // d3: workload 3 (== hardLimit) -> score 100 - 60 = 40
        EligibleDriver d3 = createDriver("d-3", "Driver Three", 3, 100.0);

        List<EligibleDriver> result = filter.filter(List.of(d3, d2, d0, d1), criteria);

        assertThat(result).hasSize(4);
        // Sorted descending by score: d0 (120) -> d1 (100) -> d2 (70) -> d3 (40)
        assertThat(result).extracting(EligibleDriver::getDriverId)
                .containsExactly("d-0", "d-1", "d-2", "d-3");
    }

    @Test
    @DisplayName("Incomplete workload data (null): queries AssignmentRepository, counts active orders and enriches")
    void filter_incompleteWorkloadData_queriesRepositoryAndEnriches() {
        EligibleDriver dNullWorkload = createDriver("d-null-wl", "Null Workload Driver", null, 100.0);

        when(assignmentRepository.countActiveAssignmentsByDriverId("d-null-wl")).thenReturn(1L);

        List<EligibleDriver> result = filter.filter(List.of(dNullWorkload), criteria);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCurrentWorkload()).isEqualTo(1);
        assertThat(result.get(0).getMetadata()).containsEntry("workloadStatus", "NORMAL");
        verify(assignmentRepository).countActiveAssignmentsByDriverId("d-null-wl");
    }

    @Test
    @DisplayName("Incomplete workload data (null) without AssignmentRepository: safely defaults to 0")
    void filter_incompleteWorkloadData_withoutRepository_defaultsSafelyToZero() {
        DriverWorkloadFilter filterNoRepo = new DriverWorkloadFilter(3, 2);
        EligibleDriver dNullWorkload = createDriver("d-no-repo", "No Repo Driver", null, 100.0);

        List<EligibleDriver> result = filterNoRepo.filter(List.of(dNullWorkload), criteria);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCurrentWorkload()).isEqualTo(0);
        assertThat(result.get(0).getMetadata()).containsEntry("workloadStatus", "IDLE");
        assertThat(result.get(0).getScore()).isEqualTo(120.0); // Awarded idle bonus
    }

    @Test
    @DisplayName("Invalid workload data (< 0) is rejected as invalid state")
    void filter_invalidNegativeWorkload_isRejected() {
        EligibleDriver dNegative = createDriver("d-neg", "Negative Driver", -1, 100.0);
        EligibleDriver dValid = createDriver("d-val", "Valid Driver", 1, 100.0);

        List<EligibleDriver> result = filter.filter(List.of(dNegative, dValid), criteria);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDriverId()).isEqualTo("d-val");
    }

    @Test
    @DisplayName("Metadata custom limits: driver with custom hardLimit or softLimit respects individual limits")
    void filter_metadataCustomLimits() {
        EligibleDriver dCustom = createDriver("d-custom", "Custom Limits", 2, 100.0);
        dCustom.addMetadata("hardLimit", 2); // Custom hardLimit = 2
        dCustom.addMetadata("softLimit", 1); // Custom softLimit = 1

        // With custom hardLimit = 2 and workload = 2:
        // When rejectAtHardLimit is true, should be rejected
        filter.setRejectAtHardLimit(true);
        List<EligibleDriver> result = filter.filter(List.of(dCustom), criteria);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Filter order is 20, running after DriverAvailabilityFilter (order 10)")
    void filter_orderAndName() {
        assertThat(filter.getOrder()).isEqualTo(20);
        assertThat(filter.getName()).isEqualTo("DriverWorkloadFilter");
    }
}
