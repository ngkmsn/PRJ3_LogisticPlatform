package com.logistics.gateway.proxy;

import com.logistics.gateway.route.GatewayRouteDefinition;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.RequestOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@ApplicationScoped
public class GatewayProxyService {

    private static final Logger log = LoggerFactory.getLogger(GatewayProxyService.class);

    @Inject
    Vertx vertx;

    private HttpClient httpClient;
    private final List<GatewayRouteDefinition> routeDefinitions = new ArrayList<>();

    @PostConstruct
    void init() {
        httpClient = vertx.createHttpClient(new HttpClientOptions()
                .setConnectTimeout(10000)
                .setIdleTimeout(60));
    }

    public List<GatewayRouteDefinition> getRouteDefinitions() {
        return Collections.unmodifiableList(routeDefinitions);
    }

    void setupRoutes(@Observes Router router) {
        String userServiceUrl = getTargetUrl("USER_SERVICE_URL", "gateway.user-service.url", "http://localhost:8081");
        String orderServiceUrl = getTargetUrl("ORDER_SERVICE_URL", "gateway.order-service.url", "http://localhost:8082");
        String shipmentServiceUrl = getTargetUrl("SHIPMENT_SERVICE_URL", "gateway.shipment-service.url", "http://localhost:8083");
        String notificationServiceUrl = getTargetUrl("NOTIFICATION_SERVICE_URL", "gateway.notification-service.url", "http://localhost:8084");

        routeDefinitions.clear();
        routeDefinitions.add(new GatewayRouteDefinition("auth-service", userServiceUrl, "/api/auth", true));
        routeDefinitions.add(new GatewayRouteDefinition("auth-service-direct", userServiceUrl, "/auth", false));
        routeDefinitions.add(new GatewayRouteDefinition("user-service", userServiceUrl, "/api/users", true));
        routeDefinitions.add(new GatewayRouteDefinition("driver-service", userServiceUrl, "/api/drivers", true));
        routeDefinitions.add(new GatewayRouteDefinition("driver-service-direct", userServiceUrl, "/drivers", false));
        routeDefinitions.add(new GatewayRouteDefinition("order-service", orderServiceUrl, "/api/orders", true));
        routeDefinitions.add(new GatewayRouteDefinition("shipment-service", shipmentServiceUrl, "/api/shipments", true));
        routeDefinitions.add(new GatewayRouteDefinition("notification-service", notificationServiceUrl, "/api/notifications", true));

        for (GatewayRouteDefinition route : routeDefinitions) {
            String pathPattern = route.getPathPrefix() + "*";
            router.route(pathPattern).handler(ctx -> forwardRequest(ctx, route));
            log.info("Registered gateway route: {} -> {} ({})", route.getId(), route.getUri(), route.getPathPrefix());
        }
    }

    private void forwardRequest(RoutingContext ctx, GatewayRouteDefinition route) {
        try {
            String currentUri = getTargetUrl(resolveEnvKey(route.getId()), null, route.getUri());
            URI targetBaseUri = URI.create(currentUri);

            String incomingPath = ctx.request().path();
            String targetPath = incomingPath;
            if (route.isStripPrefix() && incomingPath.startsWith("/api")) {
                targetPath = incomingPath.substring(4);
            }

            String query = ctx.request().query();
            String requestUri = (query != null && !query.isEmpty()) ? targetPath + "?" + query : targetPath;

            RequestOptions options = new RequestOptions()
                    .setHost(targetBaseUri.getHost())
                    .setPort(targetBaseUri.getPort() != -1 ? targetBaseUri.getPort() : (targetBaseUri.getScheme().equalsIgnoreCase("https") ? 443 : 80))
                    .setSsl(targetBaseUri.getScheme().equalsIgnoreCase("https"))
                    .setURI(requestUri)
                    .setMethod(ctx.request().method());

            ctx.request().headers().forEach(entry -> {
                if (!entry.getKey().equalsIgnoreCase("host")) {
                    options.addHeader(entry.getKey(), entry.getValue());
                }
            });

            httpClient.request(options)
                    .onSuccess(clientReq -> {
                        ctx.request().pipeTo(clientReq)
                                .onSuccess(v -> {
                                    clientReq.response()
                                            .onSuccess(clientResp -> {
                                                ctx.response().setStatusCode(clientResp.statusCode());
                                                clientResp.headers().forEach(entry -> {
                                                    ctx.response().putHeader(entry.getKey(), entry.getValue());
                                                });
                                                clientResp.pipeTo(ctx.response());
                                            })
                                            .onFailure(err -> {
                                                log.error("Error receiving response from backend {}: {}", route.getId(), err.getMessage());
                                                ctx.fail(502, err);
                                            });
                                })
                                .onFailure(err -> {
                                    log.error("Error piping request to backend {}: {}", route.getId(), err.getMessage());
                                    ctx.fail(502, err);
                                });
                    })
                    .onFailure(err -> {
                        log.error("Error connecting to backend {}: {}", route.getId(), err.getMessage());
                        ctx.fail(502, err);
                    });
        } catch (Exception e) {
            log.error("Exception in gateway forwarding for route {}: {}", route.getId(), e.getMessage());
            ctx.fail(500, e);
        }
    }

    private String resolveEnvKey(String routeId) {
        return switch (routeId) {
            case "auth-service", "auth-service-direct", "user-service", "driver-service", "driver-service-direct" -> "USER_SERVICE_URL";
            case "order-service" -> "ORDER_SERVICE_URL";
            case "shipment-service" -> "SHIPMENT_SERVICE_URL";
            case "notification-service" -> "NOTIFICATION_SERVICE_URL";
            default -> "USER_SERVICE_URL";
        };
    }

    private String getTargetUrl(String envKey, String propKey, String fallback) {
        String val = System.getenv(envKey);
        if (val != null && !val.isBlank()) return val;
        val = System.getProperty(envKey);
        if (val != null && !val.isBlank()) return val;
        if (propKey != null) {
            try {
                return ConfigProvider.getConfig().getValue(propKey, String.class);
            } catch (Exception ignored) {}
        }
        return fallback;
    }
}
