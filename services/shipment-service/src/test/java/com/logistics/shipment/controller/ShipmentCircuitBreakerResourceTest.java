package com.logistics.shipment.controller;

import com.logistics.common.circuitbreaker.CircuitBreaker;
import com.logistics.common.circuitbreaker.CircuitBreakerRegistry;
import com.logistics.common.dto.ApiResponse;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ShipmentCircuitBreakerResourceTest {

    private ShipmentCircuitBreakerResource resource;
    private ApiShipmentCircuitBreakerResource apiResource;

    @BeforeEach
    void setUp() {
        resource = new ShipmentCircuitBreakerResource();
        apiResource = new ApiShipmentCircuitBreakerResource();
        apiResource.resource = resource;

        CircuitBreakerRegistry.getInstance().resetAll();
    }

    @Test
    @DisplayName("getAllMetrics: returns 200 OK with registered circuit breaker metrics")
    void getAllMetrics_returnsList() {
        CircuitBreaker cb = CircuitBreakerRegistry.getInstance().getOrCreate("map-provider");
        cb.recordFailure(new IOException("Map timeout"));

        Response response = resource.getAllMetrics();
        assertThat(response.getStatus()).isEqualTo(200);

        ApiResponse<?> body = (ApiResponse<?>) response.getEntity();
        assertThat(body.isSuccess()).isTrue();
        assertThat(body.getData()).isInstanceOf(List.class);

        Response apiResp = apiResource.getAllMetrics();
        assertThat(apiResp.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("getMetricsByName: returns 200 OK when exists, 404 when missing")
    void getMetricsByName() {
        CircuitBreakerRegistry.getInstance().getOrCreate("map-provider");

        Response response = resource.getMetricsByName("map-provider");
        assertThat(response.getStatus()).isEqualTo(200);

        Response notFound = resource.getMetricsByName("unknown-map");
        assertThat(notFound.getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("reset: resets circuit breaker to CLOSED and returns 200 OK")
    void reset_resetsToClosed() {
        CircuitBreaker cb = CircuitBreakerRegistry.getInstance().getOrCreate("map-provider");
        cb.recordFailure(new IOException("Err 1"));
        cb.recordFailure(new IOException("Err 2"));
        cb.recordFailure(new IOException("Err 3"));
        assertThat(cb.isOpen()).isTrue();

        Response response = resource.reset("map-provider");
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(cb.isClosed()).isTrue();

        Response notFound = resource.reset("nonexistent");
        assertThat(notFound.getStatus()).isEqualTo(404);
    }
}
