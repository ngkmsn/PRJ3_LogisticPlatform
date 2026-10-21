package com.logistics.order.controller;

import com.logistics.common.dto.ApiResponse;
import com.logistics.order.dto.CancelOrderRequest;
import com.logistics.order.dto.CreateOrderRequest;
import com.logistics.order.dto.OrderDto;
import com.logistics.order.dto.UpdateOrderStatusRequest;
import com.logistics.order.service.OrderService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.logistics.order.dto.PagedResponse;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;

import com.logistics.order.dto.OrderStatusHistoryDto;
import java.util.List;

@Path("/orders")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class OrderController {

    @Inject
    OrderService orderService;

    @GET
    @Path("/{id}/history")
    public Response getOrderHistory(
            @HeaderParam("Authorization") String authHeader,
            @PathParam("id") String id) {
        List<OrderStatusHistoryDto> history = orderService.getOrderHistory(authHeader, id);
        return Response.ok(ApiResponse.ok("Order history retrieved successfully", history)).build();
    }


    @GET
    public Response listMyOrders(
            @HeaderParam("Authorization") String authHeader,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("10") int size,
            @QueryParam("status") String status) {
        PagedResponse<OrderDto> pagedOrders = orderService.listMyOrders(authHeader, page, size, status);
        return Response.ok(ApiResponse.ok("Orders retrieved successfully", pagedOrders)).build();
    }

    @GET
    @Path("/{id}")
    public Response getOrderDetails(
            @HeaderParam("Authorization") String authHeader,
            @PathParam("id") String id) {
        OrderDto order = orderService.getOrderDetails(authHeader, id);
        return Response.ok(ApiResponse.ok("Order details retrieved successfully", order)).build();
    }

    @GET
    @Path("/{id}/tracking")
    public Response getOrderTracking(
            @HeaderParam("Authorization") String authHeader,
            @PathParam("id") String id,
            @QueryParam("allowStale") @DefaultValue("false") boolean allowStale) {
        com.logistics.common.dto.OrderTrackingDto tracking = orderService.getOrderTracking(authHeader, id, allowStale);
        return Response.ok(ApiResponse.ok("Order driver location retrieved successfully", tracking)).build();
    }


    @POST
    public Response createOrder(
            @HeaderParam("Authorization") String authHeader,
            @Valid CreateOrderRequest request) {
        OrderDto created = orderService.createOrder(authHeader, request);
        return Response.status(Response.Status.CREATED)
                .entity(ApiResponse.ok("Order created successfully", created))
                .build();
    }

    @Inject
    com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @POST
    @Path("/{id}/cancel")
    @Consumes(MediaType.WILDCARD)
    public Response cancelOrder(
            @HeaderParam("Authorization") String authHeader,
            @PathParam("id") String id,
            String body) {
        CancelOrderRequest request = parseCancelRequest(body);
        OrderDto cancelled = orderService.cancelOrder(authHeader, id, request);
        return Response.ok(ApiResponse.ok("Order cancelled successfully", cancelled)).build();
    }

    @jakarta.ws.rs.PUT
    @Path("/{id}/cancel")
    @Consumes(MediaType.WILDCARD)
    public Response cancelOrderPut(
            @HeaderParam("Authorization") String authHeader,
            @PathParam("id") String id,
            String body) {
        return cancelOrder(authHeader, id, body);
    }

    @jakarta.ws.rs.PATCH
    @Path("/{id}/status")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateOrderStatus(
            @HeaderParam("Authorization") String authHeader,
            @PathParam("id") String id,
            @Valid UpdateOrderStatusRequest request) {
        OrderDto updated = orderService.updateOrderStatus(authHeader, id, request);
        return Response.ok(ApiResponse.ok("Order status updated successfully", updated)).build();
    }

    @jakarta.ws.rs.PUT
    @Path("/{id}/status")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateOrderStatusPut(
            @HeaderParam("Authorization") String authHeader,
            @PathParam("id") String id,
            @Valid UpdateOrderStatusRequest request) {
        return updateOrderStatus(authHeader, id, request);
    }

    private CancelOrderRequest parseCancelRequest(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(body, CancelOrderRequest.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid cancel request body: " + e.getMessage());
        }
    }
}
