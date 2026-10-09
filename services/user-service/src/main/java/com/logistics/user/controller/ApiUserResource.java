package com.logistics.user.controller;

import com.logistics.user.dto.CreateUserRequest;
import com.logistics.user.dto.UpdateUserRequest;
import com.logistics.user.dto.UpdateUserStatusRequest;
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

@Path("/api/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class ApiUserResource {

    @Inject
    UserController userController;

    @GET
    @Path("/me")
    public Response getCurrentUser(@HeaderParam("Authorization") String authHeader) {
        return userController.getCurrentUser(authHeader);
    }

    @GET
    public Response listUsers(
            @HeaderParam("Authorization") String authHeader,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("10") int size,
            @QueryParam("role") String role,
            @QueryParam("status") String status) {
        return userController.listUsers(authHeader, page, size, role, status);
    }

    @GET
    @Path("/{id}")
    public Response getUserById(
            @HeaderParam("Authorization") String authHeader,
            @PathParam("id") String id) {
        return userController.getUserById(authHeader, id);
    }

    @POST
    public Response createUser(
            @HeaderParam("Authorization") String authHeader,
            @Valid CreateUserRequest request) {
        return userController.createUser(authHeader, request);
    }

    @PATCH
    @Path("/{id}")
    public Response updateUser(
            @HeaderParam("Authorization") String authHeader,
            @PathParam("id") String id,
            @Valid UpdateUserRequest request) {
        return userController.updateUser(authHeader, id, request);
    }

    @PATCH
    @Path("/{id}/status")
    public Response updateUserStatus(
            @HeaderParam("Authorization") String authHeader,
            @PathParam("id") String id,
            @Valid UpdateUserStatusRequest request) {
        return userController.updateUserStatus(authHeader, id, request);
    }
}
