package com.logistics.user.service;

import com.logistics.common.dto.DriverEligibilityResultDto;
import com.logistics.common.model.DriverAvailability;
import com.logistics.common.model.DriverIneligibilityReason;
import com.logistics.user.entity.DriverProfile;
import com.logistics.user.entity.User;
import com.logistics.user.entity.UserRole;
import com.logistics.user.entity.UserStatus;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure domain policy component encapsulating rules to determine whether a driver is eligible for order dispatching.
 */
@ApplicationScoped
public class DriverEligibilityPolicy {

    /**
     * Evaluates driver eligibility based on user account and driver profile state.
     *
     * @param user          the user account entity (can be null if missing)
     * @param driverProfile the driver profile entity (can be null if missing)
     * @return DriverEligibilityResultDto with eligibility status and structured reasons
     */
    public DriverEligibilityResultDto evaluate(User user, DriverProfile driverProfile) {
        List<DriverIneligibilityReason> reasons = new ArrayList<>();

        // 1. User account checks
        if (user == null) {
            reasons.add(DriverIneligibilityReason.USER_NOT_FOUND);
        } else {
            if (user.getStatus() == UserStatus.LOCKED) {
                reasons.add(DriverIneligibilityReason.USER_LOCKED);
            } else if (user.getStatus() != UserStatus.ACTIVE) {
                reasons.add(DriverIneligibilityReason.USER_INACTIVE);
            }

            if (user.getRole() != UserRole.DRIVER) {
                reasons.add(DriverIneligibilityReason.NOT_A_DRIVER_ROLE);
            }
        }

        // 2. Driver profile checks
        if (driverProfile == null) {
            reasons.add(DriverIneligibilityReason.DRIVER_PROFILE_NOT_FOUND);
        } else {
            if (driverProfile.getStatus() == null || !"ACTIVE".equalsIgnoreCase(driverProfile.getStatus().trim())) {
                reasons.add(DriverIneligibilityReason.DRIVER_PROFILE_INACTIVE);
            }

            if (driverProfile.getAvailability() != DriverAvailability.AVAILABLE) {
                reasons.add(DriverIneligibilityReason.DRIVER_UNAVAILABLE);
            }

            if (isMissing(driverProfile.getFullName())
                    || isMissing(driverProfile.getPhoneNumber())
                    || isMissing(driverProfile.getLicenseNumber())
                    || isMissing(driverProfile.getLicenseClass())
                    || isMissing(driverProfile.getVehicleType())
                    || isMissing(driverProfile.getVehiclePlate())) {
                reasons.add(DriverIneligibilityReason.DRIVER_INFO_INCOMPLETE);
            }
        }

        String driverId = driverProfile != null ? driverProfile.getId() : null;
        String userId = user != null ? user.getId() : (driverProfile != null ? driverProfile.getUserId() : null);

        if (reasons.isEmpty()) {
            return DriverEligibilityResultDto.eligible(driverId, userId);
        } else {
            return DriverEligibilityResultDto.notEligible(driverId, userId, reasons);
        }
    }

    private boolean isMissing(String value) {
        return value == null || value.trim().isEmpty();
    }
}
