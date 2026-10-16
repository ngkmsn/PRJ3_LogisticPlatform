package com.logistics.shipment.service;

import com.logistics.shipment.dto.OrderCreatedPayload;
import com.logistics.shipment.entity.Assignment;
import com.logistics.shipment.model.AssignmentStatus;
import com.logistics.shipment.repository.AssignmentRepository;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@DefaultBean
public class DefaultAssignmentService implements AssignmentService {

    private static final Logger log = LoggerFactory.getLogger(DefaultAssignmentService.class);

    @Inject
    AssignmentRepository assignmentRepository;

    public DefaultAssignmentService() {
    }

    public DefaultAssignmentService(AssignmentRepository assignmentRepository) {
        this.assignmentRepository = assignmentRepository;
    }

    @Override
    @Transactional
    public Assignment initiateAssignment(OrderCreatedPayload payload) {
        if (payload == null || payload.getOrderId() == null) {
            throw new IllegalArgumentException("Payload and orderId must not be null");
        }

        // Idempotency check: if assignment already exists for this orderId, return existing
        Optional<Assignment> existing = assignmentRepository.findByOrderId(payload.getOrderId());
        if (existing.isPresent()) {
            Assignment found = existing.get();
            log.info("Assignment already exists for orderId '{}' (assignmentId: '{}', status: '{}'). Idempotent return.",
                    payload.getOrderId(), found.getId(), found.getStatus());
            return found;
        }

        Instant now = Instant.now();
        Assignment assignment = new Assignment(
                UUID.randomUUID().toString(),
                payload.getOrderId(),
                payload.getOrderNumber(),
                payload.getCustomerId(),
                payload.getRecipientName(),
                payload.getRecipientPhone(),
                payload.getDeliveryAddress(),
                payload.getTotalAmount(),
                AssignmentStatus.PENDING,
                null,
                payload.getNotes(),
                now,
                now
        );

        assignmentRepository.persist(assignment);
        log.info("Successfully initiated assignment '{}' for orderId '{}' (orderNumber: '{}', status: '{}')",
                assignment.getId(), assignment.getOrderId(), assignment.getOrderNumber(), assignment.getStatus());

        return assignment;
    }

    @Override
    public Optional<Assignment> findByOrderId(String orderId) {
        return assignmentRepository.findByOrderId(orderId);
    }

    @Override
    public Optional<Assignment> findById(String id) {
        return assignmentRepository.findByIdOptional(id);
    }
}
