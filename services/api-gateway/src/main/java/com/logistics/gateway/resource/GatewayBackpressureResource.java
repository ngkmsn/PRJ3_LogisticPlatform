package com.logistics.gateway.resource;

import com.logistics.common.backpressure.BackpressureMetricsDto;
import com.logistics.common.dto.ApiResponse;
import com.logistics.gateway.backpressure.GatewayBackpressureHandler;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * REST Endpoint for querying Gateway backpressure metrics and performing operational resets.
 */
@Path("/api/gateway/backpressure")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class GatewayBackpressureResource {

    @Inject
    GatewayBackpressureHandler backpressureHandler;

    @GET
    @Path("/metrics")
    public Response getMetrics() {
        if (backpressureHandler.getController() == null) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(ApiResponse.error("Backpressure controller is not initialized"))
                    .build();
        }
        BackpressureMetricsDto metrics = backpressureHandler.getController().getMetrics();
        return Response.ok(ApiResponse.ok("Gateway backpressure metrics retrieved successfully", metrics)).build();
    }

    @POST
    @Path("/reset")
    public Response reset() {
        if (backpressureHandler.getController() != null) {
            backpressureHandler.getController().reset();
        }
        return Response.ok(ApiResponse.ok("Gateway backpressure counters reset successfully", null)).build();
    }
}

