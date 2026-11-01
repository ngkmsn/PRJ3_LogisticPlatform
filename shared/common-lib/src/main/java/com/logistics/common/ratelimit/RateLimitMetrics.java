package com.logistics.common.ratelimit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Thread-safe telemetry collector for Rate Limiting invocations and rejections.
 */
public class RateLimitMetrics {

    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong allowedRequests = new AtomicLong(0);
    private final AtomicLong rejectedRequests = new AtomicLong(0);
    private final AtomicLong storageErrors = new AtomicLong(0);

    private final Map<String, PolicyStats> policyStats = new ConcurrentHashMap<>();

    private static class PolicyStats {
        final AtomicLong allowed = new AtomicLong(0);
        final AtomicLong rejected = new AtomicLong(0);
    }

    public void recordDecision(RateLimitResult result) {
        totalRequests.incrementAndGet();
        PolicyStats stats = policyStats.computeIfAbsent(result.getPolicyName(), p -> new PolicyStats());

        if (result.isAllowed()) {
            allowedRequests.incrementAndGet();
            stats.allowed.incrementAndGet();
        } else {
            rejectedRequests.incrementAndGet();
            stats.rejected.incrementAndGet();
        }
    }

    public void recordStorageError() {
        storageErrors.incrementAndGet();
    }

    public void reset() {
        totalRequests.set(0);
        allowedRequests.set(0);
        rejectedRequests.set(0);
        storageErrors.set(0);
        policyStats.clear();
    }

    public RateLimitMetricsDto snapshot(RateLimiterStorage storage) {
        Map<String, RateLimitMetricsDto.PolicyCounterDto> map = policyStats.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> new RateLimitMetricsDto.PolicyCounterDto(e.getValue().allowed.get(), e.getValue().rejected.get())
                ));

        String storageType = (storage != null) ? storage.getStorageType() : "UNKNOWN";
        boolean healthy = (storage != null) && storage.isHealthy();

        return new RateLimitMetricsDto(
                totalRequests.get(),
                allowedRequests.get(),
                rejectedRequests.get(),
                storageErrors.get(),
                storageType,
                healthy,
                map
        );
    }

    public long getTotalRequests() {
        return totalRequests.get();
    }

    public long getAllowedRequests() {
        return allowedRequests.get();
    }

    public long getRejectedRequests() {
        return rejectedRequests.get();
    }

    public long getStorageErrors() {
        return storageErrors.get();
    }
}

