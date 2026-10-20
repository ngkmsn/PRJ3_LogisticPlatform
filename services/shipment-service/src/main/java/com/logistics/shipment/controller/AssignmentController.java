package com.logistics.shipment.controller;

import com.logistics.common.dto.ApiResponse;
import com.logistics.shipment.dto.AssignmentResponseDto;
import com.logistics.shipment.dto.ManualAssignDriverRequest;
import com.logistics.shipment.dto.PagedResponse;
import com.logistics.shipment.dto.UnassignedOrderDto;
import com.logistics.shipment.entity.Assignment;
import com.logistics.shipment.service.AssignmentService;
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

@Path("/assignments")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class AssignmentController {

    @Inject
    AssignmentService assignmentService;

    @GET
    @Path("/unassigned")
    public Response getUnassignedOrders(
            @HeaderParam("Authorization") String authHeader,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("10") int size,
            @QueryParam("status") @DefaultValue("UNASSIGNED") String status,
            @QueryParam("sort") @DefaultValue("oldest_first") String sort) {
        PagedResponse<UnassignedOrderDto> pagedOrders = assignmentService.getUnassignedOrders(authHeader, page, size, status, sort);
        return Response.ok(ApiResponse.ok("Unassigned orders retrieved successfully", pagedOrders)).build();
    }

    @GET
    @Path("/unassigned/{orderId}")
    public Response getUnassignedOrderDetail(
            @HeaderParam("Authorization") String authHeader,
            @PathParam("orderId") String orderId) {
        UnassignedOrderDto order = assignmentService.getUnassignedOrderDetail(authHeader, orderId);
        return Response.ok(ApiResponse.ok("Unassigned order detail retrieved successfully", order)).build();
    }

    @POST
    @Path("/{orderId}/manual-assign")
    public Response manualAssignDriver(
            @HeaderParam("Authorization") String authHeader,
            @PathParam("orderId") String orderId,
            @Valid ManualAssignDriverRequest request) {
        Assignment assignment = assignmentService.manualAssignDriver(authHeader, orderId, request);
        return Response.ok(ApiResponse.ok("Driver manually assigned successfully", AssignmentResponseDto.from(assignment))).build();
    }

    @POST
    @Path("/{orderId}/reassign")
    public Response reassignDriver(
            @HeaderParam("Authorization") String authHeader,
            @PathParam("orderId") String orderId,
            @Valid com.logistics.shipment.dto.ReassignDriverRequest request) {
        Assignment assignment = assignmentService.reassignDriver(authHeader, orderId, request);
        return Response.ok(ApiResponse.ok("Driver reassigned successfully", AssignmentResponseDto.from(assignment))).build();
    }

    @GET
    @Path("/{orderId}/history")
    public Response getAssignmentHistory(
            @HeaderParam("Authorization") String authHeader,
            @PathParam("orderId") String orderId) {
        java.util.List<com.logistics.shipment.dto.AssignmentHistoryDto> history = assignmentService.getAssignmentHistory(authHeader, orderId);
        return Response.ok(ApiResponse.ok("Assignment history retrieved successfully", history)).build();
    }
}
