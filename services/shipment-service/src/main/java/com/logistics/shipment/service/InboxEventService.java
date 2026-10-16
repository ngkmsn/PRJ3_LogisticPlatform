package com.logistics.shipment.service;

import com.logistics.shipment.dto.OrderCreatedPayload;
import com.logistics.shipment.entity.Assignment;
import com.logistics.shipment.entity.InboxEvent;
import com.logistics.shipment.model.InboxStatus;
import com.logistics.shipment.repository.AssignmentRepository;
import com.logistics.shipment.repository.InboxEventRepository;
import io.quarkus.arc.DefaultBean;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@DefaultBean
public class InboxEventService {

    private static final Logger log = LoggerFactory.getLogger(InboxEventService.class);

    @Inject
    InboxEventRepository inboxEventRepository;

    @Inject
    AssignmentRepository assignmentRepository;

    @Inject
    AssignmentService assignmentService;

    public InboxEventService() {
    }

    public InboxEventService(InboxEventRepository inboxEventRepository,
                             AssignmentRepository assignmentRepository,
                             AssignmentService assignmentService) {
        this.inboxEventRepository = inboxEventRepository;
        this.assignmentRepository = assignmentRepository;
        this.assignmentService = assignmentService;
    }

    /**
     * Checks if the event has already been recorded and processed in the Inbox.
     *
     * @param eventId the unique event identifier
     * @return true if the event has already been successfully processed
     */
    public boolean isAlreadyProcessed(String eventId) {
        if (eventId == null || eventId.trim().isEmpty()) {
            return false;
        }
        return inboxEventRepository.isEventProcessed(eventId);
    }

    /**
     * Processes an incoming OrderCreated event under the Inbox Pattern.
     * If the event was already processed, skips business execution.
     * If a concurrent duplicate arrives, the DB unique constraint prevents duplicate creation,
     * catches the constraint violation, and gracefully returns the existing assignment.
     *
     * @param eventId     unique event ID from message/payload
     * @param eventType   event type (e.g. ORDER_CREATED)
     * @param aggregateId aggregate identifier (e.g. orderId)
     * @param payload     order created payload
     * @return created or existing Assignment
     */
    @ActivateRequestContext
    public Assignment processOrderCreatedWithInbox(String eventId,
                                                  String eventType,
                                                  String aggregateId,
                                                  OrderCreatedPayload payload) {
        // 1. Pre-check: if event was already processed, skip business operation immediately
        if (isAlreadyProcessed(eventId)) {
            log.info("Event with eventId '{}' has already been successfully processed in Inbox. Skipping business execution.", eventId);
            return assignmentRepository.findByOrderId(payload.getOrderId()).orElse(null);
        }

        try {
            // 2. Execute business operation and persist Inbox record in an atomic transaction
            return QuarkusTransaction.requiringNew().call(() -> {
                if (inboxEventRepository.isEventProcessed(eventId)) {
                    return assignmentRepository.findByOrderId(payload.getOrderId()).orElse(null);
                }

                Assignment assignment = assignmentService.initiateAssignment(payload);

                Instant now = Instant.now();
                InboxEvent inboxEvent = new InboxEvent(
                        UUID.randomUUID().toString(),
                        eventId,
                        eventType,
                        aggregateId,
                        InboxStatus.PROCESSED,
                        now,
                        now
                );
                inboxEventRepository.persist(inboxEvent);

                log.info("Successfully processed event '{}' ({}) in Inbox and initiated assignment '{}'",
                        eventId, eventType, assignment.getId());

                return assignment;
            });
        } catch (Exception e) {
            // Check for concurrent duplicate race condition (DB unique constraint on event_id or order_id)
            if (isUniqueConstraintViolation(e)) {
                log.warn("Concurrent duplicate detected for eventId '{}' or orderId '{}'. Another transaction succeeded. Gracefully returning existing assignment.",
                        eventId, payload.getOrderId());
                return assignmentRepository.findByOrderId(payload.getOrderId()).orElse(null);
            }
            throw e;
        }
    }

    /**
     * Finds an inbox event record by eventId.
     *
     * @param eventId unique event identifier
     * @return optional inbox event
     */
    public Optional<InboxEvent> findByEventId(String eventId) {
        return inboxEventRepository.findByEventId(eventId);
    }

    public boolean isUniqueConstraintViolation(Throwable throwable) {
        Throwable cause = throwable;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException) {
                return true;
            }
            String message = cause.getMessage();
            if (message != null) {
                String lower = message.toLowerCase();
                if (lower.contains("unique") || lower.contains("duplicate") || lower.contains("23505")) {
                    return true;
                }
            }
            cause = cause.getCause();
        }
        return false;
    }
}
