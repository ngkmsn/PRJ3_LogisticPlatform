package com.logistics.common.ratelimit;

import java.time.Instant;
import java.util.Map;

/**
 * Data Transfer Object for Rate Limiting telemetry and operational monitoring.
 */
public class RateLimitMetricsDto {

    private long totalRequests;
    private long allowedRequests;
    private long rejectedRequests;
    private long storageErrors;
    private String storageType;
    private boolean storageHealthy;
    private Map<String, PolicyCounterDto> policies;
    private Instant timestamp;

    public RateLimitMetricsDto() {
        this.timestamp = Instant.now();
    }

    public RateLimitMetricsDto(long totalRequests, long allowedRequests, long rejectedRequests,
                               long storageErrors, String storageType, boolean storageHealthy,
                               Map<String, PolicyCounterDto> policies) {
        this.totalRequests = totalRequests;
        this.allowedRequests = allowedRequests;
        this.rejectedRequests = rejectedRequests;
        this.storageErrors = storageErrors;
        this.storageType = storageType;
        this.storageHealthy = storageHealthy;
        this.policies = policies;
        this.timestamp = Instant.now();
    }

    public long getTotalRequests() {
        return totalRequests;
    }

    public void setTotalRequests(long totalRequests) {
        this.totalRequests = totalRequests;
    }

    public long getAllowedRequests() {
        return allowedRequests;
    }

    public void setAllowedRequests(long allowedRequests) {
        this.allowedRequests = allowedRequests;
    }

    public long getRejectedRequests() {
        return rejectedRequests;
    }

    public void setRejectedRequests(long rejectedRequests) {
        this.rejectedRequests = rejectedRequests;
    }

    public long getStorageErrors() {
        return storageErrors;
    }

    public void setStorageErrors(long storageErrors) {
        this.storageErrors = storageErrors;
    }

    public String getStorageType() {
        return storageType;
    }

    public void setStorageType(String storageType) {
        this.storageType = storageType;
    }

    public boolean isStorageHealthy() {
        return storageHealthy;
    }

    public void setStorageHealthy(boolean storageHealthy) {
        this.storageHealthy = storageHealthy;
    }

    public Map<String, PolicyCounterDto> getPolicies() {
        return policies;
    }

    public void setPolicies(Map<String, PolicyCounterDto> policies) {
        this.policies = policies;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public static class PolicyCounterDto {
        private long allowed;
        private long rejected;

        public PolicyCounterDto() {}

        public PolicyCounterDto(long allowed, long rejected) {
            this.allowed = allowed;
            this.rejected = rejected;
        }

        public long getAllowed() {
            return allowed;
        }

        public void setAllowed(long allowed) {
            this.allowed = allowed;
        }

        public long getRejected() {
            return rejected;
        }

        public void setRejected(long rejected) {
            this.rejected = rejected;
        }
    }
}

