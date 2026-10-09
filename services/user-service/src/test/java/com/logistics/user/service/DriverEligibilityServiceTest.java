package com.logistics.user.service;

import com.logistics.common.dto.DriverEligibilityResultDto;
import com.logistics.common.model.DriverAvailability;
import com.logistics.common.model.DriverIneligibilityReason;
import com.logistics.user.entity.DriverProfile;
import com.logistics.user.entity.User;
import com.logistics.user.entity.UserRole;
import com.logistics.user.entity.UserStatus;
import com.logistics.user.exception.AccessDeniedException;
import com.logistics.user.repository.DriverProfileRepository;
import com.logistics.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DriverEligibilityServiceTest {

    @Mock
    private DriverProfileRepository driverProfileRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DriverService driverService;

    private DriverEligibilityPolicy eligibilityPolicy;
    private DriverEligibilityService eligibilityService;

    private User driverUser;
    private User otherDriverUser;
    private User adminUser;
    private User customerUser;

    private DriverProfile driverProfile;

    @BeforeEach
    void setUp() {
        eligibilityPolicy = new DriverEligibilityPolicy();
        eligibilityService = new DriverEligibilityService(
                eligibilityPolicy, driverProfileRepository, userRepository, driverService
        );

        driverUser = new User("usr-driver-1", "driver1", "driver1@logistics.com", "hash", UserRole.DRIVER, UserStatus.ACTIVE);
        otherDriverUser = new User("usr-driver-2", "driver2", "driver2@logistics.com", "hash", UserRole.DRIVER, UserStatus.ACTIVE);
        adminUser = new User("usr-admin", "admin", "admin@logistics.com", "hash", UserRole.ADMIN, UserStatus.ACTIVE);
        customerUser = new User("usr-cust", "customer", "customer@logistics.com", "hash", UserRole.CUSTOMER, UserStatus.ACTIVE);

        driverProfile = new DriverProfile(
                "dp-001", "usr-driver-1", "Nguyễn Văn Lái Xe", "0912345678",
                "GPLX-112233", "C", "TRUCK_2T", "29C-112.23", "Hà Nội",
                "ACTIVE", DriverAvailability.AVAILABLE
        );
    }

    @Test
    @DisplayName("Evaluate driver eligibility by driver profile ID successfully")
    void evaluateDriverEligibility_byProfileId_success() {
        when(driverProfileRepository.findByIdOptional("dp-001")).thenReturn(Optional.of(driverProfile));
        when(userRepository.findByIdOptional("usr-driver-1")).thenReturn(Optional.of(driverUser));

        DriverEligibilityResultDto result = eligibilityService.evaluateDriverEligibility("dp-001");

        assertThat(result.isEligible()).isTrue();
        assertThat(result.getDriverId()).isEqualTo("dp-001");
        assertThat(result.getUserId()).isEqualTo("usr-driver-1");
    }

    @Test
    @DisplayName("Evaluate driver eligibility by user ID successfully")
    void evaluateDriverEligibility_byUserId_success() {
        when(driverProfileRepository.findByIdOptional("usr-driver-1")).thenReturn(Optional.empty());
        when(driverProfileRepository.findByUserId("usr-driver-1")).thenReturn(Optional.of(driverProfile));
        when(userRepository.findByIdOptional("usr-driver-1")).thenReturn(Optional.of(driverUser));

        DriverEligibilityResultDto result = eligibilityService.evaluateDriverEligibility("usr-driver-1");

        assertThat(result.isEligible()).isTrue();
        assertThat(result.getDriverId()).isEqualTo("dp-001");
        assertThat(result.getUserId()).isEqualTo("usr-driver-1");
    }

    @Test
    @DisplayName("Fail-safe: technical/database exception returns NOT eligible with SYSTEM_ERROR")
    void evaluateDriverEligibility_technicalException_failSafeSystemError() {
        when(driverProfileRepository.findByIdOptional("dp-001"))
                .thenThrow(new RuntimeException("Database connection timeout"));

        DriverEligibilityResultDto result = eligibilityService.evaluateDriverEligibility("dp-001");

        assertThat(result.isEligible()).isFalse();
        assertThat(result.getIneligibilityReasons()).contains(DriverIneligibilityReason.SYSTEM_ERROR);
        assertThat(result.getReasonDescriptions()).anyMatch(desc -> desc.contains("Database connection timeout"));
    }

    @Test
    @DisplayName("Driver can check their own eligibility via getMyEligibility")
    void getMyEligibility_driverSuccess() {
        String token = "Bearer mock-driver-token";
        when(driverService.resolveAuthenticatedUser(token)).thenReturn(driverUser);
        when(driverProfileRepository.findByIdOptional("usr-driver-1")).thenReturn(Optional.empty());
        when(driverProfileRepository.findByUserId("usr-driver-1")).thenReturn(Optional.of(driverProfile));
        when(userRepository.findByIdOptional("usr-driver-1")).thenReturn(Optional.of(driverUser));

        DriverEligibilityResultDto result = eligibilityService.getMyEligibility(token);

        assertThat(result.isEligible()).isTrue();
        assertThat(result.getUserId()).isEqualTo("usr-driver-1");
    }

    @Test
    @DisplayName("Non-driver caller calling getMyEligibility is rejected with 403 Forbidden")
    void getMyEligibility_customerForbidden() {
        String token = "Bearer mock-customer-token";
        when(driverService.resolveAuthenticatedUser(token)).thenReturn(customerUser);

        assertThatThrownBy(() -> eligibilityService.getMyEligibility(token))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only drivers can check personal eligibility");
    }

    @Test
    @DisplayName("Admin can check eligibility of any driver via getDriverEligibility")
    void getDriverEligibility_adminSuccess() {
        String token = "Bearer mock-admin-token";
        when(driverService.resolveAuthenticatedUser(token)).thenReturn(adminUser);
        when(driverProfileRepository.findByIdOptional("dp-001")).thenReturn(Optional.of(driverProfile));
        when(userRepository.findByIdOptional("usr-driver-1")).thenReturn(Optional.of(driverUser));

        DriverEligibilityResultDto result = eligibilityService.getDriverEligibility(token, "dp-001");

        assertThat(result.isEligible()).isTrue();
    }

    @Test
    @DisplayName("Driver can check their own eligibility via getDriverEligibility by ID")
    void getDriverEligibility_selfDriverSuccess() {
        String token = "Bearer mock-driver-token";
        when(driverService.resolveAuthenticatedUser(token)).thenReturn(driverUser);
        when(driverProfileRepository.findByIdOptional("dp-001")).thenReturn(Optional.of(driverProfile));
        when(driverProfileRepository.findByUserId("usr-driver-1")).thenReturn(Optional.of(driverProfile));
        when(userRepository.findByIdOptional("usr-driver-1")).thenReturn(Optional.of(driverUser));

        DriverEligibilityResultDto result = eligibilityService.getDriverEligibility(token, "dp-001");

        assertThat(result.isEligible()).isTrue();
    }

    @Test
    @DisplayName("Driver is denied access when attempting to check another driver's eligibility")
    void getDriverEligibility_otherDriverForbidden() {
        String token = "Bearer mock-driver-token-2";
        when(driverService.resolveAuthenticatedUser(token)).thenReturn(otherDriverUser);
        when(driverProfileRepository.findByUserId("usr-driver-2")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eligibilityService.getDriverEligibility(token, "dp-001"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You can only view your own driver eligibility");
    }

    @Test
    @DisplayName("Customer is denied access when checking driver eligibility")
    void getDriverEligibility_customerForbidden() {
        String token = "Bearer mock-customer-token";
        when(driverService.resolveAuthenticatedUser(token)).thenReturn(customerUser);

        assertThatThrownBy(() -> eligibilityService.getDriverEligibility(token, "dp-001"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Insufficient permissions");
    }
}
