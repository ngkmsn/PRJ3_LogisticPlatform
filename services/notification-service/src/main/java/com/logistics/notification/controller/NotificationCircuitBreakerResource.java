package com.logistics.notification.controller;

import com.logistics.common.circuitbreaker.CircuitBreaker;
import com.logistics.common.circuitbreaker.CircuitBreakerMetricsDto;
import com.logistics.common.circuitbreaker.CircuitBreakerRegistry;
import com.logistics.common.dto.ApiResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Path("/notifications/circuit-breaker")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class NotificationCircuitBreakerResource {

    @GET
    @Path("/metrics")
    public Response getAllMetrics() {
        List<CircuitBreakerMetricsDto> metrics = CircuitBreakerRegistry.getInstance().getAllMetrics();
        return Response.ok(ApiResponse.ok("Circuit breaker metrics retrieved successfully", metrics)).build();
    }

    @GET
    @Path("/{name}")
    public Response getMetricsByName(@PathParam("name") String name) {
        Optional<CircuitBreaker> cb = CircuitBreakerRegistry.getInstance().get(name);
        if (cb.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiResponse.error("Circuit breaker not found: " + name))
                    .build();
        }
        return Response.ok(ApiResponse.ok("Circuit breaker retrieved successfully", cb.get().getMetrics())).build();
    }

    @POST
    @Path("/{name}/reset")
    public Response reset(@PathParam("name") String name) {
        boolean success = CircuitBreakerRegistry.getInstance().reset(name);
        if (!success) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiResponse.error("Circuit breaker not found: " + name))
                    .build();
        }
        return Response.ok(ApiResponse.ok("Circuit breaker reset to CLOSED", Map.of("name", name, "state", "CLOSED"))).build();
    }

    @POST
    @Path("/reset-all")
    public Response resetAll() {
        CircuitBreakerRegistry.getInstance().resetAll();
        return Response.ok(ApiResponse.ok("All circuit breakers reset to CLOSED", Map.of("status", "SUCCESS"))).build();
    }
}
