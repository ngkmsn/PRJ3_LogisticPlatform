package com.logistics.common.backpressure;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe global registry for BackpressureController instances across the service.
 */
public class BackpressureRegistry {

    private static final BackpressureRegistry INSTANCE = new BackpressureRegistry();

    private final Map<String, BackpressureController> controllers = new ConcurrentHashMap<>();

    private BackpressureRegistry() {}

    public static BackpressureRegistry getInstance() {
        return INSTANCE;
    }

    public BackpressureController getOrCreate(String name, BackpressureConfig config) {
        return controllers.computeIfAbsent(name, k -> new BackpressureController(config));
    }

    public Optional<BackpressureController> get(String name) {
        return Optional.ofNullable(controllers.get(name));
    }

    public void register(String name, BackpressureController controller) {
        controllers.put(name, controller);
    }

    public void clear() {
        controllers.clear();
    }
}

