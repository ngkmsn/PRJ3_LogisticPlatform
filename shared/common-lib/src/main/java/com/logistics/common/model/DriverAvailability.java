package com.logistics.common.model;

public enum DriverAvailability {
    AVAILABLE,
    UNAVAILABLE;

    public static DriverAvailability fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return DriverAvailability.valueOf(value.trim().toUpperCase());
    }
}
