package com.logistics.shipment.service;

import com.logistics.shipment.dto.CreateAssignmentRequest;
import com.logistics.shipment.dto.OrderCreatedPayload;
import com.logistics.shipment.eligibility.model.AssignmentCriteria;
import com.logistics.shipment.entity.Assignment;
import com.logistics.shipment.exception.DuplicateAssignmentException;

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

    /**
     * Creates and persists a new Assignment record explicitly linked with a chosen driver (PB-031).
     * Enforces strict single active assignment constraint per order:
     * - Returns existing assignment if already assigned to the same driver (idempotent).
     * - Updates existing PENDING/SEARCHING_DRIVER assignment to ASSIGNED.
     * - Throws {@link DuplicateAssignmentException} if already assigned to a different driver.
     *
     * @param request valid assignment creation request with order and driver details
     * @return persisted Assignment with ASSIGNED status and assignedAt timestamp
     * @throws DuplicateAssignmentException if active assignment conflict occurs
     */
    Assignment createAssignment(CreateAssignmentRequest request);

    /**
     * Assigns a driver to an existing assignment record identified by orderId.
     * Sets status to ASSIGNED and records assignedAt timestamp.
     *
     * @param orderId  order identifier
     * @param driverId driver identifier
     * @return updated Assignment
     * @throws DuplicateAssignmentException if order is already assigned to another driver
     */
    Assignment assignDriver(String orderId, String driverId);

    /**
     * Executes the end-to-end auto assignment workflow for an order (PB-030 / PB-031).
     * Integrates eligibility pipeline, ranking, best driver selection, and atomic persistence.
     *
     * @param orderId  order identifier
     * @param criteria assignment criteria for evaluating and ranking candidate drivers
     * @return updated Assignment with status ASSIGNED and driverId, or SEARCHING_DRIVER if none match
     */
    Assignment autoAssignOrder(String orderId, AssignmentCriteria criteria);

    /**
     * Overload helper to automatically execute auto-assignment directly from an {@link OrderCreatedPayload}.
     *
     * @param payload order created payload
     * @return updated Assignment
     */
    Assignment autoAssignOrder(OrderCreatedPayload payload);

    Optional<Assignment> findByOrderId(String orderId);

    Optional<Assignment> findById(String id);

    /**
     * Retrieves paginated list of unassigned orders awaiting operations triage (PB-034).
     * Requires authentication and DISPATCHER or ADMIN role.
     *
     * @param authHeader Bearer token
     * @param page       0-based page number
     * @param size       page size
     * @param status     filter status (default UNASSIGNED)
     * @param sort       sorting order (e.g. oldest_first, newest_first)
     * @return PagedResponse of UnassignedOrderDto
     */
    com.logistics.shipment.dto.PagedResponse<com.logistics.shipment.dto.UnassignedOrderDto> getUnassignedOrders(
            String authHeader, int page, int size, String status, String sort);

    /**
     * Retrieves details of a specific unassigned order by orderId (PB-034).
     *
     * @param authHeader Bearer token
     * @param orderId    order identifier
     * @return UnassignedOrderDto
     */
    com.logistics.shipment.dto.UnassignedOrderDto getUnassignedOrderDetail(String authHeader, String orderId);

    /**
     * Manually assigns a driver to an unassigned order by operations personnel (PB-035).
     * Validates that the caller has DISPATCHER or ADMIN role, the order is unassigned,
     * the driver is valid and available, marks assignment source as MANUAL, records assignedBy,
     * and broadcasts a DRIVER_ASSIGNED event.
     *
     * @param authHeader Bearer token
     * @param orderId    order identifier
     * @param request    manual assign driver request containing driverId and notes
     * @return updated Assignment
     */
    Assignment manualAssignDriver(String authHeader, String orderId, com.logistics.shipment.dto.ManualAssignDriverRequest request);

    /**
     * Reassigns an order from its current driver to a new driver by operations personnel (PB-036).
     * Validates that the caller has DISPATCHER or ADMIN role, the order currently has an active assignment,
     * order status is not final/cancelled/failed, the new driver is different and available,
     * records previous driver and reassign reason, updates workload, and broadcasts DRIVER_REASSIGNED.
     *
     * @param authHeader Bearer token
     * @param orderId    order identifier
     * @param request    reassign driver request containing newDriverId, reason, and notes
     * @return updated Assignment
     */
    Assignment reassignDriver(String authHeader, String orderId, com.logistics.shipment.dto.ReassignDriverRequest request);

    /**
     * Retrieves chronological assignment and reassignment history for an order (PB-037).
     * Requires authentication with DISPATCHER or ADMIN role.
     *
     * @param authHeader Bearer token
     * @param orderId    order identifier
     * @return list of AssignmentHistoryDto sorted by createdAt ascending
     */
    java.util.List<com.logistics.shipment.dto.AssignmentHistoryDto> getAssignmentHistory(String authHeader, String orderId);
}
