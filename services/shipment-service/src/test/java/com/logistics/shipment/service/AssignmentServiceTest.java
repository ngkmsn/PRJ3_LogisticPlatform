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
}
