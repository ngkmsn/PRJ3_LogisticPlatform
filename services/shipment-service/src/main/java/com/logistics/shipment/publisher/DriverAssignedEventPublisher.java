package com.logistics.shipment.publisher;

import com.logistics.shipment.entity.Assignment;

/**
 * Service boundary interface for publishing DRIVER_ASSIGNED events (PB-032).
 * Broadcasts assignment updates to downstream consumers (Order Service, Notification Service).
 */
public interface DriverAssignedEventPublisher {

    /**
     * Publishes a DRIVER_ASSIGNED domain event to Kafka topic logistics.shipment.events.
     *
     * @param assignment the newly created or updated assignment with status ASSIGNED
     */
    void publishDriverAssigned(Assignment assignment);

    /**
     * Publishes a DRIVER_REASSIGNED domain event to Kafka topic logistics.shipment.events (PB-036).
     *
     * @param assignment       the assignment record with the new driver and updated metadata
     * @param previousDriverId the ID of the driver who was previously assigned
     * @param reassignReason   the operational reason for reassignment
     */
    void publishDriverReassigned(Assignment assignment, String previousDriverId, String reassignReason);

    /**
     * Publishes an ORDER_PICKED_UP domain event to Kafka topic logistics.shipment.events (PB-039).
     *
     * @param assignment the assignment record with status PICKED_UP and pickedUpAt set
     */
    void publishOrderPickedUp(Assignment assignment);

    /**
     * Publishes an ORDER_IN_TRANSIT domain event to Kafka topic logistics.shipment.events (PB-040).
     *
     * @param assignment the assignment record with status IN_TRANSIT and transitStartedAt set
     */
    void publishOrderInTransit(Assignment assignment);

    /**
     * Publishes an ORDER_DELIVERED domain event to Kafka topic logistics.shipment.events (PB-041).
     *
     * @param assignment the assignment record with status DELIVERED and deliveredAt set
     */
    void publishOrderDelivered(Assignment assignment);

    /**
     * Publishes an ORDER_FAILED domain event to Kafka topic logistics.shipment.events (PB-042).
     *
     * @param assignment the assignment record with status FAILED and failureReason set
     */
    void publishOrderFailed(Assignment assignment);
}


