package com.logistics.common.util;

/**
 * Utility helpers shared across services.
 * <p>
 * Placeholder for commonly-needed string / validation utilities.
 * Business-specific utilities belong in their respective service modules.
 */
public final class CommonUtils {

    private CommonUtils() {
        // utility class – no instantiation
    }

    /**
     * Returns {@code true} if the given string is null or blank.
     */
    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Returns {@code true} if the given string is neither null nor blank.
     */
    public static boolean isNotBlank(String value) {
        return !isBlank(value);
    }
}
