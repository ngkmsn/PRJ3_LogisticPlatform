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
import io.smallrye.reactive.messaging.kafka.Record;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import jakarta.enterprise.inject.Any;
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
class UnassignedDriverHandlingTest {

    @Inject
    AssignmentService assignmentService;

    @Inject
    AssignmentRepository assignmentRepository;

    @InjectMock
    DriverClient driverClient;

    @Inject
    @Any
    InMemoryConnector connector;

    private InMemorySink<Record<String, String>> sink;

    @BeforeEach
    @Transactional
    void setUp() {
        assignmentRepository.deleteAll();
        sink = connector.sink("shipment-events");
        sink.clear();
    }

    private OrderCreatedPayload createPayload(String orderId) {
        return new OrderCreatedPayload(
                orderId,
                "ORD-" + orderId.substring(0, Math.min(orderId.length(), 8)),
                "cust-ops-01",
                "Nguyen Operations Test",
                "0911335577",
                "10.7769, 106.7009",
                OrderStatus.PENDING,
                BigDecimal.valueOf(220000.00),
                "Fragile package",
                Instant.now()
        );
    }

    private EligibleDriver createCandidate(String driverId, DriverAvailability availability, String status, int workload) {
        EligibleDriver driver = new EligibleDriver(
                driverId, "usr-" + driverId, "Driver " + driverId, "0900112233", "VAN", "59C-99999",
                availability, status, 10.7800, 106.7000, Instant.now()
        );
        driver.setCurrentWorkload(workload);
        return driver;
    }

    @Test
    @DisplayName("Fallback Scenario 1: Initial candidate pool is empty -> transitions to UNASSIGNED with NO_ELIGIBLE_DRIVER")
    void testNoCandidatesFromClient_transitionsToUnassigned() {
        String orderId = "ord-unassigned-empty-" + UUID.randomUUID();
        OrderCreatedPayload payload = createPayload(orderId);

        when(driverClient.getCandidateDrivers(any(AssignmentCriteria.class)))
                .thenReturn(Collections.emptyList());

        Assignment assignment = assignmentService.autoAssignOrder(payload);

        assertThat(assignment).isNotNull();
        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.UNASSIGNED);
        assertThat(assignment.getDriverId()).isNull();
        assertThat(assignment.getUnassignedReason()).isEqualTo(DefaultAssignmentService.REASON_NO_ELIGIBLE_DRIVER);
        assertThat(assignment.getUnassignedAt()).isNotNull();

        // Verify in DB
        Assignment fromDb = assignmentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(fromDb.getStatus()).isEqualTo(AssignmentStatus.UNASSIGNED);
        assertThat(fromDb.getUnassignedReason()).isEqualTo(DefaultAssignmentService.REASON_NO_ELIGIBLE_DRIVER);
        assertThat(fromDb.getUnassignedAt()).isNotNull();

