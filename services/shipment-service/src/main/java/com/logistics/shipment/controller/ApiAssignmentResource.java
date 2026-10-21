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

@Path("/api/assignments")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class ApiAssignmentResource {

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

    @GET
    @Path("/failed")
    public Response getFailedOrders(
            @HeaderParam("Authorization") String authHeader,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("10") int size,
            @QueryParam("sort") @DefaultValue("newest_first") String sort) {
        return assignmentController.getFailedOrders(authHeader, page, size, sort);
    }

    @GET
    @Path("/failed/{orderId}")
    public Response getFailedOrderDetail(
            @HeaderParam("Authorization") String authHeader,
            @PathParam("orderId") String orderId) {
        return assignmentController.getFailedOrderDetail(authHeader, orderId);
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

    @GET
    @Path("/driver/orders")
    public Response getDriverOrders(
            @HeaderParam("Authorization") String authHeader,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("10") int size,
            @QueryParam("status") String status) {
        return assignmentController.getDriverOrders(authHeader, page, size, status);
    }

    @GET
    @Path("/my-orders")
    public Response getMyOrders(
            @HeaderParam("Authorization") String authHeader,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("10") int size,
            @QueryParam("status") String status) {
        return assignmentController.getMyOrders(authHeader, page, size, status);
    }

    @jakarta.ws.rs.POST
    @Path("/{orderId}/pickup")
    public Response confirmPickup(
            @HeaderParam("Authorization") String authHeader,
            @PathParam("orderId") String orderId,
            @jakarta.validation.Valid com.logistics.shipment.dto.ConfirmPickupRequest request) {
        return assignmentController.confirmPickup(authHeader, orderId, request);
    }

    @jakarta.ws.rs.POST
    @Path("/{orderId}/transit")
    public Response startTransit(
            @HeaderParam("Authorization") String authHeader,
            @PathParam("orderId") String orderId,
            @jakarta.validation.Valid com.logistics.shipment.dto.StartTransitRequest request) {
        return assignmentController.startTransit(authHeader, orderId, request);
    }

    @jakarta.ws.rs.POST
    @Path("/{orderId}/start-transit")
    public Response startTransitAlias(
            @HeaderParam("Authorization") String authHeader,
            @PathParam("orderId") String orderId,
            @jakarta.validation.Valid com.logistics.shipment.dto.StartTransitRequest request) {
        return assignmentController.startTransit(authHeader, orderId, request);
    }

    @jakarta.ws.rs.POST
    @Path("/{orderId}/deliver")
    public Response confirmDelivery(
            @HeaderParam("Authorization") String authHeader,
            @PathParam("orderId") String orderId,
            @jakarta.validation.Valid com.logistics.shipment.dto.ConfirmDeliveryRequest request) {
        return assignmentController.confirmDelivery(authHeader, orderId, request);
    }

    @jakarta.ws.rs.POST
    @Path("/{orderId}/complete-delivery")
    public Response completeDeliveryAlias(
            @HeaderParam("Authorization") String authHeader,
            @PathParam("orderId") String orderId,
            @jakarta.validation.Valid com.logistics.shipment.dto.ConfirmDeliveryRequest request) {
        return assignmentController.confirmDelivery(authHeader, orderId, request);
    }

    @jakarta.ws.rs.POST
    @Path("/{orderId}/delivered")
    public Response deliveredAlias(
            @HeaderParam("Authorization") String authHeader,
            @PathParam("orderId") String orderId,
            @jakarta.validation.Valid com.logistics.shipment.dto.ConfirmDeliveryRequest request) {
        return assignmentController.confirmDelivery(authHeader, orderId, request);
    }

    @jakarta.ws.rs.POST
    @Path("/{orderId}/fail")
    public Response reportDeliveryFailure(
            @HeaderParam("Authorization") String authHeader,
            @PathParam("orderId") String orderId,
            @jakarta.validation.Valid com.logistics.shipment.dto.ReportDeliveryFailureRequest request) {
        return assignmentController.reportDeliveryFailure(authHeader, orderId, request);
    }

    @jakarta.ws.rs.POST
    @Path("/{orderId}/failed")
    public Response reportDeliveryFailedAlias(
            @HeaderParam("Authorization") String authHeader,
            @PathParam("orderId") String orderId,
            @jakarta.validation.Valid com.logistics.shipment.dto.ReportDeliveryFailureRequest request) {
        return assignmentController.reportDeliveryFailure(authHeader, orderId, request);
    }

    @jakarta.ws.rs.POST
    @Path("/{orderId}/fail-delivery")
    public Response reportDeliveryFailDeliveryAlias(
            @HeaderParam("Authorization") String authHeader,
            @PathParam("orderId") String orderId,
            @jakarta.validation.Valid com.logistics.shipment.dto.ReportDeliveryFailureRequest request) {
        return assignmentController.reportDeliveryFailure(authHeader, orderId, request);
    }
}


