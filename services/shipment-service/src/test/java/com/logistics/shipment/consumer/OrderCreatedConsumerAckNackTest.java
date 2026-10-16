package com.logistics.shipment.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logistics.shipment.dto.OrderCreatedPayload;
import com.logistics.shipment.entity.Assignment;
import com.logistics.shipment.model.AssignmentStatus;
import com.logistics.shipment.service.AssignmentService;
import com.logistics.shipment.service.InboxEventService;
import com.logistics.shipment.validator.OrderEventValidator;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderCreatedConsumerAckNackTest {

    @Mock
    private AssignmentService assignmentService;

    @Mock
    private InboxEventService inboxEventService;

    @Mock
    private Message<String> message;

    private OrderCreatedConsumer consumer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        OrderEventValidator validator = new OrderEventValidator();
        consumer = new OrderCreatedConsumer(assignmentService, inboxEventService, validator, objectMapper);
    }

    @Test
    @DisplayName("consume: Valid ORDER_CREATED event processes via Inbox and invokes message.ack()")
    void consume_validPayload_acknowledgesMessage() {
        String eventId = "evt-ack-1";
        String orderId = "ord-ack-" + UUID.randomUUID();
        String validJson = """
                {
                    "eventId": "%s",
                    "eventType": "ORDER_CREATED",
                    "aggregateId": "%s",
                    "payload": {
                        "orderId": "%s",
                        "orderNumber": "ORD-123",
                        "customerId": "cust-1",
                        "deliveryAddress": "Address 1",
                        "totalAmount": 100.00
                    }
                }
                """.formatted(eventId, orderId, orderId);

        when(message.getPayload()).thenReturn(validJson);
        when(message.ack()).thenReturn(CompletableFuture.completedFuture(null));

        Assignment mockAssignment = new Assignment(
                UUID.randomUUID().toString(),
                orderId,
                "ORD-123",
                "cust-1",
                null,
                null,
                "Address 1",
                BigDecimal.valueOf(100.00),
                AssignmentStatus.PENDING,
                null,
                null,
                Instant.now(),
                Instant.now()
        );
        when(inboxEventService.processOrderCreatedWithInbox(eq(eventId), eq("ORDER_CREATED"), eq(orderId), any(OrderCreatedPayload.class)))
                .thenReturn(mockAssignment);

        CompletionStage<Void> stage = consumer.consume(message);

        assertThat(stage).isNotNull();
        verify(message).ack();
        verify(message, never()).nack(any());
        verify(inboxEventService).processOrderCreatedWithInbox(eq(eventId), eq("ORDER_CREATED"), eq(orderId), any(OrderCreatedPayload.class));
    }

    @Test
    @DisplayName("consume: Duplicate event redelivery processed via Inbox still invokes message.ack()")
    void consume_duplicateEvent_acknowledgesMessage() {
        String eventId = "evt-ack-dup";
        String orderId = "ord-ack-dup";
        String validJson = """
                {
                    "eventId": "%s",
                    "eventType": "ORDER_CREATED",
                    "aggregateId": "%s",
                    "payload": {
                        "orderId": "%s",
                        "orderNumber": "ORD-123",
                        "customerId": "cust-1",
                        "deliveryAddress": "Address 1"
                    }
                }
                """.formatted(eventId, orderId, orderId);

        when(message.getPayload()).thenReturn(validJson);
        when(message.ack()).thenReturn(CompletableFuture.completedFuture(null));

        Assignment existingAssignment = new Assignment();
        existingAssignment.setId("existing-id");
        existingAssignment.setOrderId(orderId);

        when(inboxEventService.processOrderCreatedWithInbox(eq(eventId), eq("ORDER_CREATED"), eq(orderId), any(OrderCreatedPayload.class)))
                .thenReturn(existingAssignment);

        CompletionStage<Void> stage = consumer.consume(message);

        assertThat(stage).isNotNull();
        verify(message).ack();
        verify(message, never()).nack(any());
    }

    @Test
    @DisplayName("consume: Invalid payload (missing orderId) invokes message.nack() and does NOT invoke message.ack()")
    void consume_invalidPayload_nacksMessage() {
        String invalidJson = """
                {
                    "eventType": "ORDER_CREATED",
                    "aggregateId": "ord-123",
                    "payload": {
                        "customerId": "cust-1",
                        "deliveryAddress": "Address 1"
                    }
                }
                """;

        when(message.getPayload()).thenReturn(invalidJson);
        when(message.nack(any())).thenReturn(CompletableFuture.completedFuture(null));

        CompletionStage<Void> stage = consumer.consume(message);

        assertThat(stage).isNotNull();
        verify(message).nack(any(Throwable.class));
        verify(message, never()).ack();
        verify(inboxEventService, never()).processOrderCreatedWithInbox(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("consume: Processing failure (runtime exception in service) invokes message.nack() and preserves event")
    void consume_processingFailure_nacksMessage() {
        String eventId = "evt-err-1";
        String orderId = "ord-err-" + UUID.randomUUID();
        String validJson = """
                {
                    "eventId": "%s",
                    "eventType": "ORDER_CREATED",
                    "aggregateId": "%s",
                    "payload": {
                        "orderId": "%s",
                        "customerId": "cust-1",
                        "deliveryAddress": "Address 1"
                    }
                }
                """.formatted(eventId, orderId, orderId);

        when(message.getPayload()).thenReturn(validJson);
        when(message.nack(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(inboxEventService.processOrderCreatedWithInbox(eq(eventId), eq("ORDER_CREATED"), eq(orderId), any(OrderCreatedPayload.class)))
                .thenThrow(new RuntimeException("Simulated Database connection error during transaction"));

        CompletionStage<Void> stage = consumer.consume(message);

        assertThat(stage).isNotNull();
        verify(message).nack(any(Throwable.class));
        verify(message, never()).ack();
    }

    @Test
    @DisplayName("consume: Ignored event type (e.g. ORDER_CANCELLED) invokes message.ack() to avoid topic stall")
    void consume_ignoredEventType_acknowledgesMessage() {
        String orderId = "ord-cancel-" + UUID.randomUUID();
        String cancelJson = """
                {
                    "eventType": "ORDER_CANCELLED",
                    "aggregateId": "%s",
                    "payload": {
                        "orderId": "%s"
                    }
                }
                """.formatted(orderId, orderId);

        when(message.getPayload()).thenReturn(cancelJson);
        when(message.ack()).thenReturn(CompletableFuture.completedFuture(null));

        CompletionStage<Void> stage = consumer.consume(message);

        assertThat(stage).isNotNull();
        verify(message).ack();
        verify(message, never()).nack(any());
        verify(inboxEventService, never()).processOrderCreatedWithInbox(anyString(), anyString(), anyString(), any());
    }
}
