package com.logistics.common.security;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ServicePrincipal {

    private final String serviceId;
    private final Set<String> audiences;
    private final Set<String> scopes;
    private final Instant issuedAt;
    private final Instant expiresAt;

    public ServicePrincipal(String serviceId, Set<String> audiences, Set<String> scopes, Instant issuedAt, Instant expiresAt) {
        this.serviceId = serviceId;
        this.audiences = audiences != null ? Collections.unmodifiableSet(new HashSet<>(audiences)) : Collections.emptySet();
        this.scopes = scopes != null ? Collections.unmodifiableSet(new HashSet<>(scopes)) : Collections.emptySet();
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    public String getServiceId() {
        return serviceId;
    }

    public Set<String> getAudiences() {
        return audiences;
    }

    public Set<String> getScopes() {
        return scopes;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean hasScope(String requiredScope) {
        if (requiredScope == null || requiredScope.isBlank()) {
            return true;
        }
        if (scopes.contains("*") || scopes.contains("service:admin") || scopes.contains("service:all")) {
            return true;
        }
        return scopes.contains(requiredScope.trim());
    }

    public boolean hasAudience(String targetAudience) {
        if (targetAudience == null || targetAudience.isBlank()) {
            return false;
        }
        return audiences.contains(targetAudience.trim());
    }
}

