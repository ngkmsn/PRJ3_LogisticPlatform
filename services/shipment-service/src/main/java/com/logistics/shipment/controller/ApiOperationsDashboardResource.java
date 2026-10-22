package com.logistics.shipment.controller;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/shipments/operations/dashboard")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class ApiOperationsDashboardResource {

    @Inject
    OperationsDashboardController operationsDashboardController;

    @GET
    public Response getDashboard(
            @HeaderParam("Authorization") String authHeader,
            @QueryParam("refresh") @DefaultValue("false") boolean refresh) {
        return operationsDashboardController.getDashboard(authHeader, refresh);
    }
}
