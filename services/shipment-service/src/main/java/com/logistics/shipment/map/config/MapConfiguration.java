package com.logistics.shipment.map.config;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Configuration parameters for the Map Service integration (PB-047).
 * Supports pluggable providers (MOCK, OSRM, GENERIC_REST) configured via
 * environment variables or application properties without hardcoded secrets.
 */
@ApplicationScoped
public class MapConfiguration {

    @ConfigProperty(name = "logistics.map.provider.type", defaultValue = "MOCK")
    String providerType;

    @ConfigProperty(name = "logistics.map.service.url")
    java.util.Optional<String> serviceUrl = java.util.Optional.empty();

    @ConfigProperty(name = "logistics.map.api.key")
    java.util.Optional<String> apiKey = java.util.Optional.empty();

    @ConfigProperty(name = "logistics.map.timeout-ms", defaultValue = "3000")
    long timeoutMs;

    @ConfigProperty(name = "logistics.map.fallback.enabled", defaultValue = "true")
    boolean fallbackEnabled;

    @ConfigProperty(name = "logistics.map.rate-limit.permits-per-second", defaultValue = "50")
    int rateLimitPermitsPerSecond;

    public MapConfiguration() {
    }

    public String getProviderType() {
        return providerType != null ? providerType.toUpperCase().trim() : "MOCK";
    }

    public void setProviderType(String providerType) {
        this.providerType = providerType;
    }

    public String getServiceUrl() {
        return serviceUrl != null && serviceUrl.isPresent() ? serviceUrl.get().trim() : "";
    }

    public void setServiceUrl(String serviceUrl) {
        this.serviceUrl = java.util.Optional.ofNullable(serviceUrl);
    }

    public String getApiKey() {
        return apiKey != null && apiKey.isPresent() ? apiKey.get().trim() : "";
    }

    public void setApiKey(String apiKey) {
        this.apiKey = java.util.Optional.ofNullable(apiKey);
    }

    public long getTimeoutMs() {
        return timeoutMs > 0 ? timeoutMs : 3000;
    }

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public boolean isFallbackEnabled() {
        return fallbackEnabled;
    }

    public void setFallbackEnabled(boolean fallbackEnabled) {
        this.fallbackEnabled = fallbackEnabled;
    }

    public int getRateLimitPermitsPerSecond() {
        return rateLimitPermitsPerSecond > 0 ? rateLimitPermitsPerSecond : 50;
    }

    public void setRateLimitPermitsPerSecond(int rateLimitPermitsPerSecond) {
        this.rateLimitPermitsPerSecond = rateLimitPermitsPerSecond;
    }
}
