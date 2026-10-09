package com.logistics.user.controller;

import com.logistics.common.dto.ApiResponse;
import com.logistics.user.dto.CreateUserRequest;
import com.logistics.user.dto.PagedResponse;
import com.logistics.user.dto.RoleInfoDto;
import com.logistics.user.dto.UpdateUserRequest;
import com.logistics.user.dto.UpdateUserRoleRequest;
import com.logistics.user.dto.UpdateUserStatusRequest;
import com.logistics.user.dto.UserProfileDto;
import com.logistics.user.service.AuthenticationService;
import com.logistics.user.service.UserService;
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

import java.util.List;

@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class UserController {

    @Inject
    AuthenticationService authenticationService;

    @Inject
    UserService userService;

    @GET
    @Path("/me")
    public Response getCurrentUser(@HeaderParam("Authorization") String authHeader) {
        UserProfileDto profile = authenticationService.getCurrentUserProfile(authHeader);
        return Response.ok(ApiResponse.ok("User profile retrieved successfully", profile)).build();
    }

    @GET
    @Path("/roles")
    public Response listSupportedRoles(@HeaderParam("Authorization") String authHeader) {
        List<RoleInfoDto> roles = userService.listSupportedRoles(authHeader);
        return Response.ok(ApiResponse.ok("Supported roles and permissions retrieved successfully", roles)).build();
    }

    @GET
    public Response listUsers(
            @HeaderParam("Authorization") String authHeader,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("10") int size,
            @QueryParam("role") String role,
            @QueryParam("status") String status) {
        PagedResponse<UserProfileDto> pagedUsers = userService.listUsers(authHeader, page, size, role, status);
        return Response.ok(ApiResponse.ok("Users retrieved successfully", pagedUsers)).build();
    }

    @GET
    @Path("/{id}")
    public Response getUserById(
            @HeaderParam("Authorization") String authHeader,
            @PathParam("id") String id) {
        UserProfileDto profile = userService.getUserById(authHeader, id);
        return Response.ok(ApiResponse.ok("User details retrieved successfully", profile)).build();
    }

    @POST
    public Response createUser(
            @HeaderParam("Authorization") String authHeader,
            @Valid CreateUserRequest request) {
        UserProfileDto created = userService.createUser(authHeader, request);
        return Response.status(Response.Status.CREATED)
                .entity(ApiResponse.ok("User created successfully", created))
                .build();
    }

    @PATCH
    @Path("/{id}")
    public Response updateUser(
            @HeaderParam("Authorization") String authHeader,
            @PathParam("id") String id,
            @Valid UpdateUserRequest request) {
        UserProfileDto updated = userService.updateUser(authHeader, id, request);
        return Response.ok(ApiResponse.ok("User updated successfully", updated)).build();
    }

    @PATCH
    @Path("/{id}/status")
    public Response updateUserStatus(
            @HeaderParam("Authorization") String authHeader,
            @PathParam("id") String id,
            @Valid UpdateUserStatusRequest request) {
        UserProfileDto updated = userService.updateUserStatus(authHeader, id, request);
        return Response.ok(ApiResponse.ok("User status updated successfully", updated)).build();
    }

    @PATCH
    @Path("/{id}/role")
    public Response updateUserRole(
            @HeaderParam("Authorization") String authHeader,
            @PathParam("id") String id,
            @Valid UpdateUserRoleRequest request) {
        UserProfileDto updated = userService.updateUserRole(authHeader, id, request);
        return Response.ok(ApiResponse.ok("User role updated successfully", updated)).build();
    }
}
