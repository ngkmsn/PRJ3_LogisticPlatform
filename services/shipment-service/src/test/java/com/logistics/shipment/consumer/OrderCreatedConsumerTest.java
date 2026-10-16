package com.logistics.shipment.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logistics.shipment.entity.Assignment;
import com.logistics.shipment.exception.InvalidEventPayloadException;
import com.logistics.shipment.model.AssignmentStatus;
import com.logistics.shipment.repository.AssignmentRepository;
import com.logistics.shipment.service.AssignmentService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@QuarkusTest
class OrderCreatedConsumerTest {

    @Inject
    OrderCreatedConsumer consumer;

    @Inject
    AssignmentService assignmentService;

    @Inject
    AssignmentRepository assignmentRepository;

    @Inject
    ObjectMapper objectMapper;

    @BeforeEach
    @Transactional
    void cleanUp() {
        assignmentRepository.deleteAll();
    }

    private String buildValidEventJson(String orderId, String eventType) {
        return """
                {
                    "eventId": "%s",
                    "eventType": "%s",
                    "aggregateId": "%s",
                    "timestamp": "2026-10-16T08:00:00Z",
                    "payload": {
                        "orderId": "%s",
                        "orderNumber": "ORD-20261016-0001",
                        "customerId": "cust-uuid-111",
                        "recipientName": "Lê Văn C",
                        "recipientPhone": "0987654321",
                        "deliveryAddress": "789 Điện Biên Phủ, Bình Thạnh, TP.HCM",
                        "status": "PENDING",
                        "totalAmount": 350000.00,
                        "notes": "Hàng dễ vỡ xin nhẹ tay",
                        "createdAt": "2026-10-16T08:00:00Z"
                    }
                }
                """.formatted(UUID.randomUUID(), eventType, orderId, orderId);
    }

    @Test
    @DisplayName("processOrderEvent consumes valid ORDER_CREATED event and creates Assignment in DB")
    void processOrderEvent_validOrderCreated_success() {
        String orderId = "ord-valid-" + UUID.randomUUID();
        String json = buildValidEventJson(orderId, "ORDER_CREATED");

        Assignment result = consumer.processOrderEvent(json);

        assertThat(result).isNotNull();
        assertThat(result.getOrderId()).isEqualTo(orderId);
        assertThat(result.getOrderNumber()).isEqualTo("ORD-20261016-0001");
        assertThat(result.getCustomerId()).isEqualTo("cust-uuid-111");
        assertThat(result.getRecipientName()).isEqualTo("Lê Văn C");
        assertThat(result.getDeliveryAddress()).isEqualTo("789 Điện Biên Phủ, Bình Thạnh, TP.HCM");
        assertThat(result.getStatus()).isEqualTo(AssignmentStatus.PENDING);

        // Verify persisted in DB
        Assignment persisted = assignmentRepository.findByOrderId(orderId).orElse(null);
        assertThat(persisted).isNotNull();
        assertThat(persisted.getId()).isEqualTo(result.getId());
    }

    @Test
    @DisplayName("processOrderEvent consumes PascalCase 'OrderCreated' event successfully")
    void processOrderEvent_pascalCaseOrderCreated_success() {
        String orderId = "ord-pascal-" + UUID.randomUUID();
        String json = buildValidEventJson(orderId, "OrderCreated");

        Assignment result = consumer.processOrderEvent(json);

        assertThat(result).isNotNull();
        assertThat(result.getOrderId()).isEqualTo(orderId);
    }

