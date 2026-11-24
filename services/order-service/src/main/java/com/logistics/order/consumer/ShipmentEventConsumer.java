package com.logistics.order.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.logistics.order.service.OrderService;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Consumes delivery events from shipment-service (topic logistics.shipment.events) and mirrors
 * the delivery progress onto the order status, so the customer sees ASSIGNED / IN_TRANSIT / DELIVERED.
 *
 * Events are never re-thrown: a malformed or unknown event is logged and skipped so it cannot block
 * the partition. Duplicate deliveries are harmless because {@link OrderService#applyShipmentEvent}
 * is idempotent by state.
 */
@ApplicationScoped
public class ShipmentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ShipmentEventConsumer.class);

    @Inject
    OrderService orderService;

    @Inject
    ObjectMapper objectMapper;

    @Incoming("shipment-events")
    @Blocking
    public void consume(String rawMessage) {
        process(rawMessage);
    }

    /** @return true when the order status changed */
    public boolean process(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return false;
        }
        try {
            JsonNode root = objectMapper.readTree(rawMessage);
            String eventType = text(root, "eventType");
            JsonNode payload = root.path("payload");

            String orderId = text(payload, "orderId");
            if (orderId == null) {
                orderId = text(root, "aggregateId");
            }
            String reason = text(payload, "failureReason");
            if (reason == null) {
                reason = text(payload, "reason");
            }

            if (eventType == null || orderId == null) {
                log.warn("Skipping shipment event without eventType/orderId: {}", abbreviate(rawMessage));
                return false;
            }
            return orderService.applyShipmentEvent(orderId, eventType, reason);
        } catch (Exception e) {
            log.error("Failed to process shipment event, skipping: {} ({})", abbreviate(rawMessage), e.getMessage(), e);
            return false;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String s = value.asText();
        return s.isBlank() ? null : s.trim();
    }

    private static String abbreviate(String s) {
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }
}
