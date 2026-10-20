package com.logistics.shipment.publisher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.logistics.common.event.DomainEvent;
import com.logistics.shipment.dto.DriverAssignedPayload;
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
}