    @Test
    @DisplayName("processOrderEvent is idempotent on duplicate event deliveries")
    void processOrderEvent_idempotent_duplicateDelivery() {
        String orderId = "ord-dup-consumer-" + UUID.randomUUID();
        String json = buildValidEventJson(orderId, "ORDER_CREATED");

        Assignment first = consumer.processOrderEvent(json);
        Assignment second = consumer.processOrderEvent(json);

        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(assignmentRepository.count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("processOrderEvent gracefully ignores non-OrderCreated events without creating assignment")
    void processOrderEvent_otherEventType_ignored() {
        String orderId = "ord-cancelled-" + UUID.randomUUID();
        String json = buildValidEventJson(orderId, "ORDER_CANCELLED");

        Assignment result = consumer.processOrderEvent(json);

        assertThat(result).isNull();
        assertThat(assignmentRepository.count()).isEqualTo(0L);
    }

    @Test
    @DisplayName("processOrderEvent handles direct payload without envelope (fallback)")
    void processOrderEvent_directPayload_success() {
        String orderId = "ord-direct-" + UUID.randomUUID();
        String json = """
                {
                    "orderId": "%s",
                    "orderNumber": "ORD-DIRECT-01",
                    "customerId": "cust-direct",
                    "deliveryAddress": "Direct address"
                }
                """.formatted(orderId);

        Assignment result = consumer.processOrderEvent(json);

        assertThat(result).isNotNull();
        assertThat(result.getOrderId()).isEqualTo(orderId);
    }

    @Test
    @DisplayName("processOrderEvent handles double-encoded stringified payload")
    void processOrderEvent_doubleEncodedStringPayload_success() {
        String orderId = "ord-nested-str-" + UUID.randomUUID();
        String json = """
                {
                    "eventType": "ORDER_CREATED",
                    "aggregateId": "%s",
                    "payload": "{\\"orderId\\":\\"%s\\",\\"orderNumber\\":\\"ORD-NESTED\\",\\"customerId\\":\\"cust-nested\\",\\"deliveryAddress\\":\\"Nested Address\\"}"
                }
                """.formatted(orderId, orderId);

        Assignment result = consumer.processOrderEvent(json);

        assertThat(result).isNotNull();
        assertThat(result.getOrderId()).isEqualTo(orderId);
    }

    @Test
    @DisplayName("processOrderEvent throws InvalidEventPayloadException for null or empty input")
    void processOrderEvent_emptyInput_throwsException() {
        assertThatThrownBy(() -> consumer.processOrderEvent(null))
                .isInstanceOf(InvalidEventPayloadException.class)
                .hasMessageContaining("Message payload is null or empty");

        assertThatThrownBy(() -> consumer.processOrderEvent("   "))
                .isInstanceOf(InvalidEventPayloadException.class);
    }

    @Test
    @DisplayName("processOrderEvent throws InvalidEventPayloadException for malformed JSON")
    void processOrderEvent_malformedJson_throwsException() {
        assertThatThrownBy(() -> consumer.processOrderEvent("{invalid-json-content: true,"))
                .isInstanceOf(InvalidEventPayloadException.class)
                .hasMessageContaining("Failed to parse JSON");
    }

    @Test
    @DisplayName("processOrderEvent throws InvalidEventPayloadException when envelope has no eventType and no orderId")
    void processOrderEvent_missingEventType_throwsException() {
        String json = """
                {
                    "eventId": "evt-123",
                    "timestamp": "2026-10-16T08:00:00Z"
                }
                """;
        assertThatThrownBy(() -> consumer.processOrderEvent(json))
                .isInstanceOf(InvalidEventPayloadException.class)
                .hasMessageContaining("Missing mandatory 'eventType'");
    }

    @Test
    @DisplayName("processOrderEvent throws InvalidEventPayloadException when payload object is missing")
    void processOrderEvent_missingPayloadObject_throwsException() {
        String json = """
                {
                    "eventType": "ORDER_CREATED",
                    "aggregateId": "ord-123"
                }
                """;
        assertThatThrownBy(() -> consumer.processOrderEvent(json))
                .isInstanceOf(InvalidEventPayloadException.class)
                .hasMessageContaining("DomainEvent missing 'payload' object");
    }

    @Test
    @DisplayName("processOrderEvent throws InvalidEventPayloadException when mandatory fields in payload are missing")
    void processOrderEvent_missingMandatoryPayloadFields_throwsException() {
        String jsonMissingOrderId = """
                {
                    "eventType": "ORDER_CREATED",
                    "aggregateId": "ord-123",
                    "payload": {
                        "customerId": "cust-1",
                        "deliveryAddress": "Address"
                    }
                }
                """;
        assertThatThrownBy(() -> consumer.processOrderEvent(jsonMissingOrderId))
                .isInstanceOf(InvalidEventPayloadException.class)
                .hasMessageContaining("missing mandatory 'orderId'");

        String jsonMissingCustomerId = """
                {
                    "eventType": "ORDER_CREATED",
                    "aggregateId": "ord-123",
                    "payload": {
                        "orderId": "ord-123",
                        "deliveryAddress": "Address"
                    }
                }
                """;
        assertThatThrownBy(() -> consumer.processOrderEvent(jsonMissingCustomerId))
                .isInstanceOf(InvalidEventPayloadException.class)
                .hasMessageContaining("missing mandatory 'customerId'");

        String jsonMissingAddress = """
                {
                    "eventType": "ORDER_CREATED",
                    "aggregateId": "ord-123",
                    "payload": {
                        "orderId": "ord-123",
                        "customerId": "cust-1"
                    }
                }
                """;
        assertThatThrownBy(() -> consumer.processOrderEvent(jsonMissingAddress))
                .isInstanceOf(InvalidEventPayloadException.class)
                .hasMessageContaining("missing mandatory 'deliveryAddress'");
    }

    @Test
    @DisplayName("processOrderEvent throws InvalidEventPayloadException when totalAmount is negative")
    void processOrderEvent_negativeTotalAmount_throwsException() {
        String json = """
                {
                    "eventType": "ORDER_CREATED",
                    "aggregateId": "ord-123",
                    "payload": {
                        "orderId": "ord-123",
                        "customerId": "cust-1",
                        "deliveryAddress": "Address",
                        "totalAmount": -50.00
                    }
                }
                """;
        assertThatThrownBy(() -> consumer.processOrderEvent(json))
                .isInstanceOf(InvalidEventPayloadException.class)
                .hasMessageContaining("totalAmount cannot be negative");
    }
}
