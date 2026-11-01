package com.logistics.notification.controller;

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

class NotificationCircuitBreakerResourceTest {

    private NotificationCircuitBreakerResource resource;
    private ApiNotificationCircuitBreakerResource apiResource;

    @BeforeEach
    void setUp() {
        resource = new NotificationCircuitBreakerResource();
        apiResource = new ApiNotificationCircuitBreakerResource();
        apiResource.resource = resource;

        CircuitBreakerRegistry.getInstance().resetAll();
    }

    @Test
    @DisplayName("getAllMetrics: returns 200 OK with registered circuit breaker metrics")
    void getAllMetrics_returnsList() {
        CircuitBreaker cb = CircuitBreakerRegistry.getInstance().getOrCreate("notification-platform");
        cb.recordFailure(new IOException("Timeout"));

        Response response = resource.getAllMetrics();
        assertThat(response.getStatus()).isEqualTo(200);

        ApiResponse<?> body = (ApiResponse<?>) response.getEntity();
        assertThat(body.isSuccess()).isTrue();
        assertThat(body.getData()).isInstanceOf(List.class);

        // Also test Api resource delegation
        Response apiResp = apiResource.getAllMetrics();
        assertThat(apiResp.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("getMetricsByName: returns 200 OK when circuit breaker exists, 404 when missing")
    void getMetricsByName() {
        CircuitBreakerRegistry.getInstance().getOrCreate("notification-platform");

        Response response = resource.getMetricsByName("notification-platform");
        assertThat(response.getStatus()).isEqualTo(200);

        Response notFound = resource.getMetricsByName("unknown-cb");
        assertThat(notFound.getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("reset: resets circuit breaker to CLOSED and returns 200 OK")
    void reset_resetsToClosed() {
        CircuitBreaker cb = CircuitBreakerRegistry.getInstance().getOrCreate("notification-platform");
        cb.recordFailure(new IOException("Err 1"));
        cb.recordFailure(new IOException("Err 2"));
        cb.recordFailure(new IOException("Err 3"));
        assertThat(cb.isOpen()).isTrue();

        Response response = resource.reset("notification-platform");
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(cb.isClosed()).isTrue();

        Response notFound = resource.reset("nonexistent");
        assertThat(notFound.getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("resetAll: resets all circuit breakers and returns 200 OK")
    void resetAll() {
        CircuitBreaker cb1 = CircuitBreakerRegistry.getInstance().getOrCreate("cb-1");
        CircuitBreaker cb2 = CircuitBreakerRegistry.getInstance().getOrCreate("cb-2");
        cb1.recordFailure(new IOException("Err"));
        cb2.recordFailure(new IOException("Err"));

        Response response = resource.resetAll();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(cb1.isClosed()).isTrue();
        assertThat(cb2.isClosed()).isTrue();
    }
}
