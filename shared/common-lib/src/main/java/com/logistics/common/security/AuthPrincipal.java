package com.logistics.common.security;

import java.util.Collections;
import java.util.Set;

public class AuthPrincipal {

    private final String userId;
    private final String username;
    private final String email;
    private final Role role;
    private final Set<Permission> permissions;

    public AuthPrincipal(String userId, String username, String email, Role role) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.role = role;
        this.permissions = role != null ? RolePermissions.getPermissions(role) : Collections.emptySet();
    }

    public static AuthPrincipal fromJwtToken(String token, JwtTokenProvider jwtTokenProvider) {
        if (token == null || !jwtTokenProvider.validateToken(token)) {
            return null;
        }

        String userId = jwtTokenProvider.extractUserId(token);
        String username = jwtTokenProvider.extractUsername(token);
        String email = jwtTokenProvider.extractEmail(token);
        String roleStr = jwtTokenProvider.extractRole(token);
        Role role = Role.fromString(roleStr);

        return new AuthPrincipal(userId, username, email, role);
    }

    public static AuthPrincipal fromAuthorizationHeader(String authHeader, JwtTokenProvider jwtTokenProvider) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authHeader.substring(7).trim();
        return fromJwtToken(token, jwtTokenProvider);
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
}
