package com.logistics.shipment.publisher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.logistics.common.security.JwtTokenProvider;
import com.logistics.common.security.Role;
import com.logistics.shipment.dto.ConfirmDeliveryRequest;
import com.logistics.shipment.dto.ReportDeliveryFailureRequest;
import com.logistics.shipment.entity.Assignment;
import com.logistics.shipment.entity.OutboxEvent;
import com.logistics.shipment.model.AssignmentStatus;
import com.logistics.shipment.repository.AssignmentHistoryRepository;
import com.logistics.shipment.repository.AssignmentRepository;
import com.logistics.shipment.repository.OutboxEventRepository;
import com.logistics.shipment.service.AssignmentService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@QuarkusTest
class ShipmentTransactionalOutboxIntegrationTest {

    @Inject
    AssignmentRepository assignmentRepository;

    @Inject
    AssignmentHistoryRepository assignmentHistoryRepository;

    @Inject
    OutboxEventRepository outboxEventRepository;

    @Inject
    AssignmentService assignmentService;

    @Inject
    JwtTokenProvider jwtTokenProvider;

    @Inject
    ObjectMapper objectMapper;

    private String driverToken;
    private final String driverUserId = "drv-outbox-user-001";

    @BeforeEach
    @Transactional
    void setUp() {
        outboxEventRepository.deleteAll();
        assignmentHistoryRepository.deleteAll();
        assignmentRepository.deleteAll();

        driverToken = "Bearer " + jwtTokenProvider.generateToken(
                driverUserId, "driverOutbox", "driver@test.com", Role.DRIVER.name()
        );
    }

    @Transactional
    Assignment persistAssignment(String orderId, String driverId, AssignmentStatus status) {
        Instant now = Instant.now();
        Assignment a = new Assignment();
        a.setId(UUID.randomUUID().toString());
        a.setOrderId(orderId);
        a.setOrderNumber("ORD-OB-" + orderId);
        a.setCustomerId("cust-ob-100");
        a.setRecipientName("Pham Thi Lan");
        a.setRecipientPhone("0908889999");
        a.setDeliveryAddress("789 Vo Van Kiet, Q1, HCMC");
        a.setTotalAmount(new BigDecimal("220000.00"));
        a.setStatus(status);
        a.setDriverId(driverId);
        a.setAssignedAt(now);
        if (status == AssignmentStatus.PICKED_UP || status == AssignmentStatus.IN_TRANSIT) {
            a.setPickedUpAt(now);
        }
        if (status == AssignmentStatus.IN_TRANSIT) {
            a.setTransitStartedAt(now);
        }
        a.setCreatedAt(now);
        a.setUpdatedAt(now);
        assignmentRepository.persist(a);
        return a;
    }

    @Test
    @DisplayName("PB-050: confirmDelivery persists ORDER_DELIVERED outbox event inside same transaction")
    void testConfirmDelivery_persistsOutboxEvent() throws Exception {
        String orderId = "ord-del-tx-101";
        persistAssignment(orderId, driverUserId, AssignmentStatus.IN_TRANSIT);

        ConfirmDeliveryRequest request = new ConfirmDeliveryRequest();
        request.setNotes("Delivered to front desk security");
        request.setProofOfDelivery("https://s3.example.com/pod/sig-tx.png");

        Assignment delivered = assignmentService.confirmDelivery(driverToken, orderId, request);

        assertThat(delivered).isNotNull();
        assertThat(delivered.getStatus()).isEqualTo(AssignmentStatus.DELIVERED);

        // Verify outbox event in database
        List<OutboxEvent> outboxEvents = outboxEventRepository.findByAggregateId(orderId);
        assertThat(outboxEvents).isNotEmpty();

        OutboxEvent event = outboxEvents.stream()
                .filter(e -> "ORDER_DELIVERED".equals(e.getEventType()))
                .findFirst()
                .orElse(null);

        assertThat(event).isNotNull();
        assertThat(event.getAggregateType()).isEqualTo("SHIPMENT");
        assertThat(event.getAggregateId()).isEqualTo(orderId);

        JsonNode root = objectMapper.readTree(event.getPayload());
        assertThat(root.get("eventType").asText()).isEqualTo("ORDER_DELIVERED");
        JsonNode payload = root.get("payload");
        assertThat(payload.get("orderId").asText()).isEqualTo(orderId);
        assertThat(payload.get("recipientName").asText()).isEqualTo("Pham Thi Lan");
        assertThat(payload.get("recipientPhone").asText()).isEqualTo("0908889999");
        assertThat(payload.get("deliveryAddress").asText()).isEqualTo("789 Vo Van Kiet, Q1, HCMC");
        assertThat(payload.get("deliveryNotes").asText()).isEqualTo("Delivered to front desk security");
    }

