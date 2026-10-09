package com.logistics.user.controller;

import com.logistics.common.dto.ApiResponse;
import com.logistics.common.dto.DriverEligibilityResultDto;
import com.logistics.user.dto.CreateDriverProfileRequest;
import com.logistics.user.dto.DriverAvailabilityDto;
import com.logistics.user.dto.DriverProfileDto;
import com.logistics.user.dto.PagedResponse;
import com.logistics.user.dto.UpdateDriverAvailabilityRequest;
import com.logistics.user.dto.UpdateDriverProfileRequest;
import com.logistics.user.service.DriverEligibilityService;
import com.logistics.user.service.DriverService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/drivers")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class DriverController {

    @Inject
    DriverService driverService;

    @Inject
    DriverEligibilityService driverEligibilityService;

    @GET
    @Path("/me")
    public Response getMyDriverProfile(@HeaderParam("Authorization") String authHeader) {
        DriverProfileDto profile = driverService.getMyDriverProfile(authHeader);
        return Response.ok(ApiResponse.ok("Driver profile retrieved successfully", profile)).build();
    }

    @GET
    @Path("/me/availability")
    public Response getMyAvailability(@HeaderParam("Authorization") String authHeader) {
        DriverAvailabilityDto availability = driverService.getMyAvailability(authHeader);
        return Response.ok(ApiResponse.ok("Driver availability retrieved successfully", availability)).build();
    }

    @PATCH
    @Path("/me/availability")
    public Response updateMyAvailability(
            @HeaderParam("Authorization") String authHeader,
            @Valid UpdateDriverAvailabilityRequest request) {
        DriverAvailabilityDto updated = driverService.updateMyAvailability(authHeader, request);
        return Response.ok(ApiResponse.ok("Driver availability updated successfully", updated)).build();
    }

    @GET
    @Path("/me/eligibility")
    public Response getMyEligibility(@HeaderParam("Authorization") String authHeader) {
        DriverEligibilityResultDto result = driverEligibilityService.getMyEligibility(authHeader);
        return Response.ok(ApiResponse.ok("Driver eligibility evaluated successfully", result)).build();
    }

    @GET
    public Response listDrivers(
            @HeaderParam("Authorization") String authHeader,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("10") int size,
            @QueryParam("status") String status) {
        PagedResponse<DriverProfileDto> pagedDrivers = driverService.listDrivers(authHeader, page, size, status);
        return Response.ok(ApiResponse.ok("Driver profiles retrieved successfully", pagedDrivers)).build();
    }

    @GET
    @Path("/{id}")
    public Response getDriverById(
            @HeaderParam("Authorization") String authHeader,
            @PathParam("id") String id) {
        DriverProfileDto profile = driverService.getDriverById(authHeader, id);
        return Response.ok(ApiResponse.ok("Driver profile retrieved successfully", profile)).build();
    }

    @POST
    public Response createDriverProfile(
            @HeaderParam("Authorization") String authHeader,
            @Valid CreateDriverProfileRequest request) {
        DriverProfileDto created = driverService.createDriverProfile(authHeader, request);
        return Response.status(Response.Status.CREATED)
                .entity(ApiResponse.ok("Driver profile created successfully", created))
                .build();
    }

    @PATCH
    @Path("/{id}")
    public Response updateDriverProfile(
            @HeaderParam("Authorization") String authHeader,
            @PathParam("id") String id,
            @Valid UpdateDriverProfileRequest request) {
        DriverProfileDto updated = driverService.updateDriverProfile(authHeader, id, request);
        return Response.ok(ApiResponse.ok("Driver profile updated successfully", updated)).build();
    }

    @GET
    @Path("/{id}/availability")
    public Response getDriverAvailability(
            @HeaderParam("Authorization") String authHeader,
            @PathParam("id") String id) {
        DriverAvailabilityDto availability = driverService.getDriverAvailability(authHeader, id);
        return Response.ok(ApiResponse.ok("Driver availability retrieved successfully", availability)).build();
    }

    @PATCH
    @Path("/{id}/availability")
    public Response updateDriverAvailability(
            @HeaderParam("Authorization") String authHeader,
            @PathParam("id") String id,
            @Valid UpdateDriverAvailabilityRequest request) {
        DriverAvailabilityDto updated = driverService.updateDriverAvailability(authHeader, id, request);
        return Response.ok(ApiResponse.ok("Driver availability updated successfully", updated)).build();
    }

    @GET
    @Path("/{id}/eligibility")
    public Response getDriverEligibility(
            @HeaderParam("Authorization") String authHeader,
            @PathParam("id") String id) {
        DriverEligibilityResultDto result = driverEligibilityService.getDriverEligibility(authHeader, id);
        return Response.ok(ApiResponse.ok("Driver eligibility evaluated successfully", result)).build();
    }
}
