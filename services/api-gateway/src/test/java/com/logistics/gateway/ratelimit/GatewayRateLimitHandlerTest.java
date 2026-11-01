package com.logistics.gateway.ratelimit;

import com.logistics.common.ratelimit.RateLimitMetricsDto;
import com.logistics.common.ratelimit.RateLimitPolicy;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class GatewayRateLimitHandlerTest {

    private GatewayRateLimitConfig config;
    private GatewayRateLimitHandler handler;

    @BeforeEach
    void setUp() {
        config = new GatewayRateLimitConfig();
        config.init();

        handler = new GatewayRateLimitHandler();
        handler.config = config;
        handler.init();
    }

    private RoutingContext mockContext(String path, String clientIp, String userId, String bearerToken) {
        RoutingContext ctx = mock(RoutingContext.class);
        HttpServerRequest req = mock(HttpServerRequest.class);
        HttpServerResponse resp = mock(HttpServerResponse.class);

        when(ctx.request()).thenReturn(req);
        when(ctx.response()).thenReturn(resp);
        when(req.path()).thenReturn(path);

        SocketAddress socketAddress = mock(SocketAddress.class);
        when(socketAddress.host()).thenReturn(clientIp != null ? clientIp : "127.0.0.1");
        when(req.remoteAddress()).thenReturn(socketAddress);

        when(req.getHeader("X-Forwarded-For")).thenReturn(clientIp);
        when(req.getHeader("X-User-Id")).thenReturn(userId);
        if (bearerToken != null) {
            when(req.getHeader("Authorization")).thenReturn("Bearer " + bearerToken);
        }

        when(resp.putHeader(anyString(), anyString())).thenReturn(resp);
        when(resp.setStatusCode(anyInt())).thenReturn(resp);

        return ctx;
    }

    @Test
    @DisplayName("Request within limit attaches rate limit headers and calls ctx.next()")
    void requestWithinLimit_callsNextAndSetsHeaders() {
        RoutingContext ctx = mockContext("/api/orders", "192.168.1.50", "user-1", null);

        handler.handle(ctx);

        verify(ctx).next();
        verify(ctx.response()).putHeader("X-RateLimit-Limit", "30");
        verify(ctx.response()).putHeader(eq("X-RateLimit-Remaining"), anyString());
        verify(ctx.response()).putHeader(eq("X-RateLimit-Reset"), anyString());
        verify(ctx.response(), never()).setStatusCode(429);
    }

    @Test
    @DisplayName("Request exceeding limit returns HTTP 429 and Retry-After header without calling ctx.next()")
    void requestExceedingLimit_returns429AndRetryAfter() {
        // Auth policy has limit of 10
        String ip = "10.0.0.99";
        for (int i = 0; i < 10; i++) {
            RoutingContext ctx = mockContext("/api/auth/login", ip, null, null);
            handler.handle(ctx);
            verify(ctx).next();
        }

        // 11th request must be rejected
        RoutingContext rejectedCtx = mockContext("/api/auth/login", ip, null, null);
        handler.handle(rejectedCtx);

        verify(rejectedCtx, never()).next();
        verify(rejectedCtx.response()).setStatusCode(429);
        verify(rejectedCtx.response()).putHeader(eq("Retry-After"), anyString());
        verify(rejectedCtx.response()).putHeader(eq("Content-Type"), eq("application/json"));

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(rejectedCtx.response()).end(bodyCaptor.capture());
        assertThat(bodyCaptor.getValue()).contains("Rate limit exceeded").contains("auth");
    }

    @Test
    @DisplayName("Different endpoints apply their respective tiered policies")
    void differentEndpoints_applyRespectivePolicies() {
        // Orders policy limit is 30, auth is 10, tracking is 120
        assertThat(config.getAuthPolicy().getLimit()).isEqualTo(10);
        assertThat(config.getOrdersPolicy().getLimit()).isEqualTo(30);
        assertThat(config.getTrackingPolicy().getLimit()).isEqualTo(120);
        assertThat(config.getDefaultPolicy().getLimit()).isEqualTo(60);

        RoutingContext authCtx = mockContext("/auth/login", "1.1.1.1", null, null);
        handler.handle(authCtx);
        verify(authCtx.response()).putHeader("X-RateLimit-Limit", "10");

        RoutingContext trackingCtx = mockContext("/api/shipments/tracking/ord-1", "1.1.1.1", null, null);
        handler.handle(trackingCtx);
        verify(trackingCtx.response()).putHeader("X-RateLimit-Limit", "120");
    }

    @Test
    @DisplayName("Excluded system paths bypass rate limiting completely")
    void excludedPaths_bypassRateLimiting() {
        RoutingContext healthCtx = mockContext("/q/health", "1.1.1.1", null, null);
        handler.handle(healthCtx);

        verify(healthCtx).next();
        verify(healthCtx.response(), never()).putHeader(eq("X-RateLimit-Limit"), anyString());

        RoutingContext metricsCtx = mockContext("/api/gateway/rate-limit/metrics", "1.1.1.1", null, null);
        handler.handle(metricsCtx);
        verify(metricsCtx).next();
    }

    @Test
    @DisplayName("Authenticated users are partitioned by User ID regardless of IP")
    void authenticatedUser_partitionedByUserId() {
        RoutingContext ctxUser1 = mockContext("/api/orders", "1.1.1.1", "user-alpha", null);
        handler.handle(ctxUser1);

        // Same user from different IP shares the same quota!
        RoutingContext ctxUser1DifferentIp = mockContext("/api/orders", "2.2.2.2", "user-alpha", null);
        handler.handle(ctxUser1DifferentIp);

        RateLimitMetricsDto metrics = handler.getRateLimiter().getMetrics();
        assertThat(metrics.getAllowedRequests()).isEqualTo(2);
    }
}

