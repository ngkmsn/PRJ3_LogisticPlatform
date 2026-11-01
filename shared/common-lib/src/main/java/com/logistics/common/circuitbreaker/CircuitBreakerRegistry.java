package com.logistics.common.circuitbreaker;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry managing circuit breakers across the platform (PB-054).
 */
public class CircuitBreakerRegistry {

    private static final CircuitBreakerRegistry INSTANCE = new CircuitBreakerRegistry();

    private final Map<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();

    public CircuitBreakerRegistry() {
    }

    public static CircuitBreakerRegistry getInstance() {
        return INSTANCE;
    }

    public CircuitBreaker getOrCreate(String name, CircuitBreakerConfig config) {
        return circuitBreakers.computeIfAbsent(name, k -> new CircuitBreaker(k, config));
    }

    public CircuitBreaker getOrCreate(String name) {
        return getOrCreate(name, CircuitBreakerConfig.defaultConfig());
    }

    public Optional<CircuitBreaker> get(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(circuitBreakers.get(name));
    }

    public Collection<CircuitBreaker> getAll() {
        return circuitBreakers.values();
    }

    public List<CircuitBreakerMetricsDto> getAllMetrics() {
        return circuitBreakers.values().stream()
                .map(CircuitBreaker::getMetrics)
                .toList();
    }

    public boolean reset(String name) {
        CircuitBreaker cb = circuitBreakers.get(name);
        if (cb != null) {
            cb.reset();
            return true;
        }
        return false;
    }

    public void resetAll() {
        circuitBreakers.values().forEach(CircuitBreaker::reset);
    }

    public void register(String name, CircuitBreaker circuitBreaker) {
        if (name != null && circuitBreaker != null) {
            circuitBreakers.put(name, circuitBreaker);
        }
    }

    public void clear() {
        circuitBreakers.clear();
    }
}
