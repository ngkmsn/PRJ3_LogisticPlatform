package com.logistics.common.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ServiceTokenManager {

    private static final Logger log = LoggerFactory.getLogger(ServiceTokenManager.class);

    private final ServiceTokenProvider tokenProvider;
    private final long renewWindowSeconds;
    private final ConcurrentMap<String, CachedToken> tokenCache = new ConcurrentHashMap<>();

    public ServiceTokenManager(ServiceTokenProvider tokenProvider, long renewWindowSeconds) {
        this.tokenProvider = Objects.requireNonNull(tokenProvider, "tokenProvider must not be null");
        this.renewWindowSeconds = renewWindowSeconds > 0 ? renewWindowSeconds : 30L;
    }

    public ServiceTokenManager(String serviceId, String secret, String issuer, long defaultTtlSeconds, long renewWindowSeconds) {
        this(new ServiceTokenProvider(serviceId, secret, issuer, defaultTtlSeconds), renewWindowSeconds);
    }

    public ServiceTokenManager(String serviceId, String secret, String issuer) {
        this(new ServiceTokenProvider(serviceId, secret, issuer, 300L), 30L);
    }

    public String getServiceToken(String targetAudience, Set<String> scopes) {
        if (targetAudience == null || targetAudience.isBlank()) {
            throw new IllegalArgumentException("targetAudience must not be null or blank");
        }
        String audience = targetAudience.trim();
        Set<String> scopeSet = scopes != null ? Collections.unmodifiableSet(new HashSet<>(scopes)) : Collections.emptySet();
        String cacheKey = buildCacheKey(audience, scopeSet);

        Instant now = Instant.now();
        CachedToken cached = tokenCache.get(cacheKey);

        if (cached != null && !cached.isExpiring(now, renewWindowSeconds)) {
            return cached.token();
        }

        // Generate a new token and update cache
        log.debug("PB-057: Generating fresh service token for target audience '{}' (scopes: {})", audience, scopeSet);
        long ttlSeconds = tokenProvider.getDefaultTtlSeconds();
        String token = tokenProvider.generateToken(audience, scopeSet, ttlSeconds);
        Instant expiresAt = now.plusSeconds(ttlSeconds);

        CachedToken freshToken = new CachedToken(token, audience, scopeSet, expiresAt);
        tokenCache.put(cacheKey, freshToken);

        return token;
    }

    public String getServiceToken(String targetAudience) {
        return getServiceToken(targetAudience, Set.of("service:call"));
    }

    public void invalidate(String targetAudience) {
        if (targetAudience != null) {
            tokenCache.keySet().removeIf(k -> k.startsWith(targetAudience.trim() + ":"));
        }
    }

    public void invalidateAll() {
        tokenCache.clear();
    }

    public int getCacheSize() {
        return tokenCache.size();
    }

    public ServiceTokenProvider getTokenProvider() {
        return tokenProvider;
    }

    private String buildCacheKey(String audience, Set<String> scopes) {
        return audience + ":" + String.join(",", scopes.stream().sorted().toList());
    }

    private record CachedToken(String token, String audience, Set<String> scopes, Instant expiresAt) {
        boolean isExpiring(Instant now, long windowSeconds) {
            return now.plusSeconds(windowSeconds).isAfter(expiresAt);
        }
    }
}

