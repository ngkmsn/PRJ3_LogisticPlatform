package com.logistics.order.controller;

import com.logistics.common.dto.ApiResponse;
import com.logistics.order.dto.CreateOrderRequest;
import com.logistics.order.dto.OrderDto;
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
import jakarta.ws.rs.QueryParam;

@Path("/orders")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class OrderController {

    @Inject
    OrderService orderService;

    @GET
    public Response listMyOrders(
            @HeaderParam("Authorization") String authHeader,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("10") int size,
            @QueryParam("status") String status) {
        PagedResponse<OrderDto> pagedOrders = orderService.listMyOrders(authHeader, page, size, status);
        return Response.ok(ApiResponse.ok("Orders retrieved successfully", pagedOrders)).build();
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
}
