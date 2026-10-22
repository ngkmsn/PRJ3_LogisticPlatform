package com.logistics.shipment.controller;

import com.logistics.common.dto.ActionableOrderDto;
import com.logistics.common.dto.ActionableOrdersPageDto;
import com.logistics.common.dto.ApiResponse;
import com.logistics.shipment.service.AssignmentService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/operations/actionable-orders")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class OperationsActionableResource {

    @Inject
    AssignmentService assignmentService;

    @GET
    public Response getActionableOrders(
            @HeaderParam("Authorization") String authHeader,
            @QueryParam("category") @DefaultValue("ALL") String category,
            @QueryParam("status") String status,
            @QueryParam("search") String search,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("10") int size,
            @QueryParam("sort") @DefaultValue("newest_first") String sort) {
        ActionableOrdersPageDto pagedOrders =
                assignmentService.getActionableOrders(authHeader, category, status, search, page, size, sort);
        return Response.ok(ApiResponse.ok("Actionable orders retrieved successfully", pagedOrders)).build();
    }

    @GET
    @Path("/{orderId}")
    public Response getActionableOrderDetail(
            @HeaderParam("Authorization") String authHeader,
            @PathParam("orderId") String orderId) {
        ActionableOrderDto order = assignmentService.getActionableOrderDetail(authHeader, orderId);
        return Response.ok(ApiResponse.ok("Actionable order detail retrieved successfully", order)).build();
    }
}
