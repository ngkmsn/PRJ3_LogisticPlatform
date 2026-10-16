package com.logistics.shipment.service;

import com.logistics.shipment.dto.OrderCreatedPayload;
import com.logistics.shipment.entity.Assignment;

import java.util.Optional;

public interface AssignmentService {

    /**
     * Initiates assignment workflow upon receiving an OrderCreated event.
     * Enforces idempotency: if an assignment for the given orderId already exists,
     * returns the existing assignment without duplicating.
     *
     * @param payload valid order created details
     * @return newly created or existing Assignment
     */
    Assignment initiateAssignment(OrderCreatedPayload payload);

    Optional<Assignment> findByOrderId(String orderId);

    Optional<Assignment> findById(String id);
}
