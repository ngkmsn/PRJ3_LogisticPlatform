package com.logistics.shipment.publisher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.logistics.shipment.entity.Assignment;
import com.logistics.shipment.model.AssignmentStatus;
import io.smallrye.reactive.messaging.kafka.Record;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaDriverAssignedEventPublisherTest {

    @Mock
    private Emitter<Record<String, String>> emitter;

    private ObjectMapper objectMapper;
    private KafkaDriverAssignedEventPublisher publisher;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        publisher = new KafkaDriverAssignedEventPublisher(emitter, objectMapper);
    }

    private Assignment createSampleAssignment(String orderId, String driverId) {
        Instant now = Instant.now();
        return new Assignment(
                UUID.randomUUID().toString(),
                orderId,
                "ORD-TEST-001",
                "cust-123",
                "Nguyen Van A",
                "0909123456",
                "123 Nguyen Hue, D1, HCMC",
                BigDecimal.valueOf(250000.00),
                AssignmentStatus.ASSIGNED,
                driverId,
                now,
                "Fragile goods",
                now,
                now
        );
    }

    @Test
    @DisplayName("publishDriverAssigned serializes valid DomainEvent matching contract and sends to Kafka with orderId key")
    void testPublishDriverAssigned_success() throws Exception {
        String orderId = "ord-" + UUID.randomUUID();
        String driverId = "drv-alpha";
        Assignment assignment = createSampleAssignment(orderId, driverId);

        when(emitter.send(any(Record.class))).thenReturn(CompletableFuture.completedFuture(null));

        publisher.publishDriverAssigned(assignment);

        ArgumentCaptor<Record<String, String>> captor = ArgumentCaptor.forClass(Record.class);
        verify(emitter).send(captor.capture());

        Record<String, String> record = captor.getValue();
        assertThat(record.key()).isEqualTo(orderId);

        String json = record.value();
        assertThat(json).isNotBlank();

        JsonNode root = objectMapper.readTree(json);
        assertThat(root.has("eventId")).isTrue();
        assertThat(root.get("eventType").asText()).isEqualTo("DRIVER_ASSIGNED");
        assertThat(root.get("aggregateId").asText()).isEqualTo(orderId);
        assertThat(root.has("timestamp")).isTrue();

        JsonNode payload = root.get("payload");
        assertThat(payload.get("assignmentId").asText()).isEqualTo(assignment.getId());
        assertThat(payload.get("orderId").asText()).isEqualTo(orderId);
        assertThat(payload.get("orderNumber").asText()).isEqualTo("ORD-TEST-001");
        assertThat(payload.get("customerId").asText()).isEqualTo("cust-123");
        assertThat(payload.get("driverId").asText()).isEqualTo(driverId);
        assertThat(payload.get("status").asText()).isEqualTo("ASSIGNED");
        assertThat(payload.get("deliveryAddress").asText()).isEqualTo("123 Nguyen Hue, D1, HCMC");
        assertThat(payload.get("totalAmount").asDouble()).isEqualTo(250000.00);
        assertThat(payload.get("notes").asText()).isEqualTo("Fragile goods");
    }

    @Test
    @DisplayName("publishDriverAssigned safely skips publishing if assignment is null")
    void testPublish_nullAssignment_doesNotSend() {
        publisher.publishDriverAssigned(null);
        verify(emitter, never()).send(any(Record.class));
    }

    @Test
    @DisplayName("publishDriverAssigned safely skips publishing if driverId is null or blank")
    void testPublish_nullOrBlankDriverId_doesNotSend() {
        Assignment withoutDriver = createSampleAssignment("ord-1", null);
        publisher.publishDriverAssigned(withoutDriver);
        verify(emitter, never()).send(any(Record.class));

        Assignment withBlankDriver = createSampleAssignment("ord-2", "   ");
        publisher.publishDriverAssigned(withBlankDriver);
        verify(emitter, never()).send(any(Record.class));
    }

    @Test
    @DisplayName("PB-050: publishOrderDelivered emits ORDER_DELIVERED DomainEvent with sanitized recipient metadata and proof of delivery")
    void testPublishOrderDelivered_success() throws Exception {
        String orderId = "ord-del-" + UUID.randomUUID();
        String driverId = "drv-del-01";
        Assignment assignment = createSampleAssignment(orderId, driverId);
        assignment.setStatus(AssignmentStatus.DELIVERED);
        assignment.setDeliveredAt(Instant.now());
        assignment.setDeliveryNotes("Delivered to front desk");
        assignment.setProofOfDelivery("https://s3.example.com/pod/sig-001.png");

        when(emitter.send(any(Record.class))).thenReturn(CompletableFuture.completedFuture(null));

        publisher.publishOrderDelivered(assignment);

        ArgumentCaptor<Record<String, String>> captor = ArgumentCaptor.forClass(Record.class);
        verify(emitter).send(captor.capture());

        Record<String, String> record = captor.getValue();
        assertThat(record.key()).isEqualTo(orderId);

        JsonNode root = objectMapper.readTree(record.value());
        assertThat(root.has("eventId")).isTrue();
        assertThat(root.get("eventType").asText()).isEqualTo("ORDER_DELIVERED");
        assertThat(root.get("aggregateId").asText()).isEqualTo(orderId);
        assertThat(root.has("timestamp")).isTrue();

        JsonNode payload = root.get("payload");
        assertThat(payload.get("orderId").asText()).isEqualTo(orderId);
        assertThat(payload.get("status").asText()).isEqualTo("DELIVERED");
        assertThat(payload.get("recipientName").asText()).isEqualTo("Nguyen Van A");
        assertThat(payload.get("recipientPhone").asText()).isEqualTo("0909123456");
        assertThat(payload.get("deliveryAddress").asText()).isEqualTo("123 Nguyen Hue, D1, HCMC");
        assertThat(payload.get("deliveryNotes").asText()).isEqualTo("Delivered to front desk");
        assertThat(payload.get("proofOfDelivery").asText()).isEqualTo("https://s3.example.com/pod/sig-001.png");

        // Verify NO sensitive PII (no passwords, credit cards, or internal auth tokens)
        assertThat(payload.has("password")).isFalse();
        assertThat(payload.has("token")).isFalse();
        assertThat(payload.has("creditCard")).isFalse();
    }

    @Test
    @DisplayName("PB-050: publishOrderFailed emits ORDER_FAILED DomainEvent with failureReason and failureNotes")
    void testPublishOrderFailed_success() throws Exception {
        String orderId = "ord-fail-" + UUID.randomUUID();
        String driverId = "drv-fail-02";
        Assignment assignment = createSampleAssignment(orderId, driverId);
        assignment.setStatus(AssignmentStatus.FAILED);
        assignment.setFailedAt(Instant.now());
        assignment.setFailureReason("Customer unavailable");
        assignment.setFailureNotes("Called 3 times with no answer");

        when(emitter.send(any(Record.class))).thenReturn(CompletableFuture.completedFuture(null));

        publisher.publishOrderFailed(assignment);

        ArgumentCaptor<Record<String, String>> captor = ArgumentCaptor.forClass(Record.class);
        verify(emitter).send(captor.capture());

        Record<String, String> record = captor.getValue();
        assertThat(record.key()).isEqualTo(orderId);

        JsonNode root = objectMapper.readTree(record.value());
        assertThat(root.has("eventId")).isTrue();
        assertThat(root.get("eventType").asText()).isEqualTo("ORDER_FAILED");
        assertThat(root.get("aggregateId").asText()).isEqualTo(orderId);

        JsonNode payload = root.get("payload");
        assertThat(payload.get("orderId").asText()).isEqualTo(orderId);
        assertThat(payload.get("status").asText()).isEqualTo("FAILED");
        assertThat(payload.get("failureReason").asText()).isEqualTo("Customer unavailable");
        assertThat(payload.get("failureNotes").asText()).isEqualTo("Called 3 times with no answer");
        assertThat(payload.get("recipientName").asText()).isEqualTo("Nguyen Van A");
        assertThat(payload.get("recipientPhone").asText()).isEqualTo("0909123456");

        // Verify NO sensitive PII
        assertThat(payload.has("password")).isFalse();
        assertThat(payload.has("token")).isFalse();
    }
}