        // Verify NO Kafka event was emitted
        assertThat(sink.received()).isEmpty();
    }

    @Test
    @DisplayName("Fallback Scenario 1b: Candidates exist but all are ineligible after pipeline -> transitions to UNASSIGNED with NO_ELIGIBLE_DRIVER")
    void testAllCandidatesIneligibleAfterPipeline_transitionsToUnassigned() {
        String orderId = "ord-unassigned-ineligible-" + UUID.randomUUID();
        OrderCreatedPayload payload = createPayload(orderId);

        // Candidate 1: UNAVAILABLE
        EligibleDriver d1 = createCandidate("drv-unavail", DriverAvailability.UNAVAILABLE, "ACTIVE", 0);
        // Candidate 2: INACTIVE
        EligibleDriver d2 = createCandidate("drv-inactive", DriverAvailability.AVAILABLE, "INACTIVE", 0);
        // Candidate 3: Exceeds hard workload limit (4 > hardLimit 3)
        EligibleDriver d3 = createCandidate("drv-overloaded", DriverAvailability.AVAILABLE, "ACTIVE", 4);

        when(driverClient.getCandidateDrivers(any(AssignmentCriteria.class)))
                .thenReturn(List.of(d1, d2, d3));

        Assignment assignment = assignmentService.autoAssignOrder(payload);

        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.UNASSIGNED);
        assertThat(assignment.getDriverId()).isNull();
        assertThat(assignment.getUnassignedReason()).isEqualTo(DefaultAssignmentService.REASON_NO_ELIGIBLE_DRIVER);
        assertThat(assignment.getUnassignedAt()).isNotNull();

        // Verify NO Kafka event was emitted
        assertThat(sink.received()).isEmpty();
    }

    @Test
    @DisplayName("Fallback Scenario 2: Candidates qualify after pipeline but all are busy at commit time -> transitions to UNASSIGNED with ALL_DRIVERS_BUSY")
    @Transactional
    void testAllCandidatesOverloadedAtCommit_transitionsToUnassigned() {
        String busyDriverId = "drv-busy-at-commit";

        // Seed 3 active assignments in DB for busyDriverId (hard limit reached)
        for (int i = 0; i < 3; i++) {
            Assignment existing = new Assignment(
                    UUID.randomUUID().toString(),
                    "seed-busy-ord-" + i + "-" + UUID.randomUUID(),
                    "ORD-SEED-" + i,
                    "cust-1",
                    "Recipient",
                    "0900000000",
                    "Delivery Address",
                    BigDecimal.valueOf(100000),
                    AssignmentStatus.ASSIGNED,
                    busyDriverId,
                    Instant.now(),
                    "seed assignment",
                    Instant.now(),
                    Instant.now()
            );
            assignmentRepository.persist(existing);
        }

        String orderId = "ord-all-busy-" + UUID.randomUUID();
        OrderCreatedPayload payload = createPayload(orderId);

        // Driver passes pipeline check (e.g. currentWorkload reported as 1 initially by provider)
        EligibleDriver candidate = createCandidate(busyDriverId, DriverAvailability.AVAILABLE, "ACTIVE", 1);

        when(driverClient.getCandidateDrivers(any(AssignmentCriteria.class)))
                .thenReturn(List.of(candidate));

        Assignment assignment = assignmentService.autoAssignOrder(payload);

        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.UNASSIGNED);
        assertThat(assignment.getDriverId()).isNull();
        assertThat(assignment.getUnassignedReason()).isEqualTo(DefaultAssignmentService.REASON_ALL_DRIVERS_BUSY);
        assertThat(assignment.getUnassignedAt()).isNotNull();

        // Verify in DB
        Assignment fromDb = assignmentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(fromDb.getStatus()).isEqualTo(AssignmentStatus.UNASSIGNED);
        assertThat(fromDb.getUnassignedReason()).isEqualTo(DefaultAssignmentService.REASON_ALL_DRIVERS_BUSY);
        assertThat(fromDb.getUnassignedAt()).isNotNull();

        // Verify NO Kafka event was emitted
        assertThat(sink.received()).isEmpty();
    }

    @Test
    @DisplayName("Repository: findUnassignedAssignments retrieves only UNASSIGNED assignments ordered for Operations triage")
    @Transactional
    void testRepository_findUnassignedAssignments() {
        // Create 1 ASSIGNED
        Assignment assigned = new Assignment(
                UUID.randomUUID().toString(), "ord-assigned-" + UUID.randomUUID(), "ORD-ASS", "cust-1",
                "Recipient", "0900000000", "Address", BigDecimal.valueOf(100),
                AssignmentStatus.ASSIGNED, "drv-1", Instant.now(), null, Instant.now(), Instant.now()
        );
        assignmentRepository.persist(assigned);

        // Create 2 UNASSIGNED
        Assignment unassigned1 = new Assignment(
                UUID.randomUUID().toString(), "ord-unassigned-1-" + UUID.randomUUID(), "ORD-UN-1", "cust-1",
                "Recipient", "0900000000", "Address", BigDecimal.valueOf(100),
                AssignmentStatus.UNASSIGNED, null, null, null, Instant.now(), Instant.now()
        );
        unassigned1.setUnassignedReason(DefaultAssignmentService.REASON_NO_ELIGIBLE_DRIVER);
        unassigned1.setUnassignedAt(Instant.now().minusSeconds(120));
        assignmentRepository.persist(unassigned1);

        Assignment unassigned2 = new Assignment(
                UUID.randomUUID().toString(), "ord-unassigned-2-" + UUID.randomUUID(), "ORD-UN-2", "cust-1",
                "Recipient", "0900000000", "Address", BigDecimal.valueOf(100),
                AssignmentStatus.UNASSIGNED, null, null, null, Instant.now(), Instant.now()
        );
        unassigned2.setUnassignedReason(DefaultAssignmentService.REASON_ALL_DRIVERS_BUSY);
        unassigned2.setUnassignedAt(Instant.now());
        assignmentRepository.persist(unassigned2);

        List<Assignment> unassignedList = assignmentRepository.findUnassignedAssignments();

        assertThat(unassignedList).hasSize(2);
        assertThat(unassignedList).extracting(Assignment::getOrderId)
                .containsExactly(unassigned2.getOrderId(), unassigned1.getOrderId());
    }
}
