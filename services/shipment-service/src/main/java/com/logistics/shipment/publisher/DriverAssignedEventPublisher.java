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
}
