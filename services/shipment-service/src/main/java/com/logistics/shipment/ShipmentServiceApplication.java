package com.logistics.shipment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Shipment Service.
 *
 * <p>Responsibilities (to be implemented in subsequent PB items):
 * <ul>
 *   <li>Create and manage shipments (grouping of orders into a physical delivery run)</li>
 *   <li>Track vehicle assignment and driver assignment per shipment</li>
 *   <li>Record real-time location updates and estimated arrival times</li>
 *   <li>Manage proof-of-delivery confirmations</li>
 * </ul>
 */
@SpringBootApplication
public class ShipmentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShipmentServiceApplication.class, args);
    }
}
