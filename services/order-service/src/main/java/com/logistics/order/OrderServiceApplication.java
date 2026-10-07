package com.logistics.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Order Service.
 *
 * <p>Responsibilities (to be implemented in subsequent PB items):
 * <ul>
 *   <li>Create, update, and cancel logistics orders</li>
 *   <li>Track order status lifecycle (PENDING → ASSIGNED → IN_TRANSIT → DELIVERED)</li>
 *   <li>Coordinate with Shipment Service when an order is assigned to a shipment</li>
 * </ul>
 */
@SpringBootApplication
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
