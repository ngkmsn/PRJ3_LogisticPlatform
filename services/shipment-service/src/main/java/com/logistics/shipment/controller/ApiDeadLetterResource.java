package com.logistics.shipment.controller;

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

@Path("/api/shipments/dlq")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class ApiDeadLetterResource {

    @Inject
    DeadLetterController deadLetterController;

    @GET
    public Response listMessages(
            @QueryParam("status") String status,
            @QueryParam("topic") String topic,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size) {
        return deadLetterController.listMessages(status, topic, page, size);
    }

    @GET
    @Path("/metrics")
    public Response getMetrics() {
        return deadLetterController.getMetrics();
    }

    @GET
    @Path("/{id}")
    public Response getMessageById(@PathParam("id") String id) {
        return deadLetterController.getMessageById(id);
    }

    @POST
    @Path("/{id}/replay")
    public Response replayMessage(
            @PathParam("id") String id,
            @HeaderParam("X-User-Id") String userId) {
        return deadLetterController.replayMessage(id, userId);
    }

    @POST
    @Path("/{id}/discard")
    public Response discardMessage(
            @PathParam("id") String id,
            @QueryParam("reason") @DefaultValue("Administrative discard") String reason) {
        return deadLetterController.discardMessage(id, reason);
    }
}
