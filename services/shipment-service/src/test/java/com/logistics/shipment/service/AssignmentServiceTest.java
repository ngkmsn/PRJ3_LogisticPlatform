package com.logistics.shipment.service;

import com.logistics.common.model.OrderStatus;
import com.logistics.shipment.dto.OrderCreatedPayload;
import com.logistics.shipment.entity.Assignment;
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
class AssignmentServiceTest {

    @Inject
    AssignmentService assignmentService;

    @Inject
    AssignmentRepository assignmentRepository;

    @BeforeEach
    @Transactional
    void cleanUp() {
        assignmentRepository.deleteAll();
    }

    private OrderCreatedPayload createPayload(String orderId) {
        return new OrderCreatedPayload(
                orderId,
                "ORD-" + orderId.substring(0, 8),
                "cust-" + UUID.randomUUID(),
                "Trần Thị B",
                "0912345678",
                "456 Lê Lợi, Quận 1, TP.HCM",
                OrderStatus.PENDING,
                BigDecimal.valueOf(200000.00),
                "Giao trước 17h",
                Instant.now()
        );
    }

    @Test
    @DisplayName("initiateAssignment creates and persists a new Assignment record with PENDING status")
    void initiateAssignment_success() {
        String orderId = "ord-" + UUID.randomUUID();
        OrderCreatedPayload payload = createPayload(orderId);

        Assignment assignment = assignmentService.initiateAssignment(payload);

        assertThat(assignment).isNotNull();
        assertThat(assignment.getId()).isNotNull();
        assertThat(assignment.getOrderId()).isEqualTo(orderId);
        assertThat(assignment.getOrderNumber()).isEqualTo(payload.getOrderNumber());
        assertThat(assignment.getCustomerId()).isEqualTo(payload.getCustomerId());
        assertThat(assignment.getRecipientName()).isEqualTo(payload.getRecipientName());
        assertThat(assignment.getRecipientPhone()).isEqualTo(payload.getRecipientPhone());
        assertThat(assignment.getDeliveryAddress()).isEqualTo(payload.getDeliveryAddress());
        assertThat(assignment.getTotalAmount()).isEqualByComparingTo(payload.getTotalAmount());
        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.PENDING);
        assertThat(assignment.getDriverId()).isNull();
        assertThat(assignment.getCreatedAt()).isNotNull();

