package com.logistics.gateway.resource;

import com.logistics.common.dto.ApiResponse;
import com.logistics.common.ratelimit.RateLimitMetricsDto;
import com.logistics.gateway.ratelimit.GatewayRateLimitConfig;
import com.logistics.gateway.ratelimit.GatewayRateLimitHandler;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST Endpoint for querying Gateway rate limit metrics and performing operational resets.
 */
@Path("/api/gateway/rate-limit")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class GatewayRateLimitResource {

    @Inject
    GatewayRateLimitHandler rateLimitHandler;

    @Inject
    GatewayRateLimitConfig config;

    @GET
    @Path("/metrics")
    public Response getMetrics() {
        if (rateLimitHandler.getRateLimiter() == null) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(ApiResponse.error("Rate limiter is not initialized"))
                    .build();
        }
        RateLimitMetricsDto metrics = rateLimitHandler.getRateLimiter().getMetrics();
        return Response.ok(ApiResponse.ok("Gateway rate limit metrics retrieved successfully", metrics)).build();
    }

    @POST
    @Path("/reset")
    public Response reset() {
        if (rateLimitHandler.getRateLimiter() != null) {
            rateLimitHandler.getRateLimiter().resetAll();
        }
        return Response.ok(ApiResponse.ok("All rate limit counters reset successfully", null)).build();
    }

    @GET
    @Path("/policies")
    public Response getPolicies() {
        Map<String, Object> policies = new LinkedHashMap<>();
        policies.put("enabled", config.isEnabled());
        policies.put("storage", config.getStorageType());
        policies.put("failPolicy", config.getFailPolicy().name());
        policies.put("auth", Map.of(
                "limit", config.getAuthPolicy().getLimit(),
                "windowSeconds", config.getAuthPolicy().getWindowSeconds(),
                "keyType", config.getAuthPolicy().getKeyType().name()
        ));
        policies.put("orders", Map.of(
                "limit", config.getOrdersPolicy().getLimit(),
                "windowSeconds", config.getOrdersPolicy().getWindowSeconds(),
                "keyType", config.getOrdersPolicy().getKeyType().name()
        ));
        policies.put("tracking", Map.of(
                "limit", config.getTrackingPolicy().getLimit(),
                "windowSeconds", config.getTrackingPolicy().getWindowSeconds(),
                "keyType", config.getTrackingPolicy().getKeyType().name()
        ));
        policies.put("default", Map.of(
                "limit", config.getDefaultPolicy().getLimit(),
                "windowSeconds", config.getDefaultPolicy().getWindowSeconds(),
                "keyType", config.getDefaultPolicy().getKeyType().name()
        ));

        return Response.ok(ApiResponse.ok("Active rate limit policies", policies)).build();
    }
}

