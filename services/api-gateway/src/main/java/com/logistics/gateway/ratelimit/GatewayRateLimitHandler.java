package com.logistics.gateway.ratelimit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.logistics.common.ratelimit.*;
import io.vertx.core.Handler;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Reactive Vert.x Route Handler providing tiered Inbound Rate Limiting at the API Gateway.
 * Intercepts requests before route forwarders to prevent downstream microservice exhaustion.
 */
@ApplicationScoped
public class GatewayRateLimitHandler implements Handler<RoutingContext> {

    private static final Logger log = LoggerFactory.getLogger(GatewayRateLimitHandler.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    GatewayRateLimitConfig config;

    private RateLimiter rateLimiter;
    private RateLimiterStorage storage;

    @PostConstruct
    public void init() {
        if ("redis".equalsIgnoreCase(config.getStorageType())) {
            InMemoryRateLimiterStorage fallback = new InMemoryRateLimiterStorage();
            this.storage = new RedisRateLimiterStorage(
                    config.getRedisHost(),
                    config.getRedisPort(),
                    config.getRedisTimeoutMs(),
                    config.getFailPolicy(),
                    fallback
            );
            log.info("Initialized Gateway Rate Limiting with Redis storage ({}:{}) and failPolicy={}",
                    config.getRedisHost(), config.getRedisPort(), config.getFailPolicy());
        } else {
            this.storage = new InMemoryRateLimiterStorage();
            log.info("Initialized Gateway Rate Limiting with In-Memory storage");
        }

        this.rateLimiter = new RateLimiter(storage);
    }

    void setupRateLimitRoute(@Observes Router router) {
        // Register at order -100 to execute before forwardRequest handlers
        router.route("/*").order(-100).handler(this);
        log.info("Registered Gateway rate limit filter on /* (order: -100)");
    }

    @Override
    public void handle(RoutingContext ctx) {
        if (!config.isEnabled()) {
            ctx.next();
            return;
        }

        String path = ctx.request().path();

        // Exclude system, telemetry, and gateway management endpoints
        if (isExcludedPath(path)) {
            ctx.next();
            return;
        }

        RateLimitPolicy policy = resolvePolicy(path);
        String clientKey = extractClientKey(ctx, policy.getKeyType());

        RateLimitResult result = rateLimiter.checkAndConsume(clientKey, policy);

        // Attach standard rate limit headers to response
        ctx.response().putHeader("X-RateLimit-Limit", String.valueOf(result.getLimit()));
        ctx.response().putHeader("X-RateLimit-Remaining", String.valueOf(result.getRemaining()));
        ctx.response().putHeader("X-RateLimit-Reset", String.valueOf(result.getResetEpochSeconds()));

        if (result.isAllowed()) {
            ctx.next();
        } else {
            // Rate limit exceeded: return HTTP 429 Too Many Requests
            long retryAfter = result.getRetryAfterSeconds();
            ctx.response().setStatusCode(429);
            ctx.response().putHeader("Retry-After", String.valueOf(retryAfter));
            ctx.response().putHeader("Content-Type", "application/json");

            String errorJson = String.format(
                    "{\"success\":false,\"message\":\"Rate limit exceeded. Please retry after %d seconds.\"," +
                            "\"data\":{\"policy\":\"%s\",\"limit\":%d,\"retryAfterSeconds\":%d,\"resetEpochSeconds\":%d}}",
                    retryAfter, result.getPolicyName(), result.getLimit(), retryAfter, result.getResetEpochSeconds()
            );

            ctx.response().end(errorJson);
        }
    }

    private boolean isExcludedPath(String path) {
        if (path == null) return true;
        return path.startsWith("/q/") ||
                path.startsWith("/api/gateway/") ||
                path.equals("/favicon.ico");
    }

    private RateLimitPolicy resolvePolicy(String path) {
        if (path.startsWith("/api/auth") || path.startsWith("/auth")) {
            return config.getAuthPolicy();
        }
        if (path.startsWith("/api/orders")) {
            return config.getOrdersPolicy();
        }
        if (path.startsWith("/api/shipments/tracking") || path.startsWith("/api/shipments/dashboard")) {
            return config.getTrackingPolicy();
        }
        return config.getDefaultPolicy();
    }

    private String extractClientKey(RoutingContext ctx, RateLimitKeyType keyType) {
        String clientIp = extractClientIp(ctx);

        if (keyType == RateLimitKeyType.IP_ONLY) {
            return "ip:" + clientIp;
        }

        // Check X-User-Id header
        String userId = ctx.request().getHeader("X-User-Id");
        if (userId != null && !userId.isBlank()) {
            return "user:" + userId.trim();
        }

        // Check JWT Bearer token
        String authHeader = ctx.request().getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String tokenUserId = extractUserIdFromJwt(authHeader.substring(7));
            if (tokenUserId != null && !tokenUserId.isBlank()) {
                return "user:" + tokenUserId;
            }
        }

        if (keyType == RateLimitKeyType.USER_ONLY) {
            return "user:unauthenticated";
        }

        return "ip:" + clientIp;
    }

    private String extractClientIp(RoutingContext ctx) {
        String xff = ctx.request().getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int commaIdx = xff.indexOf(',');
            return (commaIdx != -1 ? xff.substring(0, commaIdx) : xff).trim();
        }
        String realIp = ctx.request().getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        if (ctx.request().remoteAddress() != null && ctx.request().remoteAddress().host() != null) {
            return ctx.request().remoteAddress().host();
        }
        return "127.0.0.1";
    }

    private String extractUserIdFromJwt(String jwtToken) {
        try {
            String[] parts = jwtToken.split("\\.");
            if (parts.length >= 2) {
                byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
                JsonNode payload = objectMapper.readTree(decoded);
                if (payload.hasNonNull("userId")) {
                    return payload.get("userId").asText();
                }
                if (payload.hasNonNull("sub")) {
                    return payload.get("sub").asText();
                }
            }
        } catch (Exception e) {
            log.debug("Unable to extract user identity from JWT: {}", e.getMessage());
        }
        return null;
    }

    public RateLimiter getRateLimiter() {
        return rateLimiter;
    }

    public void setRateLimiter(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    public GatewayRateLimitConfig getConfig() {
        return config;
    }

    public void setConfig(GatewayRateLimitConfig config) {
        this.config = config;
    }
}