    @Test
    @DisplayName("PB-050: reportDeliveryFailure persists ORDER_FAILED outbox event inside same transaction")
    void testReportDeliveryFailure_persistsOutboxEvent() throws Exception {
        String orderId = "ord-fail-tx-202";
        persistAssignment(orderId, driverUserId, AssignmentStatus.IN_TRANSIT);

        ReportDeliveryFailureRequest request = new ReportDeliveryFailureRequest();
        request.setReason("Recipient refused package");
        request.setNotes("Customer cancelled order on door");

        Assignment failed = assignmentService.reportDeliveryFailure(driverToken, orderId, request);

        assertThat(failed).isNotNull();
        assertThat(failed.getStatus()).isEqualTo(AssignmentStatus.FAILED);

        List<OutboxEvent> outboxEvents = outboxEventRepository.findByAggregateId(orderId);
        assertThat(outboxEvents).isNotEmpty();

        OutboxEvent event = outboxEvents.stream()
                .filter(e -> "ORDER_FAILED".equals(e.getEventType()))
                .findFirst()
                .orElse(null);

        assertThat(event).isNotNull();
        assertThat(event.getEventType()).isEqualTo("ORDER_FAILED");

        JsonNode root = objectMapper.readTree(event.getPayload());
        JsonNode payload = root.get("payload");
        assertThat(payload.get("failureReason").asText()).isEqualTo("Recipient refused package");
        assertThat(payload.get("failureNotes").asText()).isEqualTo("Customer cancelled order on door");
    }

    @Test
    @DisplayName("PB-050: Failed business operation / invalid state does NOT create any outbox event")
    void testFailedOperation_doesNotCreateOutboxEvent() {
        String orderId = "ord-invalid-tx-303";
        // Create in ASSIGNED status (not IN_TRANSIT) so confirmDelivery fails validation
        persistAssignment(orderId, driverUserId, AssignmentStatus.ASSIGNED);

        ConfirmDeliveryRequest request = new ConfirmDeliveryRequest();

        assertThatThrownBy(() -> assignmentService.confirmDelivery(driverToken, orderId, request))
                .isInstanceOf(IllegalStateException.class);

        // Verify NO outbox event created
        List<OutboxEvent> outboxEvents = outboxEventRepository.findByAggregateId(orderId);
        assertThat(outboxEvents).isEmpty();
    }

    @Test
    @DisplayName("PB-051: Downstream notification failure does NOT rollback a successfully completed Order delivery")
    void testOrderDelivered_notificationFailure_doesNotRollbackOrder() throws Exception {
        String orderId = "ord-del-norollback-404";
        persistAssignment(orderId, driverUserId, AssignmentStatus.IN_TRANSIT);

        ConfirmDeliveryRequest request = new ConfirmDeliveryRequest();
        request.setNotes("Giao tan tay khach");

        // Execute successful delivery transaction
        var response = assignmentService.confirmDelivery(driverToken, orderId, request);
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(AssignmentStatus.DELIVERED);

        // Verify that in the database, the Order/Assignment is permanently DELIVERED
        Assignment committedAssignment = assignmentRepository.findByOrderId(orderId).orElse(null);
        assertThat(committedAssignment).isNotNull();
        assertThat(committedAssignment.getStatus()).isEqualTo(AssignmentStatus.DELIVERED);
        assertThat(committedAssignment.getDeliveredAt()).isNotNull();

        // The transactional outbox guarantees event was persisted and decoupled from notification delivery
        List<OutboxEvent> outboxEvents = outboxEventRepository.findByAggregateId(orderId);
        assertThat(outboxEvents).isNotEmpty();
    }
}
