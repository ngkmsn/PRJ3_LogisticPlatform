package com.logistics.user.controller;

import com.logistics.user.dto.CreateDriverProfileRequest;
import com.logistics.user.dto.UpdateDriverAvailabilityRequest;
import com.logistics.user.dto.UpdateDriverProfileRequest;
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

@Path("/api/drivers")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class ApiDriverResource {

    @Inject
    DriverController driverController;

    @GET
    @Path("/me")
    public Response getMyDriverProfile(@HeaderParam("Authorization") String authHeader) {
        return driverController.getMyDriverProfile(authHeader);
    }

    @GET
    @Path("/me/availability")
    public Response getMyAvailability(@HeaderParam("Authorization") String authHeader) {
        return driverController.getMyAvailability(authHeader);
    }

    @PATCH
    @Path("/me/availability")
    public Response updateMyAvailability(
            @HeaderParam("Authorization") String authHeader,
            @Valid UpdateDriverAvailabilityRequest request) {
        return driverController.updateMyAvailability(authHeader, request);
    }

    @GET
    @Path("/me/eligibility")
    public Response getMyEligibility(@HeaderParam("Authorization") String authHeader) {
        return driverController.getMyEligibility(authHeader);
    }

    @GET
    public Response listDrivers(
            @HeaderParam("Authorization") String authHeader,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("10") int size,
            @QueryParam("status") String status) {
        return driverController.listDrivers(authHeader, page, size, status);
    }

    @GET
    @Path("/{id}")
    public Response getDriverById(
            @HeaderParam("Authorization") String authHeader,
            @PathParam("id") String id) {
        return driverController.getDriverById(authHeader, id);
    }

    @POST
    public Response createDriverProfile(
            @HeaderParam("Authorization") String authHeader,
            @Valid CreateDriverProfileRequest request) {
        return driverController.createDriverProfile(authHeader, request);
    }

    @PATCH
    @Path("/{id}")
    public Response updateDriverProfile(
            @HeaderParam("Authorization") String authHeader,
            @PathParam("id") String id,
            @Valid UpdateDriverProfileRequest request) {
        return driverController.updateDriverProfile(authHeader, id, request);
    }

    @GET
    @Path("/{id}/availability")
    public Response getDriverAvailability(
            @HeaderParam("Authorization") String authHeader,
            @PathParam("id") String id) {
        return driverController.getDriverAvailability(authHeader, id);
    }

    @PATCH
    @Path("/{id}/availability")
    public Response updateDriverAvailability(
            @HeaderParam("Authorization") String authHeader,
            @PathParam("id") String id,
            @Valid UpdateDriverAvailabilityRequest request) {
        return driverController.updateDriverAvailability(authHeader, id, request);
    }

    @GET
    @Path("/{id}/eligibility")
    public Response getDriverEligibility(
            @HeaderParam("Authorization") String authHeader,
            @PathParam("id") String id) {
        return driverController.getDriverEligibility(authHeader, id);
    }
}
