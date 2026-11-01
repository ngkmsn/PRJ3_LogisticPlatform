package com.logistics.common.backpressure;

/**
 * Configuration parameters for adaptive backpressure regulation.
 */
public class BackpressureConfig {

    private final String name;
    private final int maxConcurrency;
    private final int warningConcurrency;
    private final long highLatencyMs;
    private final long recoveryLatencyMs;
    private final long minPacingDelayMs;
    private final long maxPacingDelayMs;
    private final double ewmaAlpha;

    public BackpressureConfig(String name, int maxConcurrency, int warningConcurrency,
                              long highLatencyMs, long recoveryLatencyMs,
                              long minPacingDelayMs, long maxPacingDelayMs, double ewmaAlpha) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Config name must not be blank");
        }
        if (maxConcurrency <= 0) {
            throw new IllegalArgumentException("maxConcurrency must be positive: " + maxConcurrency);
        }
        this.name = name.trim();
        this.maxConcurrency = maxConcurrency;
        this.warningConcurrency = warningConcurrency > 0 ? warningConcurrency : (int) Math.max(1, maxConcurrency * 0.7);
        this.highLatencyMs = highLatencyMs > 0 ? highLatencyMs : 1000;
        this.recoveryLatencyMs = recoveryLatencyMs > 0 ? recoveryLatencyMs : 300;
        this.minPacingDelayMs = Math.max(0, minPacingDelayMs);
        this.maxPacingDelayMs = Math.max(minPacingDelayMs, maxPacingDelayMs);
        this.ewmaAlpha = (ewmaAlpha > 0.0 && ewmaAlpha <= 1.0) ? ewmaAlpha : 0.2;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static BackpressureConfig defaultConfig(String name) {
        return builder().name(name).build();
    }

    public String getName() {
        return name;
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

    public long getMinPacingDelayMs() {
        return minPacingDelayMs;
    }

    public long getMaxPacingDelayMs() {
        return maxPacingDelayMs;
    }

    public double getEwmaAlpha() {
        return ewmaAlpha;
    }

    public static class Builder {
        private String name = "default";
        private int maxConcurrency = 20;
        private int warningConcurrency = 14;
        private long highLatencyMs = 1000;
        private long recoveryLatencyMs = 300;
        private long minPacingDelayMs = 20;
        private long maxPacingDelayMs = 200;
        private double ewmaAlpha = 0.2;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder maxConcurrency(int maxConcurrency) {
            this.maxConcurrency = maxConcurrency;
            return this;
        }

        public Builder warningConcurrency(int warningConcurrency) {
            this.warningConcurrency = warningConcurrency;
            return this;
        }

        public Builder highLatencyMs(long highLatencyMs) {
            this.highLatencyMs = highLatencyMs;
            return this;
        }

        public Builder recoveryLatencyMs(long recoveryLatencyMs) {
            this.recoveryLatencyMs = recoveryLatencyMs;
            return this;
        }

        public Builder minPacingDelayMs(long minPacingDelayMs) {
            this.minPacingDelayMs = minPacingDelayMs;
            return this;
        }

        public Builder maxPacingDelayMs(long maxPacingDelayMs) {
            this.maxPacingDelayMs = maxPacingDelayMs;
            return this;
        }

        public Builder ewmaAlpha(double ewmaAlpha) {
            this.ewmaAlpha = ewmaAlpha;
            return this;
        }

        public BackpressureConfig build() {
            return new BackpressureConfig(name, maxConcurrency, warningConcurrency,
                    highLatencyMs, recoveryLatencyMs, minPacingDelayMs, maxPacingDelayMs, ewmaAlpha);
        }
    }
}

