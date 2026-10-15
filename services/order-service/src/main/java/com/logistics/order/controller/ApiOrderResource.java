package com.logistics.order.controller;

import com.logistics.order.dto.CreateOrderRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/orders")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class ApiOrderResource {

    @Inject
    OrderController orderController;

    @GET
    public Response listMyOrders(
            @HeaderParam("Authorization") String authHeader,
            @jakarta.ws.rs.QueryParam("page") @jakarta.ws.rs.DefaultValue("0") int page,
            @jakarta.ws.rs.QueryParam("size") @jakarta.ws.rs.DefaultValue("10") int size,
            @jakarta.ws.rs.QueryParam("status") String status) {
        return orderController.listMyOrders(authHeader, page, size, status);
    }

    @GET
    @Path("/{id}")
    public Response getOrderDetails(
            @HeaderParam("Authorization") String authHeader,
            @jakarta.ws.rs.PathParam("id") String id) {
        return orderController.getOrderDetails(authHeader, id);
    }

    @POST
    public Response createOrder(
            @HeaderParam("Authorization") String authHeader,
            @Valid CreateOrderRequest request) {
        return orderController.createOrder(authHeader, request);
    }

    @POST
    @Path("/{id}/cancel")
    @Consumes(MediaType.WILDCARD)
    public Response cancelOrder(
            @HeaderParam("Authorization") String authHeader,
            @jakarta.ws.rs.PathParam("id") String id,
            String body) {
        return orderController.cancelOrder(authHeader, id, body);
    }

    @jakarta.ws.rs.PUT
    @Path("/{id}/cancel")
    @Consumes(MediaType.WILDCARD)
    public Response cancelOrderPut(
            @HeaderParam("Authorization") String authHeader,
            @jakarta.ws.rs.PathParam("id") String id,
            String body) {
        return orderController.cancelOrderPut(authHeader, id, body);
    }

    @jakarta.ws.rs.PATCH
    @Path("/{id}/status")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateOrderStatus(
            @HeaderParam("Authorization") String authHeader,
            @jakarta.ws.rs.PathParam("id") String id,
            @Valid com.logistics.order.dto.UpdateOrderStatusRequest request) {
        return orderController.updateOrderStatus(authHeader, id, request);
    }

    @jakarta.ws.rs.PUT
    @Path("/{id}/status")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateOrderStatusPut(
            @HeaderParam("Authorization") String authHeader,
            @jakarta.ws.rs.PathParam("id") String id,
            @Valid com.logistics.order.dto.UpdateOrderStatusRequest request) {
        return orderController.updateOrderStatusPut(authHeader, id, request);
    }

    @GET
    @Path("/{id}/history")
    public Response getOrderHistory(
            @HeaderParam("Authorization") String authHeader,
            @jakarta.ws.rs.PathParam("id") String id) {
        return orderController.getOrderHistory(authHeader, id);
    }
}
