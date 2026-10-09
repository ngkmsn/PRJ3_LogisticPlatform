package com.logistics.user.controller;

import com.logistics.common.dto.ApiResponse;
import com.logistics.common.security.JwtTokenProvider;
import com.logistics.user.dto.LoginRequest;
import com.logistics.user.dto.LoginResponse;
import com.logistics.user.service.AuthenticationService;
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

import java.util.LinkedHashMap;
import java.util.Map;

@Path("/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class AuthController {

    @Inject
    AuthenticationService authenticationService;

    @Inject
    JwtTokenProvider jwtTokenProvider;

    @POST
    @Path("/login")
    public Response login(@Valid LoginRequest request) {
        LoginResponse response = authenticationService.login(request);
        return Response.ok(ApiResponse.ok("Login successful", response)).build();
    }

    @GET
    @Path("/verify")
    public Response verifyToken(@HeaderParam("Authorization") String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(ApiResponse.error("Missing or invalid Authorization header"))
                    .build();
        }

        String token = authHeader.substring(7).trim();
        if (!jwtTokenProvider.validateToken(token)) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(ApiResponse.error("Token is invalid or expired"))
                    .build();
        }

        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("userId", jwtTokenProvider.extractUserId(token));
        claims.put("username", jwtTokenProvider.extractUsername(token));
        claims.put("email", jwtTokenProvider.extractEmail(token));
        claims.put("role", jwtTokenProvider.extractRole(token));
        claims.put("expiresAt", jwtTokenProvider.extractExpiration(token));

        return Response.ok(ApiResponse.ok("Token is valid", claims)).build();
    }

    @GET
    @Path("/me")
    public Response getCurrentUser(@HeaderParam("Authorization") String authHeader) {
        var profile = authenticationService.getCurrentUserProfile(authHeader);
        return Response.ok(ApiResponse.ok("User profile retrieved successfully", profile)).build();
    }
}
