package com.logistics.shipment.controller;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/shipments/assignments")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class ApiShipmentAssignmentResource {

    @Inject
    AssignmentController assignmentController;

    @GET
    @Path("/unassigned")
    public Response getUnassignedOrders(
            @HeaderParam("Authorization") String authHeader,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("10") int size,
            @QueryParam("status") @DefaultValue("UNASSIGNED") String status,
            @QueryParam("sort") @DefaultValue("oldest_first") String sort) {
        return assignmentController.getUnassignedOrders(authHeader, page, size, status, sort);
    }

    @GET
    @Path("/unassigned/{orderId}")
    public Response getUnassignedOrderDetail(
            @HeaderParam("Authorization") String authHeader,
            @PathParam("orderId") String orderId) {
        return assignmentController.getUnassignedOrderDetail(authHeader, orderId);
    }

    @jakarta.ws.rs.POST
    @Path("/{orderId}/manual-assign")
    public Response manualAssignDriver(
            @HeaderParam("Authorization") String authHeader,
            @PathParam("orderId") String orderId,
            @jakarta.validation.Valid com.logistics.shipment.dto.ManualAssignDriverRequest request) {
        return assignmentController.manualAssignDriver(authHeader, orderId, request);
    }

    @jakarta.ws.rs.POST
    @Path("/{orderId}/reassign")
    public Response reassignDriver(
            @HeaderParam("Authorization") String authHeader,
            @PathParam("orderId") String orderId,
            @jakarta.validation.Valid com.logistics.shipment.dto.ReassignDriverRequest request) {
        return assignmentController.reassignDriver(authHeader, orderId, request);
    }

    @GET
    @Path("/{orderId}/history")
    public Response getAssignmentHistory(
            @HeaderParam("Authorization") String authHeader,
            @PathParam("orderId") String orderId) {
        return assignmentController.getAssignmentHistory(authHeader, orderId);
    }
}
