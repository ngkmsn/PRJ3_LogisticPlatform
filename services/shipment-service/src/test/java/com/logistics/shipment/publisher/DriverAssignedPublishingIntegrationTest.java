package com.logistics.shipment.publisher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.logistics.shipment.dto.CreateAssignmentRequest;
import com.logistics.shipment.dto.OrderCreatedPayload;
import com.logistics.shipment.entity.Assignment;
import com.logistics.shipment.exception.DuplicateAssignmentException;
import com.logistics.shipment.model.AssignmentStatus;
import com.logistics.shipment.repository.AssignmentRepository;
import com.logistics.shipment.service.AssignmentService;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@QuarkusTest
class DriverAssignedPublishingIntegrationTest {

    @Inject
    AssignmentService assignmentService;

    @Inject
    AssignmentRepository assignmentRepository;

    @Inject
    ObjectMapper objectMapper;

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

    private CreateAssignmentRequest createRequest(String orderId, String driverId) {
        return CreateAssignmentRequest.builder()
                .orderId(orderId)
                .orderNumber("ORD-" + orderId.substring(0, Math.min(orderId.length(), 8)))
                .customerId("cust-kafka-01")
                .recipientName("Tran Thi Event")
                .recipientPhone("0988112233")
                .deliveryAddress("200 Vo Thi Sau, D3, HCMC")
                .totalAmount(BigDecimal.valueOf(180000.00))
                .driverId(driverId)
                .notes("Express delivery")
                .build();
    }

    @Test
    @DisplayName("End-to-End: When Assignment is created successfully, DRIVER_ASSIGNED event is published to Kafka sink")
    void testCreateAssignment_publishesDriverAssignedEventToKafkaSink() throws Exception {
        String orderId = "ord-kafka-1-" + UUID.randomUUID();
        String driverId = "drv-kafka-01";
        CreateAssignmentRequest request = createRequest(orderId, driverId);

        Assignment assignment = assignmentService.createAssignment(request);
        assertThat(assignment).isNotNull();
        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.ASSIGNED);

        // Verify Kafka sink received exactly 1 message
        assertThat(sink.received()).hasSize(1);

        Record<String, String> record = sink.received().get(0).getPayload();
        assertThat(record.key()).isEqualTo(orderId);

        String json = record.value();
        JsonNode root = objectMapper.readTree(json);
        assertThat(root.get("eventType").asText()).isEqualTo("DRIVER_ASSIGNED");
        assertThat(root.get("aggregateId").asText()).isEqualTo(orderId);

        JsonNode payload = root.get("payload");
        assertThat(payload.get("orderId").asText()).isEqualTo(orderId);
        assertThat(payload.get("driverId").asText()).isEqualTo(driverId);
        assertThat(payload.get("status").asText()).isEqualTo("ASSIGNED");
        assertThat(payload.has("assignedAt")).isTrue();
    }

    @Test
    @DisplayName("End-to-End: When driver is assigned to existing assignment, DRIVER_ASSIGNED event is published")
    void testAssignDriver_publishesDriverAssignedEvent() throws Exception {
        String orderId = "ord-kafka-2-" + UUID.randomUUID();
        // Create initial PENDING assignment
        OrderCreatedPayload payload = new OrderCreatedPayload(
                orderId, "ORD-INIT-01", "cust-1", "Receiver", "0900000000",
                "Address", null, BigDecimal.valueOf(100), "notes", Instant.now()
        );
        assignmentService.initiateAssignment(payload);
        assertThat(sink.received()).isEmpty(); // No event for initial PENDING state

        // Assign driver
        String driverId = "drv-kafka-02";
        Assignment assigned = assignmentService.assignDriver(orderId, driverId);
        assertThat(assigned.getStatus()).isEqualTo(AssignmentStatus.ASSIGNED);

        // Verify event published
        assertThat(sink.received()).hasSize(1);
        Record<String, String> record = sink.received().get(0).getPayload();
        assertThat(record.key()).isEqualTo(orderId);

        JsonNode root = objectMapper.readTree(record.value());
        assertThat(root.get("eventType").asText()).isEqualTo("DRIVER_ASSIGNED");
        assertThat(root.get("payload").get("driverId").asText()).isEqualTo(driverId);
    }

    @Test
    @DisplayName("Reliability: When assignment creation fails due to conflict, NO event is published to Kafka")
    void testDatabaseFailureOrConflict_neverPublishesEvent() {
        String orderId = "ord-kafka-fail-" + UUID.randomUUID();
        CreateAssignmentRequest firstReq = createRequest(orderId, "drv-original");
        assignmentService.createAssignment(firstReq);
        assertThat(sink.received()).hasSize(1);
        sink.clear(); // Reset sink

        // Attempting to assign a second driver must fail and throw DuplicateAssignmentException
        CreateAssignmentRequest secondReq = createRequest(orderId, "drv-conflicting");
        assertThatThrownBy(() -> assignmentService.createAssignment(secondReq))
                .isInstanceOf(DuplicateAssignmentException.class);

        // Crucial requirement: ABSOLUTELY NO EVENT emitted on failure
        assertThat(sink.received()).isEmpty();
    }

    @Test
    @DisplayName("Idempotency: Re-assigning the same driver does not publish duplicate events")
    void testIdempotentCall_doesNotDuplicateEvent() {
        String orderId = "ord-kafka-idemp-" + UUID.randomUUID();
        String driverId = "drv-kafka-same";
        CreateAssignmentRequest request = createRequest(orderId, driverId);

        // First call: creates assignment and publishes 1 event
        assignmentService.createAssignment(request);
        assertThat(sink.received()).hasSize(1);

        // Second call (idempotent duplicate): returns existing assignment, does NOT re-publish event
        assignmentService.createAssignment(request);
        assertThat(sink.received()).hasSize(1); // Still exactly 1 event!
    }
}
