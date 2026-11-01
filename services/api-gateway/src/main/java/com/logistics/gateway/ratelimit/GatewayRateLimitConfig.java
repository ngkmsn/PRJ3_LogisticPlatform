package com.logistics.gateway.ratelimit;

import com.logistics.common.ratelimit.FailPolicy;
import com.logistics.common.ratelimit.RateLimitKeyType;
import com.logistics.common.ratelimit.RateLimitPolicy;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration manager for Gateway Rate Limiting policies and storage backends.
 * Reads settings from application.properties or environment variables.
 */
@ApplicationScoped
public class GatewayRateLimitConfig {

    private static final Logger log = LoggerFactory.getLogger(GatewayRateLimitConfig.class);

    private boolean enabled;
    private String storageType;
    private FailPolicy failPolicy;
    private String redisHost;
    private int redisPort;
    private int redisTimeoutMs;

    private RateLimitPolicy authPolicy;
    private RateLimitPolicy ordersPolicy;
    private RateLimitPolicy trackingPolicy;
    private RateLimitPolicy defaultPolicy;

    @PostConstruct
    public void init() {
        this.enabled = getBooleanConfig("gateway.rate-limit.enabled", true);
        this.storageType = getStringConfig("gateway.rate-limit.storage", "in-memory").toLowerCase();
        
        String failPolicyStr = getStringConfig("gateway.rate-limit.fail-policy", "FAIL_OPEN");
        this.failPolicy = "FAIL_CLOSED".equalsIgnoreCase(failPolicyStr) ? FailPolicy.FAIL_CLOSED : FailPolicy.FAIL_OPEN;

        this.redisHost = getStringConfig("gateway.rate-limit.redis.host", "localhost");
        this.redisPort = getIntConfig("gateway.rate-limit.redis.port", 6379);
        this.redisTimeoutMs = getIntConfig("gateway.rate-limit.redis.timeout-ms", 2000);

        // Load tiered policy thresholds
        long authLimit = getLongConfig("gateway.rate-limit.policies.auth.limit", 10);
        long authWindow = getLongConfig("gateway.rate-limit.policies.auth.window-seconds", 60);
        this.authPolicy = new RateLimitPolicy("auth", authLimit, authWindow, RateLimitKeyType.IP_ONLY);

        long ordersLimit = getLongConfig("gateway.rate-limit.policies.orders.limit", 30);
        long ordersWindow = getLongConfig("gateway.rate-limit.policies.orders.window-seconds", 60);
        this.ordersPolicy = new RateLimitPolicy("orders", ordersLimit, ordersWindow, RateLimitKeyType.IP_AND_USER);

        long trackingLimit = getLongConfig("gateway.rate-limit.policies.tracking.limit", 120);
        long trackingWindow = getLongConfig("gateway.rate-limit.policies.tracking.window-seconds", 60);
        this.trackingPolicy = new RateLimitPolicy("tracking", trackingLimit, trackingWindow, RateLimitKeyType.IP_AND_USER);

        long defaultLimit = getLongConfig("gateway.rate-limit.policies.default.limit", 60);
        long defaultWindow = getLongConfig("gateway.rate-limit.policies.default.window-seconds", 60);
        this.defaultPolicy = new RateLimitPolicy("default", defaultLimit, defaultWindow, RateLimitKeyType.IP_AND_USER);

        log.info("PB-055 Gateway Rate Limiting initialized: enabled={}, storage={}, failPolicy={}, policies=[auth: {}/{}, orders: {}/{}, tracking: {}/{}, default: {}/{}]",
                enabled, storageType, failPolicy,
                authLimit, authWindow, ordersLimit, ordersWindow, trackingLimit, trackingWindow, defaultLimit, defaultWindow);
    }

    private String getStringConfig(String key, String defaultValue) {
        try {
            return ConfigProvider.getConfig().getOptionalValue(key, String.class).orElse(defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private boolean getBooleanConfig(String key, boolean defaultValue) {
        try {
            return ConfigProvider.getConfig().getOptionalValue(key, Boolean.class).orElse(defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private int getIntConfig(String key, int defaultValue) {
        try {
            return ConfigProvider.getConfig().getOptionalValue(key, Integer.class).orElse(defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private long getLongConfig(String key, long defaultValue) {
        try {
            return ConfigProvider.getConfig().getOptionalValue(key, Long.class).orElse(defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getStorageType() {
        return storageType;
    }

    public FailPolicy getFailPolicy() {
        return failPolicy;
    }

    public String getRedisHost() {
        return redisHost;
    }

    public int getRedisPort() {
        return redisPort;
    }

    public int getRedisTimeoutMs() {
        return redisTimeoutMs;
    }

    public RateLimitPolicy getAuthPolicy() {
        return authPolicy;
    }

    public RateLimitPolicy getOrdersPolicy() {
        return ordersPolicy;
    }

    public RateLimitPolicy getTrackingPolicy() {
        return trackingPolicy;
    }

    public RateLimitPolicy getDefaultPolicy() {
        return defaultPolicy;
    }
}

