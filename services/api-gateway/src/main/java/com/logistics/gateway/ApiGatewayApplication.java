package com.logistics.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API Gateway – single entry point for all external clients.
 *
 * <p>All client requests MUST go through this gateway. Backend services
 * ({@code user-service}, {@code order-service}, {@code shipment-service},
 * {@code notification-service}) are not intended to be accessed directly
 * by external clients.
 *
 * <p>Routing rules are defined in {@code application.yml} under
 * {@code spring.cloud.gateway.routes}. Each route is identified by a
 * request-path prefix and forwarded to the corresponding backend service URL,
 * which is supplied via environment variables (e.g. {@code USER_SERVICE_URL}).
 *
 * <p>Future cross-cutting concerns (authentication, rate-limiting, logging)
 * should be implemented here as Gateway Filters (PB-003+).
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
