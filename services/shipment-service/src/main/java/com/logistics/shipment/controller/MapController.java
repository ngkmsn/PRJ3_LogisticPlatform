package com.logistics.shipment.controller;

import com.logistics.common.dto.ApiResponse;
import com.logistics.common.dto.GeocodeResponse;
import com.logistics.common.dto.MapCoordinatesDto;
import com.logistics.common.dto.RouteDirectionRequest;
import com.logistics.common.dto.RouteDirectionResponse;
import com.logistics.shipment.map.exception.MapInvalidRequestException;
import com.logistics.shipment.map.service.MapService;
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

@Path("/tracking/map")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class MapController {

    @Inject
    MapService mapService;

    @POST
    @Path("/directions")
    public Response calculateDirections(
            @HeaderParam("Authorization") String authHeader,
            @Valid RouteDirectionRequest request) {
        RouteDirectionResponse response = mapService.calculateDirections(request);
        return Response.ok(ApiResponse.ok("Route directions calculated successfully", response)).build();
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

        RouteDirectionRequest request = new RouteDirectionRequest();
        if (originLat != null && originLon != null) {
            request.setOrigin(MapCoordinatesDto.of(originLat, originLon));
        }
        if (destLat != null && destLon != null) {
            request.setDestination(MapCoordinatesDto.of(destLat, destLon));
        }
        request.setOriginAddress(originAddress);
        request.setDestinationAddress(destAddress);
        request.setVehicleMode(vehicleMode);

        RouteDirectionResponse response = mapService.calculateDirections(request);
        return Response.ok(ApiResponse.ok("Route directions calculated successfully", response)).build();
    }

    @GET
    @Path("/geocode")
    public Response geocode(
            @HeaderParam("Authorization") String authHeader,
            @QueryParam("address") String address) {
        if (address == null || address.trim().isEmpty()) {
            throw new MapInvalidRequestException("Address parameter cannot be empty");
        }
        GeocodeResponse response = mapService.geocode(address);
        return Response.ok(ApiResponse.ok("Address geocoded successfully", response)).build();
    }

    @GET
    @Path("/reverse-geocode")
    public Response reverseGeocode(
            @HeaderParam("Authorization") String authHeader,
            @QueryParam("lat") Double lat,
            @QueryParam("lon") Double lon) {
        if (lat == null || lon == null) {
            throw new MapInvalidRequestException("Latitude and longitude parameters are required");
        }
        GeocodeResponse response = mapService.reverseGeocode(lat, lon);
        return Response.ok(ApiResponse.ok("Coordinates reverse geocoded successfully", response)).build();
    }

    @GET
    @Path("/orders/{orderId}/directions")
    public Response getOrderDirections(
            @HeaderParam("Authorization") String authHeader,
            @PathParam("orderId") String orderId) {
        RouteDirectionResponse response = mapService.getOrderDirections(orderId);
        return Response.ok(ApiResponse.ok("Order directions calculated successfully", response)).build();
    }
}
