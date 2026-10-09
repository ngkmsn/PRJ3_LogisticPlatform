package com.logistics.user.controller;

import com.logistics.user.dto.LoginRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class ApiAuthResource {

    @Inject
    AuthController authController;

    @POST
    @Path("/login")
    public Response login(@Valid LoginRequest request) {
        return authController.login(request);
    }

    @GET
    @Path("/verify")
    public Response verifyToken(@HeaderParam("Authorization") String authHeader) {
        return authController.verifyToken(authHeader);
    }

    @GET
    @Path("/me")
    public Response getCurrentUser(@HeaderParam("Authorization") String authHeader) {
        return authController.getCurrentUser(authHeader);
    }
}
