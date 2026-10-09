package com.logistics.common.security;

public enum Role {
    ADMIN,
    DISPATCHER,
    DRIVER,
    CUSTOMER;

    public static Role fromString(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }
        return Role.valueOf(role.trim().toUpperCase());
    }
}
