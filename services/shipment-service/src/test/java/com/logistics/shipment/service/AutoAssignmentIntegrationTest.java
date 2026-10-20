package com.logistics.shipment.service;

import com.logistics.common.model.DriverAvailability;
import com.logistics.common.model.OrderStatus;
import com.logistics.shipment.dto.OrderCreatedPayload;
import com.logistics.shipment.eligibility.client.DriverClient;
import com.logistics.shipment.eligibility.model.AssignmentCriteria;
import com.logistics.shipment.eligibility.model.EligibleDriver;
import com.logistics.shipment.entity.Assignment;
import com.logistics.shipment.model.AssignmentStatus;
import com.logistics.shipment.repository.AssignmentRepository;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@QuarkusTest
class AutoAssignmentIntegrationTest {

    @Inject
    AssignmentService assignmentService;

    @Inject
    AssignmentRepository assignmentRepository;

    @InjectMock
    DriverClient driverClient;

    @BeforeEach
    @Transactional
    void setUp() {
        assignmentRepository.deleteAll();
    }

    private OrderCreatedPayload createPayload(String orderId) {
        return new OrderCreatedPayload(
                orderId,
                "ORD-" + orderId.substring(0, 8),
                "cust-" + UUID.randomUUID(),
                "Nguyen Van Khach",
                "0988776655",
                "10.7769, 106.7009", // District 1, HCMC
                OrderStatus.PENDING,
                BigDecimal.valueOf(180000.00),
                "Giao nhanh",
                Instant.now()
        );
    }

    private EligibleDriver createCandidate(String driverId, String name, double lat, double lon,
                                           DriverAvailability availability, String status) {
        EligibleDriver driver = new EligibleDriver(
                driverId, "user-" + driverId, name, "0911223344", "MOTORBIKE", "59B-56789",
                availability, status, lat, lon, Instant.now()
        );
        return driver;
    }

    @Test
    @DisplayName("End-to-End Auto Assignment: Selects best driver and updates assignment status to ASSIGNED")
    void testAutoAssignment_successfulAssignment() {
        String orderId = "ord-auto-1-" + UUID.randomUUID();
        OrderCreatedPayload payload = createPayload(orderId);

        // Candidate 1: 10.7800, 106.7000 (~0.35 km away, idle)
        EligibleDriver bestCandidate = createCandidate("drv-best", "Driver Best", 10.7800, 106.7000,
                DriverAvailability.AVAILABLE, "ACTIVE");

        // Candidate 2: 10.8200, 106.6300 (~9.0 km away)
        EligibleDriver fartherCandidate = createCandidate("drv-far", "Driver Far", 10.8200, 106.6300,
                DriverAvailability.AVAILABLE, "ACTIVE");

        when(driverClient.getCandidateDrivers(any(AssignmentCriteria.class)))
                .thenReturn(List.of(bestCandidate, fartherCandidate));

        Assignment assignment = assignmentService.autoAssignOrder(payload);

        assertThat(assignment).isNotNull();
        assertThat(assignment.getOrderId()).isEqualTo(orderId);
        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.ASSIGNED);
        assertThat(assignment.getDriverId()).isEqualTo("drv-best");

