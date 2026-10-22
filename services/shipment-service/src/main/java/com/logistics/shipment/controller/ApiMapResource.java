package com.logistics.shipment.controller;

import com.logistics.common.dto.RouteDirectionRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/tracking/map")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class ApiMapResource {

    @Inject
    MapController mapController;

    @POST
    @Path("/directions")
    public Response calculateDirections(
            @HeaderParam("Authorization") String authHeader,
            @Valid RouteDirectionRequest request) {
        return mapController.calculateDirections(authHeader, request);
    }

    @GET
    @Path("/directions")
    public Response calculateDirectionsGet(
            @HeaderParam("Authorization") String authHeader,
            @QueryParam("originLat") Double originLat,
            @QueryParam("originLon") Double originLon,
            @QueryParam("destLat") Double destLat,
            @QueryParam("destLon") Double destLon,
            @QueryParam("originAddress") String originAddress,
            @QueryParam("destAddress") String destAddress,
            @QueryParam("vehicleMode") @DefaultValue("MOTORCYCLE") String vehicleMode) {
        return mapController.calculateDirectionsGet(authHeader, originLat, originLon, destLat, destLon, originAddress, destAddress, vehicleMode);
    }

    @GET
    @Path("/geocode")
    public Response geocode(
            @HeaderParam("Authorization") String authHeader,
            @QueryParam("address") String address) {
        return mapController.geocode(authHeader, address);
    }

    @GET
    @Path("/reverse-geocode")
    public Response reverseGeocode(
            @HeaderParam("Authorization") String authHeader,
            @QueryParam("lat") Double lat,
            @QueryParam("lon") Double lon) {
        return mapController.reverseGeocode(authHeader, lat, lon);
    }

    @GET
    @Path("/orders/{orderId}/directions")
    public Response getOrderDirections(
            @HeaderParam("Authorization") String authHeader,
            @PathParam("orderId") String orderId) {
        return mapController.getOrderDirections(authHeader, orderId);
    }
}
