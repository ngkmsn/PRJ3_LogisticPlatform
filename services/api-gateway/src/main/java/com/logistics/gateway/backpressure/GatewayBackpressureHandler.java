package com.logistics.gateway.backpressure;

import com.logistics.common.backpressure.BackpressureController;
import com.logistics.common.backpressure.BackpressureState;
import io.vertx.core.Handler;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reactive Vert.x Route Handler providing In-Flight Concurrency Control and Backpressure.
 * Rejects or paces inbound traffic when downstream microservices experience congestion or high latency.
 */
@ApplicationScoped
public class GatewayBackpressureHandler implements Handler<RoutingContext> {

    private static final Logger log = LoggerFactory.getLogger(GatewayBackpressureHandler.class);

    @Inject
    GatewayBackpressureConfig config;

    private BackpressureController controller;

    @PostConstruct
    public void init() {
        this.controller = new BackpressureController(config.getBackpressureConfig());
        log.info("Initialized Gateway Backpressure Controller with maxConcurrency={}",
                config.getMaxConcurrency());
    }

    void setupBackpressureRoute(@Observes Router router) {
        // Register at order -90 to execute after rate limiting (-100) but before forwarders (0)
        router.route("/*").order(-90).handler(this);
        log.info("Registered Gateway backpressure filter on /* (order: -90)");
    }

    @Override
    public void handle(RoutingContext ctx) {
        if (!config.isEnabled()) {
            ctx.next();
            return;
        }

        String path = ctx.request().path();
        if (isExcludedPath(path)) {
            ctx.next();
            return;
        }

        if (!controller.tryAcquire()) {
            // Downstream is congested: shed load immediately to protect running requests
            ctx.response().setStatusCode(503);
            ctx.response().putHeader("Retry-After", "5");
            ctx.response().putHeader("Content-Type", "application/json");

            String errorJson = String.format(
                    "{\"success\":false,\"message\":\"Gateway backpressure activated: downstream services are congested. Please retry shortly.\"," +
                            "\"data\":{\"state\":\"%s\",\"inFlight\":%d,\"maxConcurrency\":%d,\"retryAfterSeconds\":5}}",
                    controller.getState().name(), controller.getInFlightCount(), controller.getConfig().getMaxConcurrency()
            );

            ctx.response().end(errorJson);
            return;
        }

        long startTime = System.currentTimeMillis();
        ctx.put("bp_start_time", startTime);

        // Attach end handler to release permit and track downstream latency
        ctx.addEndHandler(v -> {
            Long start = ctx.get("bp_start_time");
            long duration = (start != null) ? (System.currentTimeMillis() - start) : 0;
            boolean success = ctx.response().getStatusCode() < 500;
            controller.recordExecution(duration, success);
        });

        // If throttled, apply pacing delay
        controller.applyPacingDelayIfThrottled();

        ctx.next();
    }

    private boolean isExcludedPath(String path) {
        if (path == null) return true;
        return path.startsWith("/q/") ||
                path.startsWith("/api/gateway/") ||
                path.equals("/favicon.ico");
    }

    public BackpressureController getController() {
        return controller;
    }

    public void setController(BackpressureController controller) {
        this.controller = controller;
    }

    public GatewayBackpressureConfig getConfig() {
        return config;
    }

    public void setConfig(GatewayBackpressureConfig config) {
        this.config = config;
    }
}

