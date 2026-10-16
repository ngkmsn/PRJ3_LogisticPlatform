package com.logistics.shipment.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.logistics.common.event.KafkaTopics;
import com.logistics.shipment.dto.OrderCreatedPayload;
import com.logistics.shipment.entity.Assignment;
import com.logistics.shipment.exception.InvalidEventPayloadException;
import com.logistics.shipment.service.AssignmentService;
import com.logistics.shipment.service.InboxEventService;
import com.logistics.shipment.validator.OrderEventValidator;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletionStage;

@ApplicationScoped
public class OrderCreatedConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedConsumer.class);

    public static final String EVENT_TYPE_ORDER_CREATED = "ORDER_CREATED";
    public static final String EVENT_TYPE_ORDER_CREATED_PASCAL = "OrderCreated";

    @Inject
    AssignmentService assignmentService;

    @Inject
    InboxEventService inboxEventService;

    @Inject
    OrderEventValidator validator;

    @Inject
    ObjectMapper objectMapper;

    public OrderCreatedConsumer() {
    }

    public OrderCreatedConsumer(AssignmentService assignmentService,
                                InboxEventService inboxEventService,
                                OrderEventValidator validator,
                                ObjectMapper objectMapper) {
        this.assignmentService = assignmentService;
        this.inboxEventService = inboxEventService;
        this.validator = validator;
        this.objectMapper = objectMapper;
    }

    public OrderCreatedConsumer(AssignmentService assignmentService,
                                OrderEventValidator validator,
                                ObjectMapper objectMapper) {
        this(assignmentService, null, validator, objectMapper);
    }

    @Incoming("order-events")
    @Acknowledgment(Acknowledgment.Strategy.MANUAL)
    @Blocking
    public CompletionStage<Void> consume(Message<String> message) {
        String rawPayload = message.getPayload();
        log.info("Received raw message on channel 'order-events': {}", rawPayload);

        try {
            Assignment assignment = processOrderEvent(rawPayload);
            if (assignment != null) {
                log.info("OrderCreated event successfully processed and initiated assignment '{}' for orderId '{}'",
                        assignment.getId(), assignment.getOrderId());
            }
            // Acknowledge / commit offset only after successful processing
            return message.ack();
        } catch (InvalidEventPayloadException e) {
            log.error("Invalid event payload received, rejecting message without ACK: {}", e.getMessage());
            return message.nack(e);
        } catch (Exception e) {
            log.error("Unexpected error processing OrderCreated message, nacking for retry: {}", e.getMessage(), e);
            return message.nack(e);
        }
    }

    /**
     * Core processing method for OrderCreated events under the Inbox Pattern.
     * Deserializes DomainEvent envelope, checks event type, extracts & validates payload,
     * and delegates to inboxEventService to guarantee idempotent processing.
     *
     * @param rawJson the raw JSON message payload from Kafka
     * @return created or existing Assignment, or null if event was ignored (non-OrderCreated event)
     * @throws InvalidEventPayloadException if JSON is malformed or payload fails validation
     */
    public Assignment processOrderEvent(String rawJson) {
        if (rawJson == null || rawJson.trim().isEmpty()) {
            throw new InvalidEventPayloadException("Message payload is null or empty");
        }

        JsonNode rootNode;
        try {
            rootNode = objectMapper.readTree(rawJson);
        } catch (JsonProcessingException e) {
            throw new InvalidEventPayloadException("Failed to parse JSON message: " + e.getMessage(), e);
        }

        // Extract eventType
        JsonNode eventTypeNode = rootNode.get("eventType");
        if (eventTypeNode == null || eventTypeNode.isNull() || eventTypeNode.asText().trim().isEmpty()) {
            // Check if rootNode itself is a direct OrderCreatedPayload (fallback)
            if (rootNode.has("orderId")) {
                OrderCreatedPayload payload = deserializePayload(rootNode);
                validator.validate(payload);
                String fallbackEventId = "evt-direct-" + payload.getOrderId();
                return executeWithInboxOrDirect(fallbackEventId, EVENT_TYPE_ORDER_CREATED, payload.getOrderId(), payload);
            }
            throw new InvalidEventPayloadException("Missing mandatory 'eventType' in DomainEvent envelope");
        }

        String eventType = eventTypeNode.asText().trim();
        if (!isOrderCreatedEventType(eventType)) {
            log.info("Ignoring eventType '{}' on topic '{}'. Only ORDER_CREATED is handled for assignment initialization.",
                    eventType, KafkaTopics.ORDER_EVENTS);
            return null;
        }

        // Extract payload
        JsonNode payloadNode = rootNode.get("payload");
        if (payloadNode == null || payloadNode.isNull()) {
            throw new InvalidEventPayloadException("DomainEvent missing 'payload' object");
        }

        OrderCreatedPayload payload;
        if (payloadNode.isTextual()) {
            // In case payload was double-encoded as a JSON string
            try {
                payload = objectMapper.readValue(payloadNode.asText(), OrderCreatedPayload.class);
            } catch (JsonProcessingException e) {
                throw new InvalidEventPayloadException("Failed to deserialize nested JSON string payload: " + e.getMessage(), e);
            }
        } else {
            payload = deserializePayload(payloadNode);
        }

        // Validate payload fields
        validator.validate(payload);

        // Extract eventId from envelope (or deterministic fallback)
        JsonNode eventIdNode = rootNode.get("eventId");
        String eventId = (eventIdNode != null && !eventIdNode.isNull() && !eventIdNode.asText().trim().isEmpty())
                ? eventIdNode.asText().trim()
                : "evt-order-" + payload.getOrderId();

        // Initiate assignment workflow via Inbox Pattern
        return executeWithInboxOrDirect(eventId, eventType, payload.getOrderId(), payload);
    }

    private Assignment executeWithInboxOrDirect(String eventId, String eventType, String aggregateId, OrderCreatedPayload payload) {
        if (inboxEventService != null) {
            return inboxEventService.processOrderCreatedWithInbox(eventId, eventType, aggregateId, payload);
        }
        return assignmentService.initiateAssignment(payload);
    }

    private boolean isOrderCreatedEventType(String eventType) {
        return EVENT_TYPE_ORDER_CREATED.equalsIgnoreCase(eventType)
                || EVENT_TYPE_ORDER_CREATED_PASCAL.equalsIgnoreCase(eventType);
    }

    private OrderCreatedPayload deserializePayload(JsonNode node) {
        try {
            return objectMapper.treeToValue(node, OrderCreatedPayload.class);
        } catch (JsonProcessingException e) {
            throw new InvalidEventPayloadException("Failed to deserialize OrderCreatedPayload: " + e.getMessage(), e);
        }
    }
}
