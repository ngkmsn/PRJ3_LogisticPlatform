package com.logistics.common.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Distributed Rate Limiter Storage using Redis 7.
 * Executes an atomic Lua script across replicas to guarantee consistent rate limiting
 * regardless of which API Gateway or service instance processes the incoming request.
 */
public class RedisRateLimiterStorage implements RateLimiterStorage {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiterStorage.class);

    private static final String ATOMIC_RATE_LIMIT_LUA =
            "local key = KEYS[1]\n" +
            "local limit = tonumber(ARGV[1])\n" +
            "local window = tonumber(ARGV[2])\n" +
            "local current = redis.call('INCR', key)\n" +
            "if current == 1 then\n" +
            "    redis.call('EXPIRE', key, window)\n" +
            "end\n" +
            "local ttl = redis.call('TTL', key)\n" +
            "if ttl < 0 then\n" +
            "    redis.call('EXPIRE', key, window)\n" +
            "    ttl = window\n" +
            "end\n" +
            "return {current, ttl}";

    private final RedisClient redisClient;
    private final FailPolicy failPolicy;
    private final RateLimiterStorage fallbackStorage;
    private final AtomicLong errorCount = new AtomicLong(0);

    public RedisRateLimiterStorage(RedisClient redisClient, FailPolicy failPolicy, RateLimiterStorage fallbackStorage) {
        this.redisClient = redisClient;
        this.failPolicy = (failPolicy != null) ? failPolicy : FailPolicy.FAIL_OPEN;
        this.fallbackStorage = fallbackStorage;
    }

    public RedisRateLimiterStorage(String host, int port, int timeoutMs, FailPolicy failPolicy, RateLimiterStorage fallbackStorage) {
        this(new RedisClient(host, port, timeoutMs), failPolicy, fallbackStorage);
    }

    @Override
    public RateLimitResult checkAndConsume(String key, RateLimitPolicy policy) {
        if (key == null || key.isBlank() || policy == null) {
            throw new IllegalArgumentException("Key and policy must not be null or blank");
        }

        String redisKey = "ratelimit:" + policy.getName() + ":" + key;
        long limit = policy.getLimit();
        long windowSeconds = policy.getWindowSeconds();

        try {
            Object evalResult = redisClient.eval(
                    ATOMIC_RATE_LIMIT_LUA,
                    1,
                    redisKey,
                    String.valueOf(limit),
                    String.valueOf(windowSeconds)
            );

            if (evalResult instanceof List<?> list && list.size() >= 2) {
                long currentCount = ((Number) list.get(0)).longValue();
                long ttl = ((Number) list.get(1)).longValue();
                long retryAfter = Math.max(1, ttl);
                long resetEpoch = Instant.now().getEpochSecond() + retryAfter;

                if (currentCount <= limit) {
                    long remaining = Math.max(0, limit - currentCount);
                    return new RateLimitResult(true, limit, remaining, resetEpoch, 0, policy.getName(), key);
                } else {
                    return RateLimitResult.rejected(limit, 0, resetEpoch, retryAfter, policy.getName(), key);
                }
            } else {
                throw new IllegalStateException("Unexpected Redis response shape from rate limit script: " + evalResult);
            }
        } catch (Exception e) {
            errorCount.incrementAndGet();
            log.warn("PB-055 [REDIS_STORAGE_ERROR] Failed to query Redis for key '{}' (failPolicy={}): {}",
                    redisKey, failPolicy, e.getMessage());

            if (fallbackStorage != null) {
                log.info("PB-055 [STORAGE_FALLBACK] Falling back to in-memory rate limiter for key '{}'", key);
                return fallbackStorage.checkAndConsume(key, policy);
            }

            if (failPolicy == FailPolicy.FAIL_OPEN) {
                log.info("PB-055 [FAIL_OPEN] Permitting request for key '{}' due to FAIL_OPEN policy", key);
                return RateLimitResult.allowed(limit, limit, windowSeconds, policy.getName(), key);
            } else {
                long resetEpoch = Instant.now().getEpochSecond() + 5;
                return RateLimitResult.rejected(limit, 0, resetEpoch, 5, policy.getName(), key);
            }
        }
    }

    @Override
    public void reset(String key) {
        if (key != null) {
            try {
                redisClient.del("ratelimit:*:" + key);
                redisClient.del(key);
            } catch (Exception e) {
                log.warn("Failed to delete Redis key: {}", e.getMessage());
            }
            if (fallbackStorage != null) {
                fallbackStorage.reset(key);
            }
        }
    }

    @Override
    public void resetAll() {
        try {
            redisClient.flushDb();
        } catch (Exception e) {
            log.warn("Failed to flush Redis DB: {}", e.getMessage());
        }
        if (fallbackStorage != null) {
            fallbackStorage.resetAll();
        }
    }

    @Override
    public boolean isHealthy() {
        return redisClient.ping();
    }

    @Override
    public String getStorageType() {
        return "REDIS";
    }

    public FailPolicy getFailPolicy() {
        return failPolicy;
    }

    public long getErrorCount() {
        return errorCount.get();
    }

    public RateLimiterStorage getFallbackStorage() {
        return fallbackStorage;
    }
}

