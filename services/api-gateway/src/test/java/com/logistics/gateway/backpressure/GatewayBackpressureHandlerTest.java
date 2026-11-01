package com.logistics.gateway.backpressure;

import com.logistics.common.backpressure.BackpressureState;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class GatewayBackpressureHandlerTest {

    private GatewayBackpressureConfig config;
    private GatewayBackpressureHandler handler;

    @BeforeEach
    void setUp() {
        config = new GatewayBackpressureConfig();
        config.init();

        handler = new GatewayBackpressureHandler();
        handler.setConfig(config);
        handler.init();
    }

    private RoutingContext mockContext(String path) {
        RoutingContext ctx = mock(RoutingContext.class);
        HttpServerRequest req = mock(HttpServerRequest.class);
        HttpServerResponse resp = mock(HttpServerResponse.class);

        when(ctx.request()).thenReturn(req);
        when(ctx.response()).thenReturn(resp);
        when(req.path()).thenReturn(path);
        when(resp.setStatusCode(anyInt())).thenReturn(resp);
        when(resp.putHeader(anyString(), anyString())).thenReturn(resp);

        return ctx;
    }

    @Test
    @DisplayName("Request under normal concurrency calls ctx.next() and attaches end handler")
    void normalLoad_passesRequest() {
        RoutingContext ctx = mockContext("/api/orders");

        handler.handle(ctx);

        verify(ctx).next();
        verify(ctx).put(eq("bp_start_time"), anyLong());
        verify(ctx).addEndHandler(any());
        verify(ctx.response(), never()).setStatusCode(503);
    }

    @Test
    @DisplayName("When concurrency reaches maxConcurrency, requests are shed with HTTP 503 and Retry-After")
    void saturatedLoad_triggers503Shedding() {
        // Artificially saturate controller permits (maxConcurrency = 50)
        for (int i = 0; i < 50; i++) {
            assertThat(handler.getController().tryAcquire()).isTrue();
        }

        RoutingContext ctx = mockContext("/api/orders");
        handler.handle(ctx);

        verify(ctx, never()).next();
        verify(ctx.response()).setStatusCode(503);
        verify(ctx.response()).putHeader("Retry-After", "5");
        verify(ctx.response()).putHeader("Content-Type", "application/json");

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(ctx.response()).end(bodyCaptor.capture());
        assertThat(bodyCaptor.getValue()).contains("Gateway backpressure activated").contains("SHEDDING");
    }

    @Test
    @DisplayName("Excluded paths bypass backpressure evaluation")
    void excludedPaths_bypassBackpressure() {
        RoutingContext healthCtx = mockContext("/q/health");
        handler.handle(healthCtx);

        verify(healthCtx).next();
        verify(healthCtx, never()).put(eq("bp_start_time"), anyLong());

        RoutingContext metricsCtx = mockContext("/api/gateway/backpressure/metrics");
        handler.handle(metricsCtx);
        verify(metricsCtx).next();
    }
}