        // Verify persisted in DB
        Optional<Assignment> found = assignmentService.findByOrderId(orderId);
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(assignment.getId());
    }

    @Test
    @DisplayName("initiateAssignment is idempotent: duplicate calls return existing assignment without error")
    void initiateAssignment_idempotent_duplicateCalls() {
        String orderId = "ord-dup-" + UUID.randomUUID();
        OrderCreatedPayload payload = createPayload(orderId);

        Assignment first = assignmentService.initiateAssignment(payload);
        Assignment second = assignmentService.initiateAssignment(payload);

        assertThat(second).isNotNull();
        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(assignmentRepository.count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("initiateAssignment throws IllegalArgumentException when payload or orderId is null")
    void initiateAssignment_nullPayload_throwsException() {
        assertThatThrownBy(() -> assignmentService.initiateAssignment(null))
                .isInstanceOf(IllegalArgumentException.class);

        OrderCreatedPayload empty = new OrderCreatedPayload();
        assertThatThrownBy(() -> assignmentService.initiateAssignment(empty))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("findById retrieves assignment by id")
    void findById_success() {
        String orderId = "ord-id-" + UUID.randomUUID();
        Assignment created = assignmentService.initiateAssignment(createPayload(orderId));

        Optional<Assignment> found = assignmentService.findById(created.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getOrderId()).isEqualTo(orderId);

        Optional<Assignment> notFound = assignmentService.findById("non-existent-id");
        assertThat(notFound).isEmpty();
    }

    @Test
    @DisplayName("createAssignment creates and persists a new Assignment with ASSIGNED status and assignedAt")
    void createAssignment_success() {
        String orderId = "ord-create-" + UUID.randomUUID();
        String driverId = "drv-alpha-1";

        com.logistics.shipment.dto.CreateAssignmentRequest request = com.logistics.shipment.dto.CreateAssignmentRequest.builder()
                .orderId(orderId)
                .orderNumber("ORD-ALPHA-01")
                .customerId("cust-alpha")
                .recipientName("Nguyen Van A")
                .recipientPhone("0901234567")
                .deliveryAddress("123 Le Loi, D1, HCMC")
                .totalAmount(BigDecimal.valueOf(150000.00))
                .driverId(driverId)
                .notes("Giao gap")
                .build();

        Assignment assignment = assignmentService.createAssignment(request);

        assertThat(assignment).isNotNull();
        assertThat(assignment.getId()).isNotBlank();
        assertThat(assignment.getOrderId()).isEqualTo(orderId);
        assertThat(assignment.getDriverId()).isEqualTo(driverId);
        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.ASSIGNED);
        assertThat(assignment.getAssignedAt()).isNotNull();

        Optional<Assignment> found = assignmentService.findByOrderId(orderId);
        assertThat(found).isPresent();
        assertThat(found.get().getDriverId()).isEqualTo(driverId);
        assertThat(found.get().getStatus()).isEqualTo(AssignmentStatus.ASSIGNED);
        assertThat(found.get().getAssignedAt()).isNotNull();
    }

    @Test
    @DisplayName("createAssignment on existing PENDING assignment transitions it to ASSIGNED and records assignedAt")
    void createAssignment_transitionsExistingPendingToAssigned() {
        String orderId = "ord-trans-" + UUID.randomUUID();
        Assignment pending = assignmentService.initiateAssignment(createPayload(orderId));
        assertThat(pending.getStatus()).isEqualTo(AssignmentStatus.PENDING);
        assertThat(pending.getAssignedAt()).isNull();

        String driverId = "drv-beta-2";
        com.logistics.shipment.dto.CreateAssignmentRequest request = com.logistics.shipment.dto.CreateAssignmentRequest.builder()
                .orderId(orderId)
                .orderNumber(pending.getOrderNumber())
                .customerId(pending.getCustomerId())
                .recipientName(pending.getRecipientName())
                .recipientPhone(pending.getRecipientPhone())
                .deliveryAddress(pending.getDeliveryAddress())
                .totalAmount(pending.getTotalAmount())
                .driverId(driverId)
                .build();

        Assignment assigned = assignmentService.createAssignment(request);

        assertThat(assigned.getId()).isEqualTo(pending.getId());
        assertThat(assigned.getStatus()).isEqualTo(AssignmentStatus.ASSIGNED);
        assertThat(assigned.getDriverId()).isEqualTo(driverId);
        assertThat(assigned.getAssignedAt()).isNotNull();
    }

    @Test
    @DisplayName("createAssignment is idempotent when called again with the same driverId")
    void createAssignment_idempotentSameDriver() {
        String orderId = "ord-same-drv-" + UUID.randomUUID();
        String driverId = "drv-gamma-3";

        com.logistics.shipment.dto.CreateAssignmentRequest request = com.logistics.shipment.dto.CreateAssignmentRequest.builder()
                .orderId(orderId)
                .customerId("cust-gamma")
                .deliveryAddress("789 Dien Bien Phu, Binh Thanh")
                .driverId(driverId)
                .build();

        Assignment first = assignmentService.createAssignment(request);
        Assignment second = assignmentService.createAssignment(request);

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getDriverId()).isEqualTo(driverId);
        assertThat(assignmentRepository.count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("createAssignment throws DuplicateAssignmentException when order is already assigned to a different driver")
    void createAssignment_duplicateDifferentDriver_throwsException() {
        String orderId = "ord-conflict-" + UUID.randomUUID();
        String originalDriverId = "drv-original";
        String newDriverId = "drv-intruder";

        com.logistics.shipment.dto.CreateAssignmentRequest firstReq = com.logistics.shipment.dto.CreateAssignmentRequest.builder()
                .orderId(orderId)
                .customerId("cust-conflict")
                .deliveryAddress("District 1")
                .driverId(originalDriverId)
                .build();

        assignmentService.createAssignment(firstReq);

        com.logistics.shipment.dto.CreateAssignmentRequest conflictingReq = com.logistics.shipment.dto.CreateAssignmentRequest.builder()
                .orderId(orderId)
                .customerId("cust-conflict")
                .deliveryAddress("District 1")
                .driverId(newDriverId)
                .build();

        assertThatThrownBy(() -> assignmentService.createAssignment(conflictingReq))
                .isInstanceOf(com.logistics.shipment.exception.DuplicateAssignmentException.class)
                .hasMessageContaining("already has an active assignment");
    }

    @Test
    @DisplayName("assignDriver assigns driver to existing assignment and throws if already assigned to another driver")
    void assignDriver_successAndConflictHandling() {
        String orderId = "ord-assign-drv-" + UUID.randomUUID();
        assignmentService.initiateAssignment(createPayload(orderId));

        // Assign driver 1
        Assignment assigned = assignmentService.assignDriver(orderId, "drv-one");
        assertThat(assigned.getStatus()).isEqualTo(AssignmentStatus.ASSIGNED);
        assertThat(assigned.getDriverId()).isEqualTo("drv-one");
        assertThat(assigned.getAssignedAt()).isNotNull();

        // Idempotent call with same driver
        Assignment idempotent = assignmentService.assignDriver(orderId, "drv-one");
        assertThat(idempotent.getId()).isEqualTo(assigned.getId());

        // Call with different driver throws DuplicateAssignmentException
        assertThatThrownBy(() -> assignmentService.assignDriver(orderId, "drv-two"))
                .isInstanceOf(com.logistics.shipment.exception.DuplicateAssignmentException.class);
    }
}
