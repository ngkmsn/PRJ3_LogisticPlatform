package com.logistics.user.service;

import com.logistics.common.dto.DriverEligibilityResultDto;
import com.logistics.common.model.DriverAvailability;
import com.logistics.common.model.DriverIneligibilityReason;
import com.logistics.user.entity.DriverProfile;
import com.logistics.user.entity.User;
import com.logistics.user.entity.UserRole;
import com.logistics.user.entity.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DriverEligibilityPolicyTest {

    private DriverEligibilityPolicy policy;
    private User validDriverUser;
    private DriverProfile validAvailableProfile;

    @BeforeEach
    void setUp() {
        policy = new DriverEligibilityPolicy();

        validDriverUser = new User(
                "usr-001", "driver1", "driver1@logistics.com", "hash",
                UserRole.DRIVER, UserStatus.ACTIVE
        );

        validAvailableProfile = new DriverProfile(
                "dp-001", "usr-001", "Nguyễn Văn Lái Xe", "0912345678",
                "GPLX-123456", "C", "TRUCK_2T", "29C-123.45", "Hà Nội",
                "ACTIVE", DriverAvailability.AVAILABLE
        );
    }

    @Test
    @DisplayName("Driver with ACTIVE user, DRIVER role, ACTIVE profile, complete info, and AVAILABLE status is ELIGIBLE")
    void evaluate_fullyQualifiedDriver_isEligible() {
        DriverEligibilityResultDto result = policy.evaluate(validDriverUser, validAvailableProfile);

        assertThat(result.isEligible()).isTrue();
        assertThat(result.getIneligibilityReasons()).isEmpty();
        assertThat(result.getDriverId()).isEqualTo("dp-001");
        assertThat(result.getUserId()).isEqualTo("usr-001");
    }

    @Test
    @DisplayName("Driver with UNAVAILABLE status is NOT eligible with DRIVER_UNAVAILABLE reason")
    void evaluate_unavailableDriver_notEligible() {
        validAvailableProfile.setAvailability(DriverAvailability.UNAVAILABLE);

        DriverEligibilityResultDto result = policy.evaluate(validDriverUser, validAvailableProfile);

        assertThat(result.isEligible()).isFalse();
        assertThat(result.getIneligibilityReasons()).containsExactly(DriverIneligibilityReason.DRIVER_UNAVAILABLE);
    }

    @Test
    @DisplayName("Driver with LOCKED user account is NOT eligible with USER_LOCKED reason")
    void evaluate_lockedUser_notEligible() {
        validDriverUser.setStatus(UserStatus.LOCKED);

        DriverEligibilityResultDto result = policy.evaluate(validDriverUser, validAvailableProfile);

        assertThat(result.isEligible()).isFalse();
        assertThat(result.getIneligibilityReasons()).contains(DriverIneligibilityReason.USER_LOCKED);
    }

    @Test
    @DisplayName("Driver with INACTIVE user account is NOT eligible with USER_INACTIVE reason")
    void evaluate_inactiveUser_notEligible() {
        validDriverUser.setStatus(UserStatus.INACTIVE);

        DriverEligibilityResultDto result = policy.evaluate(validDriverUser, validAvailableProfile);

        assertThat(result.isEligible()).isFalse();
        assertThat(result.getIneligibilityReasons()).contains(DriverIneligibilityReason.USER_INACTIVE);
    }

    @Test
    @DisplayName("User with non-DRIVER role (e.g. CUSTOMER) is NOT eligible with NOT_A_DRIVER_ROLE reason")
    void evaluate_nonDriverRole_notEligible() {
        validDriverUser.setRole(UserRole.CUSTOMER);

        DriverEligibilityResultDto result = policy.evaluate(validDriverUser, validAvailableProfile);

        assertThat(result.isEligible()).isFalse();
        assertThat(result.getIneligibilityReasons()).contains(DriverIneligibilityReason.NOT_A_DRIVER_ROLE);
    }

    @Test
    @DisplayName("Missing user account (null) is NOT eligible with USER_NOT_FOUND reason")
    void evaluate_nullUser_notEligible() {
        DriverEligibilityResultDto result = policy.evaluate(null, validAvailableProfile);

        assertThat(result.isEligible()).isFalse();
        assertThat(result.getIneligibilityReasons()).contains(DriverIneligibilityReason.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("Missing driver profile (null) is NOT eligible with DRIVER_PROFILE_NOT_FOUND reason")
    void evaluate_nullProfile_notEligible() {
        DriverEligibilityResultDto result = policy.evaluate(validDriverUser, null);

        assertThat(result.isEligible()).isFalse();
        assertThat(result.getIneligibilityReasons()).contains(DriverIneligibilityReason.DRIVER_PROFILE_NOT_FOUND);
    }

    @Test
    @DisplayName("Driver profile with non-ACTIVE status (e.g. SUSPENDED) is NOT eligible with DRIVER_PROFILE_INACTIVE reason")
    void evaluate_suspendedProfile_notEligible() {
        validAvailableProfile.setStatus("SUSPENDED");

        DriverEligibilityResultDto result = policy.evaluate(validDriverUser, validAvailableProfile);

        assertThat(result.isEligible()).isFalse();
        assertThat(result.getIneligibilityReasons()).contains(DriverIneligibilityReason.DRIVER_PROFILE_INACTIVE);
    }

    @Test
    @DisplayName("Driver profile with missing mandatory field (e.g. licenseNumber) is NOT eligible with DRIVER_INFO_INCOMPLETE reason")
    void evaluate_incompleteProfile_notEligible() {
        validAvailableProfile.setLicenseNumber("   "); // blank license number

        DriverEligibilityResultDto result = policy.evaluate(validDriverUser, validAvailableProfile);

        assertThat(result.isEligible()).isFalse();
        assertThat(result.getIneligibilityReasons()).contains(DriverIneligibilityReason.DRIVER_INFO_INCOMPLETE);
    }

    @Test
    @DisplayName("Multiple violations are accumulated into all respective ineligibility reasons")
    void evaluate_multipleViolations_accumulatesAllReasons() {
        validDriverUser.setStatus(UserStatus.LOCKED);
        validDriverUser.setRole(UserRole.CUSTOMER);
        validAvailableProfile.setStatus("INACTIVE");
        validAvailableProfile.setAvailability(DriverAvailability.UNAVAILABLE);
        validAvailableProfile.setVehiclePlate("");

        DriverEligibilityResultDto result = policy.evaluate(validDriverUser, validAvailableProfile);

        assertThat(result.isEligible()).isFalse();
        assertThat(result.getIneligibilityReasons()).containsExactlyInAnyOrder(
                DriverIneligibilityReason.USER_LOCKED,
                DriverIneligibilityReason.NOT_A_DRIVER_ROLE,
                DriverIneligibilityReason.DRIVER_PROFILE_INACTIVE,
                DriverIneligibilityReason.DRIVER_UNAVAILABLE,
                DriverIneligibilityReason.DRIVER_INFO_INCOMPLETE
        );
        assertThat(result.getReasonDescriptions()).hasSize(5);
    }
}
