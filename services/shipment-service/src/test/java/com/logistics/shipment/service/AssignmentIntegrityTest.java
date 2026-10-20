package com.logistics.shipment.service;

import com.logistics.shipment.dto.CreateAssignmentRequest;
import com.logistics.shipment.entity.Assignment;
import com.logistics.shipment.exception.DuplicateAssignmentException;
import com.logistics.shipment.model.AssignmentStatus;
import com.logistics.shipment.repository.AssignmentRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@QuarkusTest
class AssignmentIntegrityTest {

    @Inject
    AssignmentService assignmentService;

    @Inject
    AssignmentRepository assignmentRepository;

    @BeforeEach
    @Transactional
    void cleanUp() {
        assignmentRepository.deleteAll();
    }

    private CreateAssignmentRequest createRequest(String orderId, String driverId) {
        return CreateAssignmentRequest.builder()
                .orderId(orderId)
                .orderNumber("ORD-" + orderId.substring(0, Math.min(orderId.length(), 8)))
                .customerId("cust-integrity-1")
                .recipientName("Tran Van B")
                .recipientPhone("0987654321")
                .deliveryAddress("100 Pasteur, Ben Nghe, D1, HCMC")
                .totalAmount(BigDecimal.valueOf(250000.00))
                .driverId(driverId)
                .notes("Handle with care")
                .build();
    }

    @Test
    @DisplayName("Create Assignment: Successfully creates and links order with driver and sets assignedAt timestamp")
    void testCreateAssignment_successLinking() {
        String orderId = "ord-integrity-" + UUID.randomUUID();
        String driverId = "drv-verified-01";
        CreateAssignmentRequest request = createRequest(orderId, driverId);

        Assignment created = assignmentService.createAssignment(request);

        assertThat(created).isNotNull();
        assertThat(created.getId()).isNotBlank();
        assertThat(created.getOrderId()).isEqualTo(orderId);
        assertThat(created.getDriverId()).isEqualTo(driverId);
        assertThat(created.getStatus()).isEqualTo(AssignmentStatus.ASSIGNED);
        assertThat(created.getAssignedAt()).isNotNull();

        // Verify in DB via Repository methods
        Optional<Assignment> fromDb = assignmentRepository.findByOrderId(orderId);
        assertThat(fromDb).isPresent();
        assertThat(fromDb.get().getDriverId()).isEqualTo(driverId);
        assertThat(fromDb.get().getAssignedAt()).isNotNull();

        // Verify active assignment repository queries
        assertThat(assignmentRepository.hasActiveAssignmentForOrder(orderId)).isTrue();
        Optional<Assignment> active = assignmentRepository.findActiveAssignmentByOrderId(orderId);
        assertThat(active).isPresent();
        assertThat(active.get().getId()).isEqualTo(created.getId());
    }

    @Test
    @DisplayName("Active Assignment Constraint: Attempting to assign order to a second driver throws DuplicateAssignmentException")
    void testActiveAssignment_cannotAssignSecondDriver() {
        String orderId = "ord-exclusive-" + UUID.randomUUID();
        CreateAssignmentRequest firstReq = createRequest(orderId, "drv-first-winner");
        assignmentService.createAssignment(firstReq);

        CreateAssignmentRequest secondReq = createRequest(orderId, "drv-second-intruder");

        assertThatThrownBy(() -> assignmentService.createAssignment(secondReq))
                .isInstanceOf(DuplicateAssignmentException.class)
                .hasMessageContaining("already has an active assignment");

        // Verify DB still only has the original driver
        Assignment current = assignmentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(current.getDriverId()).isEqualTo("drv-first-winner");
        assertThat(current.getStatus()).isEqualTo(AssignmentStatus.ASSIGNED);
        assertThat(assignmentRepository.count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Active Assignment Constraint: Repository correctly distinguishes active vs cancelled/failed assignments")
    @Transactional
    void testRepository_activeVsInactiveAssignments() {
        String activeOrderId = "ord-active-" + UUID.randomUUID();
        Assignment active = new Assignment(
                UUID.randomUUID().toString(), activeOrderId, "ORD-ACT", "cust-1", "Name", "Phone",
                "Address", BigDecimal.valueOf(100), AssignmentStatus.ASSIGNED, "drv-1",
                Instant.now(), "notes", Instant.now(), Instant.now()
        );
        assignmentRepository.persist(active);

        String cancelledOrderId = "ord-cancelled-" + UUID.randomUUID();
        Assignment cancelled = new Assignment(
                UUID.randomUUID().toString(), cancelledOrderId, "ORD-CAN", "cust-1", "Name", "Phone",
                "Address", BigDecimal.valueOf(100), AssignmentStatus.CANCELLED, null,
                null, "notes", Instant.now(), Instant.now()
        );
        assignmentRepository.persist(cancelled);

        assertThat(assignmentRepository.hasActiveAssignmentForOrder(activeOrderId)).isTrue();
        assertThat(assignmentRepository.findActiveAssignmentByOrderId(activeOrderId)).isPresent();

        assertThat(assignmentRepository.hasActiveAssignmentForOrder(cancelledOrderId)).isFalse();
        assertThat(assignmentRepository.findActiveAssignmentByOrderId(cancelledOrderId)).isEmpty();
    }

    @Test
    @DisplayName("Database Integrity: Raw direct DB insert with duplicate order_id violates unique constraint")
    @Transactional
    void testDatabaseUniqueConstraint_onOrderId() {
        String duplicateOrderId = "ord-db-unique-" + UUID.randomUUID();

        Assignment a1 = new Assignment(
                UUID.randomUUID().toString(), duplicateOrderId, "ORD-1", "cust-1", "Name", "Phone",
                "Address", BigDecimal.valueOf(100), AssignmentStatus.ASSIGNED, "drv-1",
                Instant.now(), "notes", Instant.now(), Instant.now()
        );
        assignmentRepository.persistAndFlush(a1);

        Assignment a2 = new Assignment(
                UUID.randomUUID().toString(), duplicateOrderId, "ORD-2", "cust-2", "Name 2", "Phone 2",
                "Address 2", BigDecimal.valueOf(200), AssignmentStatus.ASSIGNED, "drv-2",
                Instant.now(), "notes 2", Instant.now(), Instant.now()
        );

        // Attempting direct persist of a2 with same orderId must fail at database level
        assertThatThrownBy(() -> assignmentRepository.persistAndFlush(a2))
                .isInstanceOf(Exception.class);
    }
}
