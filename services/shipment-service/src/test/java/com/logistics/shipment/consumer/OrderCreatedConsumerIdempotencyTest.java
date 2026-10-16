package com.logistics.shipment.consumer;

import com.logistics.shipment.entity.Assignment;
import com.logistics.shipment.repository.AssignmentRepository;
import com.logistics.shipment.repository.InboxEventRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class OrderCreatedConsumerIdempotencyTest {

    @Inject
    OrderCreatedConsumer consumer;

    @Inject
    AssignmentRepository assignmentRepository;

    @Inject
    InboxEventRepository inboxEventRepository;

    @BeforeEach
    @Transactional
    void cleanUp() {
        inboxEventRepository.deleteAll();
        assignmentRepository.deleteAll();
    }

    private String buildEventJson(String eventId, String orderId) {
        return """
                {
                    "eventId": "%s",
                    "eventType": "ORDER_CREATED",
                    "aggregateId": "%s",
                    "timestamp": "2026-10-16T09:00:00Z",
                    "payload": {
                        "orderId": "%s",
                        "orderNumber": "ORD-IDEMP-001",
                        "customerId": "cust-idemp-101",
                        "recipientName": "Vũ Đình G",
                        "recipientPhone": "0988776655",
                        "deliveryAddress": "50 Hàm Nghi, Quận 1, TP.HCM",
                        "status": "PENDING",
                        "totalAmount": 520000.00,
                        "notes": "Idempotent consumer verification",
                        "createdAt": "2026-10-16T09:00:00Z"
                    }
                }
                """.formatted(eventId, orderId, orderId);
    }

    @Test
    @DisplayName("PB-025: End-to-end Idempotent Consumer test - duplicate Kafka event redelivery is skipped via Inbox")
    void processOrderEvent_duplicateKafkaMessage_handledIdempotentlyViaInbox() {
        String eventId = "evt-kafka-dup-" + UUID.randomUUID();
        String orderId = "ord-kafka-dup-" + UUID.randomUUID();
        String messageJson = buildEventJson(eventId, orderId);

        // First delivery: should process and record in inbox
        Assignment first = consumer.processOrderEvent(messageJson);
        assertThat(first).isNotNull();
        assertThat(first.getOrderId()).isEqualTo(orderId);

        assertThat(assignmentRepository.count()).isEqualTo(1L);
        assertThat(inboxEventRepository.count()).isEqualTo(1L);
        assertThat(inboxEventRepository.findByEventId(eventId)).isPresent();

        // Second delivery (simulating Kafka at-least-once redelivery with identical eventId):
        Assignment second = consumer.processOrderEvent(messageJson);
        assertThat(second).isNotNull();
        assertThat(second.getId()).isEqualTo(first.getId());

        // Assert DB counts remained exactly 1
        assertThat(assignmentRepository.count()).isEqualTo(1L);
        assertThat(inboxEventRepository.count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Distinct events with different eventIds are each processed and recorded in Inbox")
    void processOrderEvent_distinctEvents_bothProcessed() {
        String eventId1 = "evt-distinct-1-" + UUID.randomUUID();
        String orderId1 = "ord-distinct-1-" + UUID.randomUUID();

        String eventId2 = "evt-distinct-2-" + UUID.randomUUID();
        String orderId2 = "ord-distinct-2-" + UUID.randomUUID();

        Assignment first = consumer.processOrderEvent(buildEventJson(eventId1, orderId1));
        Assignment second = consumer.processOrderEvent(buildEventJson(eventId2, orderId2));

        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        assertThat(first.getId()).isNotEqualTo(second.getId());

        assertThat(assignmentRepository.count()).isEqualTo(2L);
        assertThat(inboxEventRepository.count()).isEqualTo(2L);
    }
}
