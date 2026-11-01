package com.logistics.shipment.controller;

import com.logistics.common.dto.ApiResponse;
import com.logistics.common.dto.DeadLetterMetricsDto;
import com.logistics.shipment.entity.DeadLetterMessage;
import com.logistics.shipment.service.DeadLetterQueueService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Path("/shipments/dlq")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class DeadLetterController {

    @Inject
    DeadLetterQueueService deadLetterQueueService;

    @GET
    public Response listMessages(
            @QueryParam("status") String status,
            @QueryParam("topic") String topic,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size) {
        List<DeadLetterMessage> messages = deadLetterQueueService.listMessages(status, topic, page, size);
        return Response.ok(ApiResponse.ok("DLQ messages retrieved successfully", messages)).build();
    }

    @GET
    @Path("/metrics")
    public Response getMetrics() {
        DeadLetterMetricsDto metrics = deadLetterQueueService.getMetrics();
        return Response.ok(ApiResponse.ok("DLQ metrics retrieved successfully", metrics)).build();
    }

    @GET
    @Path("/{id}")
    public Response getMessageById(@PathParam("id") String id) {
        Optional<DeadLetterMessage> message = deadLetterQueueService.getById(id);
        if (message.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiResponse.error("DLQ message with id '" + id + "' not found"))
                    .build();
        }
        return Response.ok(ApiResponse.ok("DLQ message retrieved successfully", message.get())).build();
    }

    @POST
    @Path("/{id}/replay")
    public Response replayMessage(
            @PathParam("id") String id,
            @HeaderParam("X-User-Id") String userId) {
        String actor = (userId != null && !userId.isBlank()) ? userId : "OPERATOR";
        try {
            boolean success = deadLetterQueueService.replayMessage(id, actor);
            if (!success) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(ApiResponse.error("DLQ message not found for replay: " + id))
                        .build();
            }
            return Response.ok(ApiResponse.ok("DLQ message successfully replayed", Map.of(
                    "id", id,
                    "status", DeadLetterMessage.STATUS_REPLAYED
            ))).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to replay DLQ message: " + e.getMessage()))
                    .build();
        }
    }

    @POST
    @Path("/{id}/discard")
    public Response discardMessage(
            @PathParam("id") String id,
            @QueryParam("reason") @DefaultValue("Administrative discard") String reason) {
        boolean success = deadLetterQueueService.discardMessage(id, reason);
        if (!success) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiResponse.error("DLQ message not found to discard: " + id))
                    .build();
        }
        return Response.ok(ApiResponse.ok("DLQ message successfully discarded", Map.of(
                "id", id,
                "status", DeadLetterMessage.STATUS_DISCARDED,
                "reason", reason
        ))).build();
    }
}
