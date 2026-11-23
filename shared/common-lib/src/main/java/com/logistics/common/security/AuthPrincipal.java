package com.logistics.common.security;

import io.jsonwebtoken.Claims;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class AuthPrincipal {

    private final String userId;
    private final String username;
    private final String email;
    private final Role role;
    private final Set<Permission> permissions;
    private final Set<String> scopes;
    private final Set<String> audiences;

    public AuthPrincipal(String userId, String username, String email, Role role) {
        this(userId, username, email, role, Collections.emptySet(), Collections.emptySet());
    }

    public AuthPrincipal(String userId, String username, String email, Role role, Set<String> scopes, Set<String> audiences) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.role = role;
        this.permissions = role != null ? RolePermissions.getPermissions(role) : Collections.emptySet();
        this.scopes = scopes != null ? Collections.unmodifiableSet(new HashSet<>(scopes)) : Collections.emptySet();
        this.audiences = audiences != null ? Collections.unmodifiableSet(new HashSet<>(audiences)) : Collections.emptySet();
    }

    public static AuthPrincipal fromJwtToken(String token, JwtTokenProvider jwtTokenProvider) {
        if (token == null || !jwtTokenProvider.validateToken(token)) {
            return null;
        }

        Claims claims = jwtTokenProvider.extractClaims(token);
        String userId = claims.get("userId", String.class);
        if (userId == null || userId.isBlank()) {
            userId = claims.getSubject();
        }
        String username = claims.get("username", String.class);
        if (username == null || username.isBlank()) {
            username = userId;
        }
        String email = claims.get("email", String.class);
        String roleStr = claims.get("role", String.class);
        Role role = Role.fromString(roleStr);

        Set<String> scopes = extractScopes(claims);
        Set<String> audiences = claims.getAudience() != null ? claims.getAudience() : Collections.emptySet();

        return new AuthPrincipal(userId, username, email, role, scopes, audiences);
    }

    public static AuthPrincipal fromAuthorizationHeader(String authHeader, JwtTokenProvider jwtTokenProvider) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authHeader.substring(7).trim();
        return fromJwtToken(token, jwtTokenProvider);
    }

    public boolean isService() {
        return this.role == Role.SERVICE;
    }

    public String getServiceId() {
        return isService() ? this.userId : null;
    }

    public Set<String> getScopes() {
        return scopes;
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

    public Set<String> getAudiences() {
        return audiences;
    }

    public boolean hasAudience(String audience) {
        return audience != null && audiences.contains(audience.trim());
    }

    public boolean hasRole(Role requiredRole) {
        return this.role != null && this.role == requiredRole;
    }

    public boolean hasPermission(Permission permission) {
        return permission != null && this.permissions.contains(permission);
    }

    public boolean isAdmin() {
        return hasRole(Role.ADMIN);
    }

    public boolean isOperations() {
        return hasRole(Role.ADMIN) || hasRole(Role.DISPATCHER);
    }

    public boolean isCustomer() {
        return hasRole(Role.CUSTOMER);
    }

    public boolean isDriver() {
        return hasRole(Role.DRIVER);
    }

    public boolean canAccessCustomerResource(String resourceOwnerCustomerId) {
        if (isAdmin() || isOperations()) {
            return true;
        }
        if (isCustomer() && userId != null && resourceOwnerCustomerId != null) {
            return userId.trim().equalsIgnoreCase(resourceOwnerCustomerId.trim());
        }
        return false;
    }

    public boolean canAccessDriverResource(String resourceDriverId, String resolvedDriverProfileId) {
        if (isAdmin() || isOperations()) {
            return true;
        }
        if (isDriver() && resourceDriverId != null) {
            String target = resourceDriverId.trim();
            if (userId != null && userId.trim().equalsIgnoreCase(target)) {
                return true;
            }
            if (resolvedDriverProfileId != null && resolvedDriverProfileId.trim().equalsIgnoreCase(target)) {
                return true;
            }
        }
        return false;
    }

    public String getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public Role getRole() {
        return role;
    }

    public Set<Permission> getPermissions() {
        return permissions;
    }

    @SuppressWarnings("unchecked")
    private static Set<String> extractScopes(Claims claims) {
        Object rawScopes = claims.get(ServiceTokenClaims.CLAIM_SCOPES);
        if (rawScopes instanceof Collection<?>) {
            Set<String> set = new HashSet<>();
            for (Object item : (Collection<?>) rawScopes) {
                if (item != null) {
                    set.add(item.toString());
                }
            }
            return set;
        } else if (rawScopes instanceof String s) {
            return Set.of(s.split("[,\\s]+"));
        }
        return Collections.emptySet();
    }
}
