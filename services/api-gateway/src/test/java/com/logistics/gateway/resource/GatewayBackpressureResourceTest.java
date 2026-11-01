package com.logistics.gateway.resource;

import com.logistics.common.backpressure.BackpressureMetricsDto;
import com.logistics.common.backpressure.BackpressureState;
import com.logistics.common.dto.ApiResponse;
import com.logistics.gateway.backpressure.GatewayBackpressureConfig;
import com.logistics.gateway.backpressure.GatewayBackpressureHandler;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayBackpressureResourceTest {

    private GatewayBackpressureConfig config;
    private GatewayBackpressureHandler handler;
    private GatewayBackpressureResource resource;

    @BeforeEach
    void setUp() {
        config = new GatewayBackpressureConfig();
        config.init();

        handler = new GatewayBackpressureHandler();
        handler.setConfig(config);
        handler.init();

        resource = new GatewayBackpressureResource();
        resource.backpressureHandler = handler;
    }

    @Test
    @DisplayName("getMetrics returns accurate backpressure telemetry snapshot")
    void getMetrics_returnsMetrics() {
        handler.getController().tryAcquire();

        Response response = resource.getMetrics();
        assertThat(response.getStatus()).isEqualTo(200);

        ApiResponse<?> body = (ApiResponse<?>) response.getEntity();
        assertThat(body.isSuccess()).isTrue();

        BackpressureMetricsDto metrics = (BackpressureMetricsDto) body.getData();
        assertThat(metrics.getName()).isEqualTo("gateway-inbound");
        assertThat(metrics.getCurrentInFlight()).isEqualTo(1);
        assertThat(metrics.getMaxConcurrency()).isEqualTo(50);
        assertThat(metrics.getState()).isEqualTo(BackpressureState.NORMAL);
    }

    @Test
    @DisplayName("reset clears all backpressure counters")
    void reset_clearsCounters() {
        handler.getController().tryAcquire();
        assertThat(handler.getController().getInFlightCount()).isEqualTo(1);

        Response response = resource.reset();
        assertThat(response.getStatus()).isEqualTo(200);

        assertThat(handler.getController().getInFlightCount()).isEqualTo(0);
    }
}

