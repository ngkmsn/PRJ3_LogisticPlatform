package com.logistics.user.entity;

public enum UserStatus {
    ACTIVE,
    INACTIVE,
    LOCKED;

    public static UserStatus fromString(String status) {
        if (status == null) {
            return null;
        }
        return UserStatus.valueOf(status.trim().toUpperCase());
    }
}
