package com.logistics.shipment.controller;

import com.logistics.common.dto.ApiResponse;
import com.logistics.shipment.dto.DriverLocationDto;
import com.logistics.shipment.dto.UpdateDriverLocationRequest;
import com.logistics.shipment.entity.DriverLocation;
import com.logistics.shipment.service.TrackingService;
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

@Path("/tracking")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class TrackingController {

    @Inject
    TrackingService trackingService;

    @POST
    @Path("/location")
    public Response updateLocation(
            @HeaderParam("Authorization") String authHeader,
            @Valid UpdateDriverLocationRequest request) {
        DriverLocation location = trackingService.recordDriverLocation(authHeader, request);
        return Response.ok(ApiResponse.ok("Driver GPS location recorded successfully", DriverLocationDto.from(location))).build();
    }

    @POST
    @Path("/drivers/location")
    public Response updateDriverLocationAlias(
            @HeaderParam("Authorization") String authHeader,
            @Valid UpdateDriverLocationRequest request) {
        return updateLocation(authHeader, request);
    }

    @GET
    @Path("/location")
    public Response getCurrentLocation(@HeaderParam("Authorization") String authHeader) {
        DriverLocation location = trackingService.getCurrentDriverLocation(authHeader)
                .orElseThrow(() -> new IllegalArgumentException("No recorded GPS location found for current driver"));
        return Response.ok(ApiResponse.ok("Current GPS location retrieved successfully", DriverLocationDto.from(location))).build();
    }

    @GET
    @Path("/drivers/{driverId}/location")
    public Response getDriverLocation(
            @HeaderParam("Authorization") String authHeader,
            @PathParam("driverId") String driverId) {
        DriverLocation location = trackingService.getDriverLocation(authHeader, driverId)
                .orElseThrow(() -> new IllegalArgumentException("No recorded GPS location found for driver: " + driverId));
        return Response.ok(ApiResponse.ok("Driver GPS location retrieved successfully", DriverLocationDto.from(location))).build();
    }

    @GET
    @Path("/orders/{orderId}")
    public Response getOrderTracking(
            @HeaderParam("Authorization") String authHeader,
            @PathParam("orderId") String orderId,
            @jakarta.ws.rs.QueryParam("allowStale") @jakarta.ws.rs.DefaultValue("false") boolean allowStale) {
        com.logistics.common.dto.OrderTrackingDto tracking = trackingService.getOrderTracking(authHeader, orderId, allowStale);
        return Response.ok(ApiResponse.ok("Order driver location retrieved successfully", tracking)).build();
    }

    @GET
    @Path("/orders/{orderId}/location")
    public Response getOrderTrackingLocationAlias(
            @HeaderParam("Authorization") String authHeader,
            @PathParam("orderId") String orderId,
            @jakarta.ws.rs.QueryParam("allowStale") @jakarta.ws.rs.DefaultValue("false") boolean allowStale) {
        return getOrderTracking(authHeader, orderId, allowStale);
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
        com.logistics.shipment.dto.PagedResponse<com.logistics.common.dto.DriverMonitoringDto> paged =
                trackingService.getDriversMonitoring(authHeader, page, size, search, status, hasActiveOrder, hasGps);
        return Response.ok(ApiResponse.ok("Driver monitoring data retrieved successfully", paged)).build();
    }

    @GET
    @Path("/operations/drivers/{driverId}")
    public Response getDriverMonitoringDetails(
            @HeaderParam("Authorization") String authHeader,
            @PathParam("driverId") String driverId) {
        com.logistics.common.dto.DriverMonitoringDto driver =
                trackingService.getDriverMonitoringDetails(authHeader, driverId);
        return Response.ok(ApiResponse.ok("Driver monitoring details retrieved successfully", driver)).build();
    }

    @GET
    @Path("/drivers")
    public Response getDriversAlias(
            @HeaderParam("Authorization") String authHeader,
            @jakarta.ws.rs.QueryParam("page") @jakarta.ws.rs.DefaultValue("0") int page,
            @jakarta.ws.rs.QueryParam("size") @jakarta.ws.rs.DefaultValue("10") int size,
            @jakarta.ws.rs.QueryParam("search") String search,
            @jakarta.ws.rs.QueryParam("status") String status,
            @jakarta.ws.rs.QueryParam("hasActiveOrder") Boolean hasActiveOrder,
            @jakarta.ws.rs.QueryParam("hasGps") Boolean hasGps) {
        return getDriversMonitoring(authHeader, page, size, search, status, hasActiveOrder, hasGps);
    }
}

