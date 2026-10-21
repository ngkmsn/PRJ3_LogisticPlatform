package com.logistics.shipment.publisher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.logistics.common.event.DomainEvent;
import com.logistics.shipment.dto.DriverAssignedPayload;
import com.logistics.shipment.dto.OrderPickedUpPayload;
import com.logistics.shipment.entity.Assignment;
import io.quarkus.arc.DefaultBean;
import io.smallrye.reactive.messaging.kafka.Record;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
@DefaultBean
public class KafkaDriverAssignedEventPublisher implements DriverAssignedEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaDriverAssignedEventPublisher.class);

    public static final String EVENT_TYPE_DRIVER_ASSIGNED = "DRIVER_ASSIGNED";
    public static final String EVENT_TYPE_DRIVER_REASSIGNED = "DRIVER_REASSIGNED";
    public static final String EVENT_TYPE_ORDER_PICKED_UP = "ORDER_PICKED_UP";
    public static final String EVENT_TYPE_ORDER_IN_TRANSIT = "ORDER_IN_TRANSIT";
    public static final String EVENT_TYPE_ORDER_DELIVERED = "ORDER_DELIVERED";
    public static final String EVENT_TYPE_ORDER_FAILED = "ORDER_FAILED";

    @Inject
    @Channel("shipment-events")
    Emitter<Record<String, String>> emitter;

    @Inject
    ObjectMapper objectMapper;

    public KafkaDriverAssignedEventPublisher() {
    }

    public KafkaDriverAssignedEventPublisher(Emitter<Record<String, String>> emitter, ObjectMapper objectMapper) {
        this.emitter = emitter;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publishDriverAssigned(Assignment assignment) {
        if (assignment == null) {
            log.warn("Cannot publish DRIVER_ASSIGNED event: assignment is null.");
            return;
        }

        if (assignment.getDriverId() == null || assignment.getDriverId().trim().isEmpty()) {
            log.warn("Cannot publish DRIVER_ASSIGNED event for orderId '{}': driverId is null or empty.",
                    assignment.getOrderId());
            return;
        }

        try {
            // 1. Build payload
            DriverAssignedPayload payload = DriverAssignedPayload.builder()
                    .assignmentId(assignment.getId())
                    .orderId(assignment.getOrderId())
                    .orderNumber(assignment.getOrderNumber())
                    .customerId(assignment.getCustomerId())
                    .driverId(assignment.getDriverId())
                    .status(assignment.getStatus() != null ? assignment.getStatus().name() : "ASSIGNED")
                    .assignedAt(assignment.getAssignedAt() != null ? assignment.getAssignedAt() : Instant.now())
                    .deliveryAddress(assignment.getDeliveryAddress())
                    .totalAmount(assignment.getTotalAmount())
                    .notes(assignment.getNotes())
                    .build();

            // 2. Wrap into DomainEvent envelope matching system convention (PB-022/PB-023)
            DomainEvent<DriverAssignedPayload> domainEvent = new DomainEvent<>(
                    EVENT_TYPE_DRIVER_ASSIGNED,
                    assignment.getOrderId(),
                    payload
            );
            domainEvent.setEventId(UUID.randomUUID().toString());
            domainEvent.setTimestamp(payload.getAssignedAt());

            // 3. Serialize to JSON
            String jsonMessage = objectMapper.writeValueAsString(domainEvent);

            // 4. Emit to Kafka channel with orderId as key to guarantee partition ordering
            String partitionKey = assignment.getOrderId();
            emitter.send(Record.of(partitionKey, jsonMessage))
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            log.error("Failed to publish DRIVER_ASSIGNED event for orderId '{}' (assignmentId: '{}'): {}",
                                    assignment.getOrderId(), assignment.getId(), error.getMessage(), error);
                        } else {
                            log.info("Successfully published DRIVER_ASSIGNED event '{}' for orderId '{}' (driverId: '{}', key: '{}')",
                                    domainEvent.getEventId(), assignment.getOrderId(), assignment.getDriverId(), partitionKey);
                        }
                    });

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize DRIVER_ASSIGNED event payload for orderId '{}': {}",
                    assignment.getOrderId(), e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error publishing DRIVER_ASSIGNED event for orderId '{}': {}",
                    assignment.getOrderId(), e.getMessage(), e);
        }
    }

    @Override
    public void publishDriverReassigned(Assignment assignment, String previousDriverId, String reassignReason) {
        if (assignment == null) {
            log.warn("Cannot publish DRIVER_REASSIGNED event: assignment is null.");
            return;
        }

        try {
            // 1. Build DriverReassignedPayload
            com.logistics.shipment.dto.DriverReassignedPayload payload = com.logistics.shipment.dto.DriverReassignedPayload.builder()
                    .assignmentId(assignment.getId())
                    .orderId(assignment.getOrderId())
                    .orderNumber(assignment.getOrderNumber())
                    .customerId(assignment.getCustomerId())
                    .previousDriverId(previousDriverId)
                    .newDriverId(assignment.getDriverId())
                    .reassignReason(reassignReason)
                    .reassignedAt(assignment.getReassignedAt() != null ? assignment.getReassignedAt() : Instant.now())
                    .reassignedBy(assignment.getReassignedBy())
                    .status(assignment.getStatus() != null ? assignment.getStatus().name() : "ASSIGNED")
                    .notes(assignment.getNotes())
                    .build();

            // 2. Wrap into DomainEvent envelope
            DomainEvent<com.logistics.shipment.dto.DriverReassignedPayload> domainEvent = new DomainEvent<>(
                    EVENT_TYPE_DRIVER_REASSIGNED,
                    assignment.getOrderId(),
                    payload
            );
            domainEvent.setEventId(UUID.randomUUID().toString());
            domainEvent.setTimestamp(payload.getReassignedAt());

            // 3. Serialize and emit
            String jsonMessage = objectMapper.writeValueAsString(domainEvent);
            String partitionKey = assignment.getOrderId();

            emitter.send(Record.of(partitionKey, jsonMessage))
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            log.error("Failed to publish DRIVER_REASSIGNED event for orderId '{}': {}",
                                    assignment.getOrderId(), error.getMessage(), error);
                        } else {
                            log.info("Successfully published DRIVER_REASSIGNED event '{}' for orderId '{}' (prevDriver: '{}' -> newDriver: '{}')",
                                    domainEvent.getEventId(), assignment.getOrderId(), previousDriverId, assignment.getDriverId());
                        }
                    });

            // 4. Also emit DRIVER_ASSIGNED to maintain backwards compatibility for general assignment listeners
            publishDriverAssigned(assignment);

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize DRIVER_REASSIGNED event payload for orderId '{}': {}",
                    assignment.getOrderId(), e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error publishing DRIVER_REASSIGNED event for orderId '{}': {}",
                    assignment.getOrderId(), e.getMessage(), e);
        }
    }

    @Override
    public void publishOrderPickedUp(Assignment assignment) {
        if (assignment == null) {
            log.warn("Cannot publish ORDER_PICKED_UP event: assignment is null.");
            return;
        }

        if (assignment.getOrderId() == null || assignment.getOrderId().trim().isEmpty()) {
            log.warn("Cannot publish ORDER_PICKED_UP event: orderId is null or empty.");
            return;
        }

        try {
            OrderPickedUpPayload payload = OrderPickedUpPayload.builder()
                    .assignmentId(assignment.getId())
                    .orderId(assignment.getOrderId())
                    .orderNumber(assignment.getOrderNumber())
                    .customerId(assignment.getCustomerId())
                    .driverId(assignment.getDriverId())
                    .status(assignment.getStatus() != null ? assignment.getStatus().name() : "PICKED_UP")
                    .pickedUpAt(assignment.getPickedUpAt() != null ? assignment.getPickedUpAt() : Instant.now())
                    .pickupNotes(assignment.getPickupNotes())
                    .deliveryAddress(assignment.getDeliveryAddress())
                    .totalAmount(assignment.getTotalAmount())
                    .notes(assignment.getNotes())
                    .build();

            DomainEvent<OrderPickedUpPayload> domainEvent = new DomainEvent<>(
                    EVENT_TYPE_ORDER_PICKED_UP,
                    assignment.getOrderId(),
                    payload
            );
            domainEvent.setEventId(UUID.randomUUID().toString());
            domainEvent.setTimestamp(payload.getPickedUpAt());

            String jsonMessage = objectMapper.writeValueAsString(domainEvent);
            String partitionKey = assignment.getOrderId();

            emitter.send(Record.of(partitionKey, jsonMessage))
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            log.error("Failed to publish ORDER_PICKED_UP event for orderId '{}': {}",
                                    assignment.getOrderId(), error.getMessage(), error);
                        } else {
                            log.info("Successfully published ORDER_PICKED_UP event '{}' for orderId '{}' (driverId: '{}', key: '{}')",
                                    domainEvent.getEventId(), assignment.getOrderId(), assignment.getDriverId(), partitionKey);
                        }
                    });

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize ORDER_PICKED_UP event payload for orderId '{}': {}",
                    assignment.getOrderId(), e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error publishing ORDER_PICKED_UP event for orderId '{}': {}",
                    assignment.getOrderId(), e.getMessage(), e);
        }
    }

    @Override
    public void publishOrderInTransit(Assignment assignment) {
        if (assignment == null) {
            log.warn("Cannot publish ORDER_IN_TRANSIT event: assignment is null.");
            return;
        }

        if (assignment.getOrderId() == null || assignment.getOrderId().trim().isEmpty()) {
            log.warn("Cannot publish ORDER_IN_TRANSIT event: orderId is null or empty.");
            return;
        }

        try {
            com.logistics.shipment.dto.OrderInTransitPayload payload = com.logistics.shipment.dto.OrderInTransitPayload.builder()
                    .assignmentId(assignment.getId())
                    .orderId(assignment.getOrderId())
                    .orderNumber(assignment.getOrderNumber())
                    .customerId(assignment.getCustomerId())
                    .driverId(assignment.getDriverId())
                    .status(assignment.getStatus() != null ? assignment.getStatus().name() : "IN_TRANSIT")
                    .transitStartedAt(assignment.getTransitStartedAt() != null ? assignment.getTransitStartedAt() : Instant.now())
                    .transitNotes(assignment.getTransitNotes())
                    .deliveryAddress(assignment.getDeliveryAddress())
                    .totalAmount(assignment.getTotalAmount())
                    .notes(assignment.getNotes())
                    .build();

            DomainEvent<com.logistics.shipment.dto.OrderInTransitPayload> domainEvent = new DomainEvent<>(
                    EVENT_TYPE_ORDER_IN_TRANSIT,
                    assignment.getOrderId(),
                    payload
            );
            domainEvent.setEventId(UUID.randomUUID().toString());
            domainEvent.setTimestamp(payload.getTransitStartedAt());

            String jsonMessage = objectMapper.writeValueAsString(domainEvent);
            String partitionKey = assignment.getOrderId();

            emitter.send(Record.of(partitionKey, jsonMessage))
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            log.error("Failed to publish ORDER_IN_TRANSIT event for orderId '{}': {}",
                                    assignment.getOrderId(), error.getMessage(), error);
                        } else {
                            log.info("Successfully published ORDER_IN_TRANSIT event '{}' for orderId '{}' (driverId: '{}', key: '{}')",
                                    domainEvent.getEventId(), assignment.getOrderId(), assignment.getDriverId(), partitionKey);
                        }
                    });

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize ORDER_IN_TRANSIT event payload for orderId '{}': {}",
                    assignment.getOrderId(), e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error publishing ORDER_IN_TRANSIT event for orderId '{}': {}",
                    assignment.getOrderId(), e.getMessage(), e);
        }
    }

    @Override
    public void publishOrderDelivered(Assignment assignment) {
        if (assignment == null) {
            log.warn("Cannot publish ORDER_DELIVERED event: assignment is null.");
            return;
        }

        if (assignment.getOrderId() == null || assignment.getOrderId().trim().isEmpty()) {
            log.warn("Cannot publish ORDER_DELIVERED event: orderId is null or empty.");
            return;
        }

        try {
            com.logistics.shipment.dto.OrderDeliveredPayload payload = com.logistics.shipment.dto.OrderDeliveredPayload.builder()
                    .assignmentId(assignment.getId())
                    .orderId(assignment.getOrderId())
                    .orderNumber(assignment.getOrderNumber())
                    .customerId(assignment.getCustomerId())
                    .driverId(assignment.getDriverId())
                    .status(assignment.getStatus() != null ? assignment.getStatus().name() : "DELIVERED")
                    .deliveredAt(assignment.getDeliveredAt() != null ? assignment.getDeliveredAt() : Instant.now())
                    .deliveryNotes(assignment.getDeliveryNotes())
                    .proofOfDelivery(assignment.getProofOfDelivery())
                    .deliveryAddress(assignment.getDeliveryAddress())
                    .totalAmount(assignment.getTotalAmount())
                    .notes(assignment.getNotes())
                    .build();

            DomainEvent<com.logistics.shipment.dto.OrderDeliveredPayload> domainEvent = new DomainEvent<>(
                    EVENT_TYPE_ORDER_DELIVERED,
                    assignment.getOrderId(),
                    payload
            );
            domainEvent.setEventId(UUID.randomUUID().toString());
            domainEvent.setTimestamp(payload.getDeliveredAt());

            String jsonMessage = objectMapper.writeValueAsString(domainEvent);
            String partitionKey = assignment.getOrderId();

            emitter.send(Record.of(partitionKey, jsonMessage))
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            log.error("Failed to publish ORDER_DELIVERED event for orderId '{}': {}",
                                    assignment.getOrderId(), error.getMessage(), error);
                        } else {
                            log.info("Successfully published ORDER_DELIVERED event '{}' for orderId '{}' (driverId: '{}', key: '{}')",
                                    domainEvent.getEventId(), assignment.getOrderId(), assignment.getDriverId(), partitionKey);
                        }
                    });

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize ORDER_DELIVERED event payload for orderId '{}': {}",
                    assignment.getOrderId(), e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error publishing ORDER_DELIVERED event for orderId '{}': {}",
                    assignment.getOrderId(), e.getMessage(), e);
        }
    }

    @Override
    public void publishOrderFailed(Assignment assignment) {
        if (assignment == null) {
            log.warn("Cannot publish ORDER_FAILED event: assignment is null.");
            return;
        }

        if (assignment.getOrderId() == null || assignment.getOrderId().trim().isEmpty()) {
            log.warn("Cannot publish ORDER_FAILED event: orderId is null or empty.");
            return;
        }

        try {
            com.logistics.shipment.dto.OrderFailedPayload payload = com.logistics.shipment.dto.OrderFailedPayload.builder()
                    .assignmentId(assignment.getId())
                    .orderId(assignment.getOrderId())
                    .orderNumber(assignment.getOrderNumber())
                    .customerId(assignment.getCustomerId())
                    .driverId(assignment.getDriverId())
                    .status(assignment.getStatus() != null ? assignment.getStatus().name() : "FAILED")
                    .failedAt(assignment.getFailedAt() != null ? assignment.getFailedAt() : Instant.now())
                    .failureReason(assignment.getFailureReason())
                    .failureNotes(assignment.getFailureNotes())
                    .deliveryAddress(assignment.getDeliveryAddress())
                    .totalAmount(assignment.getTotalAmount())
                    .notes(assignment.getNotes())
                    .build();

            DomainEvent<com.logistics.shipment.dto.OrderFailedPayload> domainEvent = new DomainEvent<>(
                    EVENT_TYPE_ORDER_FAILED,
                    assignment.getOrderId(),
                    payload
            );
            domainEvent.setEventId(UUID.randomUUID().toString());
            domainEvent.setTimestamp(payload.getFailedAt());

            String jsonMessage = objectMapper.writeValueAsString(domainEvent);
            String partitionKey = assignment.getOrderId();

            emitter.send(Record.of(partitionKey, jsonMessage))
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            log.error("Failed to publish ORDER_FAILED event for orderId '{}': {}",
                                    assignment.getOrderId(), error.getMessage(), error);
                        } else {
                            log.info("Successfully published ORDER_FAILED event '{}' for orderId '{}' (driverId: '{}', key: '{}')",
                                    domainEvent.getEventId(), assignment.getOrderId(), assignment.getDriverId(), partitionKey);
                        }
                    });

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize ORDER_FAILED event payload for orderId '{}': {}",
                    assignment.getOrderId(), e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error publishing ORDER_FAILED event for orderId '{}': {}",
                    assignment.getOrderId(), e.getMessage(), e);
        }
    }
}



