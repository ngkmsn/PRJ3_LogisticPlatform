package com.logistics.gateway.resource;

import com.logistics.gateway.proxy.GatewayProxyService;
import com.logistics.gateway.route.GatewayRouteDefinition;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/")
public class GatewayRoutesResource {

    @Inject
    GatewayProxyService gatewayProxyService;

    @GET
    @Path("actuator/gateway/routes")
    @Produces(MediaType.APPLICATION_JSON)
    public List<GatewayRouteDefinition> getActuatorRoutes() {
        return gatewayProxyService.getRouteDefinitions();
    }

    @GET
    @Path("gateway/routes")
    @Produces(MediaType.APPLICATION_JSON)
    public List<GatewayRouteDefinition> getGatewayRoutes() {
        return gatewayProxyService.getRouteDefinitions();
    }
}
