package com.logistics.shipment.publisher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.logistics.common.event.DomainEvent;
import com.logistics.shipment.dto.DriverAssignedPayload;
import com.logistics.shipment.dto.DriverReassignedPayload;
import com.logistics.shipment.dto.OrderDeliveredPayload;
import com.logistics.shipment.dto.OrderFailedPayload;
import com.logistics.shipment.dto.OrderInTransitPayload;
import com.logistics.shipment.dto.OrderPickedUpPayload;
import com.logistics.shipment.entity.Assignment;
import com.logistics.shipment.entity.OutboxEvent;
import com.logistics.shipment.repository.OutboxEventRepository;
import io.quarkus.arc.DefaultBean;
import io.smallrye.reactive.messaging.kafka.Record;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;
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

    @Inject
    OutboxEventRepository outboxEventRepository;

    @Inject
    TransactionSynchronizationRegistry transactionSynchronizationRegistry;

    public KafkaDriverAssignedEventPublisher() {
    }

    public KafkaDriverAssignedEventPublisher(Emitter<Record<String, String>> emitter, ObjectMapper objectMapper) {
        this.emitter = emitter;
        this.objectMapper = objectMapper;
    }

    public KafkaDriverAssignedEventPublisher(Emitter<Record<String, String>> emitter,
                                             ObjectMapper objectMapper,
                                             OutboxEventRepository outboxEventRepository) {
        this.emitter = emitter;
        this.objectMapper = objectMapper;
        this.outboxEventRepository = outboxEventRepository;
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

        DriverAssignedPayload payload = DriverAssignedPayload.builder()
                .assignmentId(assignment.getId())
                .orderId(assignment.getOrderId())
                .orderNumber(assignment.getOrderNumber())
                .customerId(assignment.getCustomerId())
                .recipientName(assignment.getRecipientName())
                .recipientPhone(assignment.getRecipientPhone())
                .driverId(assignment.getDriverId())
                .status(assignment.getStatus() != null ? assignment.getStatus().name() : "ASSIGNED")
                .assignedAt(assignment.getAssignedAt() != null ? assignment.getAssignedAt() : Instant.now())
                .deliveryAddress(assignment.getDeliveryAddress())
                .totalAmount(assignment.getTotalAmount())
                .notes(assignment.getNotes())
                .build();

        publishEvent(EVENT_TYPE_DRIVER_ASSIGNED, assignment.getOrderId(), payload.getAssignedAt(), payload);
    }

    @Override
    public void publishDriverReassigned(Assignment assignment, String previousDriverId, String reassignReason) {
        if (assignment == null) {
            log.warn("Cannot publish DRIVER_REASSIGNED event: assignment is null.");
            return;
        }

        DriverReassignedPayload payload = DriverReassignedPayload.builder()
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

        publishEvent(EVENT_TYPE_DRIVER_REASSIGNED, assignment.getOrderId(), payload.getReassignedAt(), payload);

        // Also emit DRIVER_ASSIGNED to maintain backwards compatibility for listeners
        publishDriverAssigned(assignment);
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

        publishEvent(EVENT_TYPE_ORDER_PICKED_UP, assignment.getOrderId(), payload.getPickedUpAt(), payload);
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

        OrderInTransitPayload payload = OrderInTransitPayload.builder()
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

        publishEvent(EVENT_TYPE_ORDER_IN_TRANSIT, assignment.getOrderId(), payload.getTransitStartedAt(), payload);
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

        OrderDeliveredPayload payload = OrderDeliveredPayload.builder()
                .assignmentId(assignment.getId())
                .orderId(assignment.getOrderId())
                .orderNumber(assignment.getOrderNumber())
                .customerId(assignment.getCustomerId())
                .recipientName(assignment.getRecipientName())
                .recipientPhone(assignment.getRecipientPhone())
                .driverId(assignment.getDriverId())
                .status(assignment.getStatus() != null ? assignment.getStatus().name() : "DELIVERED")
                .deliveredAt(assignment.getDeliveredAt() != null ? assignment.getDeliveredAt() : Instant.now())
                .deliveryNotes(assignment.getDeliveryNotes())
                .proofOfDelivery(assignment.getProofOfDelivery())
                .deliveryAddress(assignment.getDeliveryAddress())
                .totalAmount(assignment.getTotalAmount())
                .notes(assignment.getNotes())
                .build();

        publishEvent(EVENT_TYPE_ORDER_DELIVERED, assignment.getOrderId(), payload.getDeliveredAt(), payload);
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

        OrderFailedPayload payload = OrderFailedPayload.builder()
                .assignmentId(assignment.getId())
                .orderId(assignment.getOrderId())
                .orderNumber(assignment.getOrderNumber())
                .customerId(assignment.getCustomerId())
                .recipientName(assignment.getRecipientName())
                .recipientPhone(assignment.getRecipientPhone())
                .driverId(assignment.getDriverId())
                .status(assignment.getStatus() != null ? assignment.getStatus().name() : "FAILED")
                .failedAt(assignment.getFailedAt() != null ? assignment.getFailedAt() : Instant.now())
                .failureReason(assignment.getFailureReason())
                .failureNotes(assignment.getFailureNotes())
                .deliveryAddress(assignment.getDeliveryAddress())
                .totalAmount(assignment.getTotalAmount())
                .notes(assignment.getNotes())
                .build();

        publishEvent(EVENT_TYPE_ORDER_FAILED, assignment.getOrderId(), payload.getFailedAt(), payload);
    }

    /**
     * Standardized event publisher using Transactional Outbox pattern when active JTA transaction exists.
     * Guarantees that:
     * 1. Outbox record is persisted in the SAME transaction as the domain state change.
     * 2. If the transaction rolls back or fails, NO event is published.
     * 3. Once committed, event is published asynchronously to Kafka with orderId key.
     * 4. Downstream consumers can handle event idempotently via eventId.
     */
    private <T> void publishEvent(String eventType, String orderId, Instant timestamp, T payload) {
        try {
            DomainEvent<T> domainEvent = new DomainEvent<>(eventType, orderId, payload);
            domainEvent.setEventId(UUID.randomUUID().toString());
            domainEvent.setTimestamp(timestamp != null ? timestamp : Instant.now());

            String jsonMessage = objectMapper != null ? objectMapper.writeValueAsString(domainEvent) : "";
            String eventId = domainEvent.getEventId();

            boolean txActive = false;
            try {
                if (transactionSynchronizationRegistry != null &&
                        transactionSynchronizationRegistry.getTransactionStatus() == Status.STATUS_ACTIVE) {
                    txActive = true;
                }
            } catch (Exception e) {
                txActive = false;
            }

            if (outboxEventRepository != null && txActive) {
                OutboxEvent outboxEvent = new OutboxEvent(
                        "SHIPMENT",
                        orderId,
                        eventType,
                        jsonMessage
                );
                outboxEvent.setId(eventId);
                outboxEvent.setOccurredAt(domainEvent.getTimestamp());
                outboxEventRepository.persist(outboxEvent);

                transactionSynchronizationRegistry.registerInterposedSynchronization(new Synchronization() {
                    @Override
                    public void beforeCompletion() {
                    }

                    @Override
                    public void afterCompletion(int status) {
                        if (status == Status.STATUS_COMMITTED) {
                            emitToKafka(orderId, jsonMessage, eventId, eventType);
                        } else {
                            log.warn("Transaction rolled back or failed (status={}); skipping Kafka emission for event '{}' ({})",
                                    status, eventId, eventType);
                        }
                    }
                });

                log.info("Persisted transactional outbox event '{}' ({}) for orderId '{}' (post-commit publish scheduled)",
                        eventId, eventType, orderId);
            } else {
                emitToKafka(orderId, jsonMessage, eventId, eventType);
            }

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize {} event payload for orderId '{}': {}",
                    eventType, orderId, e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error preparing {} event for orderId '{}': {}",
                    eventType, orderId, e.getMessage(), e);
        }
    }

    private void emitToKafka(String partitionKey, String jsonMessage, String eventId, String eventType) {
        if (emitter == null) {
            log.warn("Emitter is null, skipping emit for event '{}' ({})", eventId, eventType);
            return;
        }

        try {
            emitter.send(Record.of(partitionKey, jsonMessage))
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            log.error("Failed to publish {} event '{}' for orderId '{}': {}",
                                    eventType, eventId, partitionKey, error.getMessage(), error);
                            if (outboxEventRepository != null) {
                                try {
                                    io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().run(() ->
                                            outboxEventRepository.recordPublishFailure(eventId, error.getMessage(), 5)
                                    );
                                } catch (Exception ex) {
                                    log.warn("Could not record publish failure for event '{}': {}", eventId, ex.getMessage());
                                }
                            }
                        } else {
                            log.info("Successfully published {} event '{}' for orderId '{}' (key: '{}')",
                                    eventType, eventId, partitionKey, partitionKey);
                            if (outboxEventRepository != null) {
                                try {
                                    io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().run(() ->
                                            outboxEventRepository.markPublished(eventId, Instant.now())
                                    );
                                } catch (Exception ex) {
                                    log.warn("Could not mark event '{}' as published: {}", eventId, ex.getMessage());
                                }
                            }
                        }
                    });
        } catch (Exception e) {
            log.error("Immediate error emitting {} event '{}' to Kafka: {}", eventType, eventId, e.getMessage(), e);
            if (outboxEventRepository != null) {
                try {
                    outboxEventRepository.recordPublishFailure(eventId, e.getMessage(), 5);
                } catch (Exception ex) {
                    log.warn("Could not record immediate publish failure for event '{}': {}", eventId, ex.getMessage());
                }
            }
        }
    }
}
