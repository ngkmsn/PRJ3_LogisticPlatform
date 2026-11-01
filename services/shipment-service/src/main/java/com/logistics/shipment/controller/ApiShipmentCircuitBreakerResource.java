package com.logistics.shipment.controller;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/shipments/circuit-breaker")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class ApiShipmentCircuitBreakerResource {

    @Inject
    ShipmentCircuitBreakerResource resource;

    @GET
    @Path("/metrics")
    public Response getAllMetrics() {
        return resource.getAllMetrics();
    }

    @GET
    @Path("/{name}")
    public Response getMetricsByName(@PathParam("name") String name) {
        return resource.getMetricsByName(name);
    }

    @POST
    @Path("/{name}/reset")
    public Response reset(@PathParam("name") String name) {
        return resource.reset(name);
    }
}
