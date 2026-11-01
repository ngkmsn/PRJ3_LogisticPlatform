package com.logistics.common.ratelimit;

import java.util.Objects;

/**
 * Immutable configuration defining rate limiting rules for a specific route, client, or event type.
 */
public class RateLimitPolicy {

    private final String name;
    private final long limit;
    private final long windowSeconds;
    private final RateLimitKeyType keyType;

    public RateLimitPolicy(String name, long limit, long windowSeconds, RateLimitKeyType keyType) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Policy name must not be null or blank");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("Rate limit must be greater than zero: " + limit);
        }
        if (windowSeconds <= 0) {
            throw new IllegalArgumentException("Window seconds must be greater than zero: " + windowSeconds);
        }
        this.name = name.trim();
        this.limit = limit;
        this.windowSeconds = windowSeconds;
        this.keyType = (keyType != null) ? keyType : RateLimitKeyType.IP_AND_USER;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static RateLimitPolicy authPolicy(long limit, long windowSeconds) {
        return new RateLimitPolicy("auth", limit, windowSeconds, RateLimitKeyType.IP_ONLY);
    }

    public static RateLimitPolicy ordersPolicy(long limit, long windowSeconds) {
        return new RateLimitPolicy("orders", limit, windowSeconds, RateLimitKeyType.IP_AND_USER);
    }

    public static RateLimitPolicy trackingPolicy(long limit, long windowSeconds) {
        return new RateLimitPolicy("tracking", limit, windowSeconds, RateLimitKeyType.IP_AND_USER);
    }

    public static RateLimitPolicy defaultPolicy(long limit, long windowSeconds) {
        return new RateLimitPolicy("default", limit, windowSeconds, RateLimitKeyType.IP_AND_USER);
    }

    public String getName() {
        return name;
    }

    public long getLimit() {
        return limit;
    }

    public long getWindowSeconds() {
        return windowSeconds;
    }

    public RateLimitKeyType getKeyType() {
        return keyType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RateLimitPolicy that)) return false;
        return limit == that.limit &&
                windowSeconds == that.windowSeconds &&
                Objects.equals(name, that.name) &&
                keyType == that.keyType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, limit, windowSeconds, keyType);
    }

    @Override
    public String toString() {
        return "RateLimitPolicy{" +
                "name='" + name + '\'' +
                ", limit=" + limit +
                ", windowSeconds=" + windowSeconds +
                ", keyType=" + keyType +
                '}';
    }

    public static class Builder {
        private String name = "default";
        private long limit = 60;
        private long windowSeconds = 60;
        private RateLimitKeyType keyType = RateLimitKeyType.IP_AND_USER;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder limit(long limit) {
            this.limit = limit;
            return this;
        }

        public Builder windowSeconds(long windowSeconds) {
            this.windowSeconds = windowSeconds;
            return this;
        }

        public Builder keyType(RateLimitKeyType keyType) {
            this.keyType = keyType;
            return this;
        }

        public RateLimitPolicy build() {
            return new RateLimitPolicy(name, limit, windowSeconds, keyType);
        }
    }
}

