package com.logistics.gateway.resource;

import com.logistics.common.dto.ApiResponse;
import com.logistics.common.ratelimit.RateLimitMetricsDto;
import com.logistics.common.ratelimit.RateLimitPolicy;
import com.logistics.gateway.ratelimit.GatewayRateLimitConfig;
import com.logistics.gateway.ratelimit.GatewayRateLimitHandler;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayRateLimitResourceTest {

    private GatewayRateLimitConfig config;
    private GatewayRateLimitHandler handler;
    private GatewayRateLimitResource resource;

    @BeforeEach
    void setUp() {
        config = new GatewayRateLimitConfig();
        config.init();

        handler = new GatewayRateLimitHandler();
        handler.setConfig(config);
        handler.init();

        resource = new GatewayRateLimitResource();
        resource.config = config;
        resource.rateLimitHandler = handler;
    }

    @Test
    @DisplayName("getPolicies returns configured rate limit policies and thresholds")
    void getPolicies_returnsConfiguredPolicies() {
        Response response = resource.getPolicies();
        assertThat(response.getStatus()).isEqualTo(200);

        ApiResponse<?> body = (ApiResponse<?>) response.getEntity();
        assertThat(body.isSuccess()).isTrue();

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body.getData();
        assertThat(data).containsKeys("enabled", "storage", "failPolicy", "auth", "orders", "tracking", "default");

        @SuppressWarnings("unchecked")
        Map<String, Object> auth = (Map<String, Object>) data.get("auth");
        assertThat(auth.get("limit")).isEqualTo(10L);
    }

    @Test
    @DisplayName("getMetrics returns telemetry snapshot")
    void getMetrics_returnsTelemetrySnapshot() {
        // Exercise rate limiter
        handler.getRateLimiter().checkAndConsume("test-client", RateLimitPolicy.defaultPolicy(5, 60));

        Response response = resource.getMetrics();
        assertThat(response.getStatus()).isEqualTo(200);

        ApiResponse<?> body = (ApiResponse<?>) response.getEntity();
        assertThat(body.isSuccess()).isTrue();

        RateLimitMetricsDto metrics = (RateLimitMetricsDto) body.getData();
        assertThat(metrics.getTotalRequests()).isGreaterThanOrEqualTo(1);
        assertThat(metrics.getAllowedRequests()).isGreaterThanOrEqualTo(1);
        assertThat(metrics.getStorageType()).isEqualTo("IN_MEMORY");
        assertThat(metrics.isStorageHealthy()).isTrue();
    }

    @Test
    @DisplayName("reset clears all rate limit counters")
    void reset_clearsCounters() {
        handler.getRateLimiter().checkAndConsume("client-1", RateLimitPolicy.defaultPolicy(1, 60));
        assertThat(handler.getRateLimiter().getMetrics().getTotalRequests()).isEqualTo(1);

        Response response = resource.reset();
        assertThat(response.getStatus()).isEqualTo(200);

        assertThat(handler.getRateLimiter().getMetrics().getTotalRequests()).isEqualTo(0);
    }
}
