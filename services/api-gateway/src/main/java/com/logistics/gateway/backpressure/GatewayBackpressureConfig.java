package com.logistics.gateway.backpressure;

import com.logistics.common.backpressure.BackpressureConfig;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration manager for Gateway-level In-Flight Request Backpressure.
 */
@ApplicationScoped
public class GatewayBackpressureConfig {

    private static final Logger log = LoggerFactory.getLogger(GatewayBackpressureConfig.class);

    private boolean enabled;
    private int maxConcurrency;
    private int warningConcurrency;
    private long highLatencyMs;
    private long recoveryLatencyMs;
    private long minPacingDelayMs;
    private long maxPacingDelayMs;
    private double ewmaAlpha;

    private BackpressureConfig backpressureConfig;

    @PostConstruct
    public void init() {
        this.enabled = getBooleanConfig("gateway.backpressure.enabled", true);
        this.maxConcurrency = getIntConfig("gateway.backpressure.max-concurrency", 50);
        this.warningConcurrency = getIntConfig("gateway.backpressure.warning-concurrency", 35);
        this.highLatencyMs = getLongConfig("gateway.backpressure.high-latency-ms", 2000);
        this.recoveryLatencyMs = getLongConfig("gateway.backpressure.recovery-latency-ms", 500);
        this.minPacingDelayMs = getLongConfig("gateway.backpressure.min-pacing-delay-ms", 20);
        this.maxPacingDelayMs = getLongConfig("gateway.backpressure.max-pacing-delay-ms", 200);
        this.ewmaAlpha = getDoubleConfig("gateway.backpressure.ewma-alpha", 0.2);

        this.backpressureConfig = BackpressureConfig.builder()
                .name("gateway-inbound")
                .maxConcurrency(maxConcurrency)
                .warningConcurrency(warningConcurrency)
                .highLatencyMs(highLatencyMs)
                .recoveryLatencyMs(recoveryLatencyMs)
                .minPacingDelayMs(minPacingDelayMs)
                .maxPacingDelayMs(maxPacingDelayMs)
                .ewmaAlpha(ewmaAlpha)
                .build();

        log.info("PB-056 Gateway Backpressure initialized: enabled={}, maxConcurrency={}, warningConcurrency={}, highLatencyMs={}ms",
                enabled, maxConcurrency, warningConcurrency, highLatencyMs);
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

    private double getDoubleConfig(String key, double defaultValue) {
        try {
            return ConfigProvider.getConfig().getOptionalValue(key, Double.class).orElse(defaultValue);
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

    public BackpressureConfig getBackpressureConfig() {
        return backpressureConfig;
    }

    public int getMaxConcurrency() {
        return maxConcurrency;
    }

    public int getWarningConcurrency() {
        return warningConcurrency;
    }

    public long getHighLatencyMs() {
        return highLatencyMs;
    }

    public long getRecoveryLatencyMs() {
        return recoveryLatencyMs;
    }
}

