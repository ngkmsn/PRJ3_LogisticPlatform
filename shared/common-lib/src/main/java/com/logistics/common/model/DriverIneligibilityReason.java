package com.logistics.common.model;

/**
 * Structured reason codes identifying why a driver is evaluated as not eligible for order assignment.
 */
public enum DriverIneligibilityReason {

    /**
     * The associated user account does not exist in the system.
     */
    USER_NOT_FOUND("User account does not exist"),

    /**
     * The user account is inactive or disabled.
     */
    USER_INACTIVE("User account is inactive"),

    /**
     * The user account is locked by administrators.
     */
    USER_LOCKED("User account is locked"),

    /**
     * The user account does not have the DRIVER role.
     */
    NOT_A_DRIVER_ROLE("User does not have DRIVER role"),

    /**
     * No driver profile exists for this driver identity.
     */
    DRIVER_PROFILE_NOT_FOUND("Driver profile does not exist"),

    /**
     * The driver profile operational status is not ACTIVE (e.g., SUSPENDED or INACTIVE).
     */
    DRIVER_PROFILE_INACTIVE("Driver profile status is not ACTIVE"),

    /**
     * The driver's availability state is currently UNAVAILABLE.
     */
    DRIVER_UNAVAILABLE("Driver availability is currently UNAVAILABLE"),

    /**
     * Mandatory driver profile information (such as license, plate, or vehicle type) is missing or blank.
     */
    DRIVER_INFO_INCOMPLETE("Driver mandatory profile details are incomplete"),

    /**
     * The system could not complete verification due to technical, connectivity, or database failures.
     */
    SYSTEM_ERROR("System or communication error while verifying driver eligibility");

    private final String defaultDescription;

    DriverIneligibilityReason(String defaultDescription) {
        this.defaultDescription = defaultDescription;
    }

    public String getDefaultDescription() {
        return defaultDescription;
    }
}
