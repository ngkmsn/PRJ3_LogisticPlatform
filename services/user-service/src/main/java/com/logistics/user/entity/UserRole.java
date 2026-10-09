package com.logistics.user.entity;

public enum UserRole {
    ADMIN,
    DISPATCHER,
    DRIVER,
    CUSTOMER;

    public static UserRole fromString(String role) {
        if (role == null) {
            return null;
        }
        return UserRole.valueOf(role.trim().toUpperCase());
    }
}
