package com.logistics.shipment.controller;

import com.logistics.shipment.dto.UpdateDriverLocationRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/shipments/tracking")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class ShipmentTrackingResource {

    @Inject
    TrackingController trackingController;

    @POST
    @Path("/location")
    public Response updateLocation(
            @HeaderParam("Authorization") String authHeader,
            @Valid UpdateDriverLocationRequest request) {
        return trackingController.updateLocation(authHeader, request);
    }

    @POST
    @Path("/drivers/location")
    public Response updateDriverLocationAlias(
            @HeaderParam("Authorization") String authHeader,
            @Valid UpdateDriverLocationRequest request) {
        return trackingController.updateLocation(authHeader, request);
    }

    @GET
    @Path("/location")
    public Response getCurrentLocation(@HeaderParam("Authorization") String authHeader) {
        return trackingController.getCurrentLocation(authHeader);
    }

    @GET
    @Path("/drivers/{driverId}/location")
    public Response getDriverLocation(
            @HeaderParam("Authorization") String authHeader,
            @PathParam("driverId") String driverId) {
        return trackingController.getDriverLocation(authHeader, driverId);
    }

    @GET
    @Path("/orders/{orderId}")
    public Response getOrderTracking(
            @HeaderParam("Authorization") String authHeader,
            @PathParam("orderId") String orderId,
            @jakarta.ws.rs.QueryParam("allowStale") @jakarta.ws.rs.DefaultValue("false") boolean allowStale) {
        return trackingController.getOrderTracking(authHeader, orderId, allowStale);
    }

    @GET
    @Path("/orders/{orderId}/location")
    public Response getOrderTrackingLocationAlias(
            @HeaderParam("Authorization") String authHeader,
            @PathParam("orderId") String orderId,
            @jakarta.ws.rs.QueryParam("allowStale") @jakarta.ws.rs.DefaultValue("false") boolean allowStale) {
        return trackingController.getOrderTracking(authHeader, orderId, allowStale);
    }

    @GET
    @Path("/orders/{orderId}/directions")
    public Response getOrderDirections(
            @HeaderParam("Authorization") String authHeader,
            @PathParam("orderId") String orderId) {
        return trackingController.getOrderDirections(authHeader, orderId);
    }

    @GET
    @Path("/operations/drivers")
    public Response getDriversMonitoring(
            @HeaderParam("Authorization") String authHeader,
            @jakarta.ws.rs.QueryParam("page") @jakarta.ws.rs.DefaultValue("0") int page,
            @jakarta.ws.rs.QueryParam("size") @jakarta.ws.rs.DefaultValue("10") int size,
            @jakarta.ws.rs.QueryParam("search") String search,
            @jakarta.ws.rs.QueryParam("status") String status,
            @jakarta.ws.rs.QueryParam("hasActiveOrder") Boolean hasActiveOrder,
            @jakarta.ws.rs.QueryParam("hasGps") Boolean hasGps) {
        return trackingController.getDriversMonitoring(authHeader, page, size, search, status, hasActiveOrder, hasGps);
    }

    @GET
    @Path("/operations/drivers/{driverId}")
    public Response getDriverMonitoringDetails(
            @HeaderParam("Authorization") String authHeader,
            @PathParam("driverId") String driverId) {
        return trackingController.getDriverMonitoringDetails(authHeader, driverId);
    }
}