        // Verify in DB
        Assignment persisted = assignmentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(AssignmentStatus.ASSIGNED);
        assertThat(persisted.getDriverId()).isEqualTo("drv-best");
    }

    @Test
    @DisplayName("End-to-End Auto Assignment: Fallback to UNASSIGNED when no drivers are available (PB-033)")
    void testAutoAssignment_noEligibleCandidates_fallsBackToUnassigned() {
        String orderId = "ord-auto-empty-" + UUID.randomUUID();
        OrderCreatedPayload payload = createPayload(orderId);

        when(driverClient.getCandidateDrivers(any(AssignmentCriteria.class)))
                .thenReturn(Collections.emptyList());

        Assignment assignment = assignmentService.autoAssignOrder(payload);

        assertThat(assignment).isNotNull();
        assertThat(assignment.getOrderId()).isEqualTo(orderId);
        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.UNASSIGNED);
        assertThat(assignment.getDriverId()).isNull();
        assertThat(assignment.getUnassignedReason()).isEqualTo("NO_ELIGIBLE_DRIVER");
        assertThat(assignment.getUnassignedAt()).isNotNull();

        Assignment persisted = assignmentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(AssignmentStatus.UNASSIGNED);
        assertThat(persisted.getDriverId()).isNull();
        assertThat(persisted.getUnassignedReason()).isEqualTo("NO_ELIGIBLE_DRIVER");
        assertThat(persisted.getUnassignedAt()).isNotNull();
    }

    @Test
    @DisplayName("End-to-End Auto Assignment: Concurrent capacity check selects next best candidate if top driver reaches hard limit")
    @Transactional
    void testAutoAssignment_overloadedCandidate_fallsToNextBestCandidate() {
        String topDriverId = "drv-top-busy";
        String nextDriverId = "drv-next-available";

        // Seed 3 active assignments for top driver (reaching hard limit = 3)
        for (int i = 0; i < 3; i++) {
            Assignment existing = new Assignment(
                    UUID.randomUUID().toString(),
                    "prev-ord-" + i + "-" + UUID.randomUUID(),
                    "ORD-PREV-" + i,
                    "cust-1",
                    "Receiver",
                    "0900000000",
                    "Address",
                    BigDecimal.valueOf(100000),
                    AssignmentStatus.ASSIGNED,
                    topDriverId,
                    "active order",
                    Instant.now(),
                    Instant.now()
            );
            assignmentRepository.persist(existing);
        }

        String newOrderId = "ord-auto-concurrency-" + UUID.randomUUID();
        OrderCreatedPayload payload = createPayload(newOrderId);

        // Top candidate is very close (0.1 km)
        EligibleDriver topCandidate = createCandidate(topDriverId, "Top Driver", 10.7770, 106.7010,
                DriverAvailability.AVAILABLE, "ACTIVE");

        // Next candidate is slightly farther (1.0 km) but completely available
        EligibleDriver nextCandidate = createCandidate(nextDriverId, "Next Driver", 10.7850, 106.7050,
                DriverAvailability.AVAILABLE, "ACTIVE");

        when(driverClient.getCandidateDrivers(any(AssignmentCriteria.class)))
                .thenReturn(List.of(topCandidate, nextCandidate));

        Assignment assignment = assignmentService.autoAssignOrder(payload);

        assertThat(assignment).isNotNull();
        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.ASSIGNED);
        // Top driver was rejected due to concurrent workload check (3 active in DB), next best was selected!
        assertThat(assignment.getDriverId()).isEqualTo(nextDriverId);
    }

    @Test
    @DisplayName("End-to-End Auto Assignment: Fallback to UNASSIGNED if all candidates are full at commit time (PB-033)")
    @Transactional
    void testAutoAssignment_allCandidatesOverloaded_fallsBackToUnassigned() {
        String driver1 = "drv-full-1";

        // Pre-fill 3 active assignments for driver1
        for (int i = 0; i < 3; i++) {
            Assignment existing = new Assignment(
                    UUID.randomUUID().toString(),
                    "full-ord-" + i + "-" + UUID.randomUUID(),
                    "ORD-FULL-" + i,
                    "cust-1",
                    "Receiver",
                    "0900000000",
                    "Address",
                    BigDecimal.valueOf(100000),
                    AssignmentStatus.ASSIGNED,
                    driver1,
                    "active order",
                    Instant.now(),
                    Instant.now()
            );
            assignmentRepository.persist(existing);
        }

        String orderId = "ord-auto-all-full-" + UUID.randomUUID();
        OrderCreatedPayload payload = createPayload(orderId);

        EligibleDriver candidate1 = createCandidate(driver1, "Driver 1", 10.7770, 106.7010,
                DriverAvailability.AVAILABLE, "ACTIVE");

        when(driverClient.getCandidateDrivers(any(AssignmentCriteria.class)))
                .thenReturn(List.of(candidate1));

        Assignment assignment = assignmentService.autoAssignOrder(payload);

        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.UNASSIGNED);
        assertThat(assignment.getDriverId()).isNull();
        assertThat(assignment.getUnassignedReason()).isEqualTo("ALL_DRIVERS_BUSY");
        assertThat(assignment.getUnassignedAt()).isNotNull();
    }

    @Test
    @DisplayName("Auto-assignment skips re-assigning if assignment is already ASSIGNED (idempotency)")
    @Transactional
    void testAutoAssignment_idempotentSkipIfAlreadyAssigned() {
        String orderId = "ord-already-assigned-" + UUID.randomUUID();
        Assignment existing = new Assignment(
                UUID.randomUUID().toString(),
                orderId,
                "ORD-ASSIGNED",
                "cust-1",
                "Receiver",
                "0900000000",
                "Address",
                BigDecimal.valueOf(100000),
                AssignmentStatus.ASSIGNED,
                "drv-existing",
                "already assigned",
                Instant.now(),
                Instant.now()
        );
        assignmentRepository.persist(existing);

        AssignmentCriteria criteria = AssignmentCriteria.builder().orderId(orderId).build();
        Assignment result = assignmentService.autoAssignOrder(orderId, criteria);

        assertThat(result.getId()).isEqualTo(existing.getId());
        assertThat(result.getStatus()).isEqualTo(AssignmentStatus.ASSIGNED);
        assertThat(result.getDriverId()).isEqualTo("drv-existing");
    }
}
