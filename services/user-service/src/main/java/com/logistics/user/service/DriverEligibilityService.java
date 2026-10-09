package com.logistics.user.service;

import com.logistics.common.dto.DriverEligibilityResultDto;
import com.logistics.common.model.DriverIneligibilityReason;
import com.logistics.user.entity.DriverProfile;
import com.logistics.user.entity.User;
import com.logistics.user.entity.UserRole;
import com.logistics.user.exception.AccessDeniedException;
import com.logistics.user.repository.DriverProfileRepository;
import com.logistics.user.repository.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Service orchestrating driver eligibility checks with data retrieval and fail-safe handling.
 */
@ApplicationScoped
public class DriverEligibilityService {

    private static final Logger log = LoggerFactory.getLogger(DriverEligibilityService.class);

    private final DriverEligibilityPolicy eligibilityPolicy;
    private final DriverProfileRepository driverProfileRepository;
    private final UserRepository userRepository;
    private final DriverService driverService;

    @Inject
    public DriverEligibilityService(DriverEligibilityPolicy eligibilityPolicy,
                                    DriverProfileRepository driverProfileRepository,
                                    UserRepository userRepository,
                                    DriverService driverService) {
        this.eligibilityPolicy = eligibilityPolicy;
        this.driverProfileRepository = driverProfileRepository;
        this.userRepository = userRepository;
        this.driverService = driverService;
    }

    /**
     * Evaluates driver eligibility by driver profile ID or user ID.
     * Guaranteed to be fail-safe: any unexpected technical failure returns an ineligibility result with SYSTEM_ERROR.
     *
     * @param driverIdentifier the driver profile ID or user ID
     * @return DriverEligibilityResultDto
     */
    public DriverEligibilityResultDto evaluateDriverEligibility(String driverIdentifier) {
        if (driverIdentifier == null || driverIdentifier.isBlank()) {
            return DriverEligibilityResultDto.notEligible(null, null, List.of(DriverIneligibilityReason.USER_NOT_FOUND));
        }

        try {
            String trimmedId = driverIdentifier.trim();

            // 1. Try finding DriverProfile by ID or by userId
            Optional<DriverProfile> profileOpt = driverProfileRepository.findByIdOptional(trimmedId)
                    .or(() -> driverProfileRepository.findByUserId(trimmedId));

            // 2. Try finding User by userId from profile or by identifier directly
            Optional<User> userOpt;
            if (profileOpt.isPresent()) {
                String userId = profileOpt.get().getUserId();
                userOpt = userRepository.findByIdOptional(userId);
            } else {
                userOpt = userRepository.findByIdOptional(trimmedId);
                // If user found, check if a profile exists by user ID (in case previous check missed it)
                if (userOpt.isPresent()) {
                    profileOpt = driverProfileRepository.findByUserId(userOpt.get().getId());
                }
            }

            return eligibilityPolicy.evaluate(userOpt.orElse(null), profileOpt.orElse(null));

        } catch (Exception ex) {
            log.error("Technical or database failure during driver eligibility evaluation for identifier '{}'",
                    driverIdentifier, ex);
            return DriverEligibilityResultDto.systemError(null, driverIdentifier, "Technical failure: " + ex.getMessage());
        }
    }

    /**
     * Evaluates the caller driver's own eligibility using the authentication token.
     *
     * @param authHeader the Bearer JWT authorization header
     * @return DriverEligibilityResultDto
     */
    public DriverEligibilityResultDto getMyEligibility(String authHeader) {
        User caller = driverService.resolveAuthenticatedUser(authHeader);

        if (caller.getRole() != UserRole.DRIVER) {
            log.warn("Access denied: caller role '{}' cannot view personal driver eligibility", caller.getRole());
            throw new AccessDeniedException("Access denied: Only drivers can check personal eligibility");
        }

        return evaluateDriverEligibility(caller.getId());
    }

    /**
     * Evaluates eligibility of a driver identified by ID, subject to role-based access control.
     *
     * @param authHeader the Bearer JWT authorization header
     * @param identifier the driver profile ID or user ID
     * @return DriverEligibilityResultDto
     */
    public DriverEligibilityResultDto getDriverEligibility(String authHeader, String identifier) {
        User caller = driverService.resolveAuthenticatedUser(authHeader);

        if (caller.getRole() == UserRole.CUSTOMER) {
            log.warn("Access denied: CUSTOMER role cannot view driver eligibility");
            throw new AccessDeniedException("Access denied: Insufficient permissions");
        }

        if (caller.getRole() == UserRole.DRIVER) {
            // Driver can only check their own eligibility
            boolean isSelf = caller.getId().equals(identifier);
            if (!isSelf) {
                Optional<DriverProfile> callerProfile = driverProfileRepository.findByUserId(caller.getId());
                if (callerProfile.isPresent() && callerProfile.get().getId().equals(identifier)) {
                    isSelf = true;
                }
            }

            if (!isSelf) {
                log.warn("Access denied: Driver '{}' attempted to view eligibility of identifier '{}'",
                        caller.getId(), identifier);
                throw new AccessDeniedException("Access denied: You can only view your own driver eligibility");
            }
        }

        return evaluateDriverEligibility(identifier);
    }
}
