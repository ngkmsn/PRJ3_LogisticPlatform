package com.logistics.user.service;

import com.logistics.common.model.DriverAvailability;
import com.logistics.common.security.JwtTokenProvider;
import com.logistics.user.dto.CreateDriverProfileRequest;
import com.logistics.user.dto.DriverAvailabilityDto;
import com.logistics.user.dto.DriverProfileDto;
import com.logistics.user.dto.PagedResponse;
import com.logistics.user.dto.UpdateDriverAvailabilityRequest;
import com.logistics.user.dto.UpdateDriverProfileRequest;
import com.logistics.user.entity.DriverProfile;
import com.logistics.user.entity.User;
import com.logistics.user.entity.UserRole;
import com.logistics.user.entity.UserStatus;
import com.logistics.user.exception.AccessDeniedException;
import com.logistics.user.exception.BadCredentialsException;
import com.logistics.user.exception.DriverProfileAlreadyExistsException;
import com.logistics.user.exception.DriverProfileNotFoundException;
import com.logistics.user.exception.InvalidDriverUserException;
import com.logistics.user.exception.LicenseAlreadyRegisteredException;
import com.logistics.user.repository.DriverProfileRepository;
import com.logistics.user.repository.UserRepository;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DriverServiceTest {

    @Mock
    private DriverProfileRepository driverProfileRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PanacheQuery<DriverProfile> panacheQuery;

    private JwtTokenProvider jwtTokenProvider;
    private DriverService driverService;

    private User adminUser;
    private User driverUser1;
    private User driverUser2;
    private User customerUser;

    private String adminToken;
    private String driver1Token;
    private String driver2Token;
    private String customerToken;

    private DriverProfile driverProfile1;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                86400000L,
                "logistics-platform"
        );
        driverService = new DriverService(driverProfileRepository, userRepository, jwtTokenProvider);

        adminUser = new User("admin-id", "admin", "admin@logistics.com", "hash", UserRole.ADMIN, UserStatus.ACTIVE);
        driverUser1 = new User("driver-id-1", "driver1", "driver1@logistics.com", "hash", UserRole.DRIVER, UserStatus.ACTIVE);
        driverUser2 = new User("driver-id-2", "driver2", "driver2@logistics.com", "hash", UserRole.DRIVER, UserStatus.ACTIVE);
        customerUser = new User("customer-id", "customer", "customer@logistics.com", "hash", UserRole.CUSTOMER, UserStatus.ACTIVE);

        adminToken = "Bearer " + jwtTokenProvider.generateToken("admin-id", "admin", "admin@logistics.com", "ADMIN");
        driver1Token = "Bearer " + jwtTokenProvider.generateToken("driver-id-1", "driver1", "driver1@logistics.com", "DRIVER");
        driver2Token = "Bearer " + jwtTokenProvider.generateToken("driver-id-2", "driver2", "driver2@logistics.com", "DRIVER");
        customerToken = "Bearer " + jwtTokenProvider.generateToken("customer-id", "customer", "customer@logistics.com", "CUSTOMER");

        driverProfile1 = new DriverProfile(
                "dp-id-1", "driver-id-1", "Nguyễn Văn Một", "0901112222",
                "GPLX-111111", "C", "TRUCK_2T", "29C-111.11", "Hà Nội", "ACTIVE"
        );
    }

    @Test
    @DisplayName("Admin can create driver profile for valid driver user")
    void createDriverProfile_success() {
        when(userRepository.findByIdOptional("admin-id")).thenReturn(Optional.of(adminUser));
        when(userRepository.findByIdOptional("driver-id-1")).thenReturn(Optional.of(driverUser1));
        when(driverProfileRepository.existsByUserId("driver-id-1")).thenReturn(false);
        when(driverProfileRepository.existsByLicenseNumber("GPLX-111111")).thenReturn(false);

        CreateDriverProfileRequest req = new CreateDriverProfileRequest(
                "driver-id-1", "Nguyễn Văn Một", "0901112222",
                "GPLX-111111", "C", "TRUCK_2T", "29C-111.11", "Hà Nội", "ACTIVE"
        );

        DriverProfileDto dto = driverService.createDriverProfile(adminToken, req);

        assertThat(dto).isNotNull();
        assertThat(dto.getUserId()).isEqualTo("driver-id-1");
        assertThat(dto.getFullName()).isEqualTo("Nguyễn Văn Một");
        assertThat(dto.getLicenseNumber()).isEqualTo("GPLX-111111");
        assertThat(dto.getUsername()).isEqualTo("driver1");
        verify(driverProfileRepository).persist(any(DriverProfile.class));
    }

    @Test
    @DisplayName("Cannot create driver profile for non-existent user")
    void createDriverProfile_userNotFound() {
        when(userRepository.findByIdOptional("admin-id")).thenReturn(Optional.of(adminUser));
        when(userRepository.findByIdOptional("non-existent")).thenReturn(Optional.empty());

        CreateDriverProfileRequest req = new CreateDriverProfileRequest(
                "non-existent", "Tên Tài Xế", "0901112222",
                "GPLX-222222", "C", "TRUCK_2T", "29C-222.22", "Hà Nội", "ACTIVE"
        );

        assertThatThrownBy(() -> driverService.createDriverProfile(adminToken, req))
                .isInstanceOf(InvalidDriverUserException.class)
                .hasMessageContaining("User not found with id: non-existent");
    }

    @Test
    @DisplayName("Cannot create driver profile for user that does not have DRIVER role")
    void createDriverProfile_userNotDriverRole() {
        when(userRepository.findByIdOptional("admin-id")).thenReturn(Optional.of(adminUser));
        when(userRepository.findByIdOptional("customer-id")).thenReturn(Optional.of(customerUser));

        CreateDriverProfileRequest req = new CreateDriverProfileRequest(
                "customer-id", "Tên Khách Hàng", "0901112222",
                "GPLX-333333", "C", "TRUCK_2T", "29C-333.33", "Hà Nội", "ACTIVE"
        );

        assertThatThrownBy(() -> driverService.createDriverProfile(adminToken, req))
                .isInstanceOf(InvalidDriverUserException.class)
                .hasMessageContaining("does not have DRIVER role");
    }

    @Test
    @DisplayName("Cannot create duplicate driver profile for same user")
    void createDriverProfile_duplicateUserId() {
        when(userRepository.findByIdOptional("admin-id")).thenReturn(Optional.of(adminUser));
        when(userRepository.findByIdOptional("driver-id-1")).thenReturn(Optional.of(driverUser1));
        when(driverProfileRepository.existsByUserId("driver-id-1")).thenReturn(true);

        CreateDriverProfileRequest req = new CreateDriverProfileRequest(
                "driver-id-1", "Nguyễn Văn Một", "0901112222",
                "GPLX-111111", "C", "TRUCK_2T", "29C-111.11", "Hà Nội", "ACTIVE"
        );

        assertThatThrownBy(() -> driverService.createDriverProfile(adminToken, req))
                .isInstanceOf(DriverProfileAlreadyExistsException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("Cannot create driver profile with already registered license number")
    void createDriverProfile_duplicateLicenseNumber() {
        when(userRepository.findByIdOptional("admin-id")).thenReturn(Optional.of(adminUser));
        when(userRepository.findByIdOptional("driver-id-1")).thenReturn(Optional.of(driverUser1));
        when(driverProfileRepository.existsByUserId("driver-id-1")).thenReturn(false);
        when(driverProfileRepository.existsByLicenseNumber("GPLX-111111")).thenReturn(true);

        CreateDriverProfileRequest req = new CreateDriverProfileRequest(
                "driver-id-1", "Nguyễn Văn Một", "0901112222",
                "GPLX-111111", "C", "TRUCK_2T", "29C-111.11", "Hà Nội", "ACTIVE"
        );

        assertThatThrownBy(() -> driverService.createDriverProfile(adminToken, req))
                .isInstanceOf(LicenseAlreadyRegisteredException.class)
                .hasMessageContaining("already registered");
    }

    @Test
    @DisplayName("Driver role is not allowed to create driver profiles")
    void createDriverProfile_driverRoleForbidden() {
        when(userRepository.findByIdOptional("driver-id-1")).thenReturn(Optional.of(driverUser1));

        CreateDriverProfileRequest req = new CreateDriverProfileRequest(
                "driver-id-1", "Nguyễn Văn Một", "0901112222",
                "GPLX-111111", "C", "TRUCK_2T", "29C-111.11", "Hà Nội", "ACTIVE"
        );

        assertThatThrownBy(() -> driverService.createDriverProfile(driver1Token, req))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("privileges required");
    }

    @Test
    @DisplayName("Admin can get any driver profile by ID")
    void getDriverById_adminSuccess() {
        when(userRepository.findByIdOptional("admin-id")).thenReturn(Optional.of(adminUser));
        when(driverProfileRepository.findByIdOptional("dp-id-1")).thenReturn(Optional.of(driverProfile1));
        when(userRepository.findByIdOptional("driver-id-1")).thenReturn(Optional.of(driverUser1));

        DriverProfileDto dto = driverService.getDriverById(adminToken, "dp-id-1");

        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo("dp-id-1");
        assertThat(dto.getFullName()).isEqualTo("Nguyễn Văn Một");
    }

    @Test
    @DisplayName("Driver can get their own driver profile")
    void getDriverById_selfDriverSuccess() {
        when(userRepository.findByIdOptional("driver-id-1")).thenReturn(Optional.of(driverUser1));
        when(driverProfileRepository.findByIdOptional("dp-id-1")).thenReturn(Optional.of(driverProfile1));
        when(userRepository.findByIdOptional("driver-id-1")).thenReturn(Optional.of(driverUser1));

        DriverProfileDto dto = driverService.getDriverById(driver1Token, "dp-id-1");

        assertThat(dto).isNotNull();
        assertThat(dto.getUserId()).isEqualTo("driver-id-1");
    }

    @Test
    @DisplayName("Driver cannot view another driver's profile")
    void getDriverById_otherDriverForbidden() {
        when(userRepository.findByIdOptional("driver-id-2")).thenReturn(Optional.of(driverUser2));
        when(driverProfileRepository.findByIdOptional("dp-id-1")).thenReturn(Optional.of(driverProfile1));

        assertThatThrownBy(() -> driverService.getDriverById(driver2Token, "dp-id-1"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You can only view your own driver profile");
    }

    @Test
    @DisplayName("Customer is denied access to driver profiles")
    void getDriverById_customerForbidden() {
        when(userRepository.findByIdOptional("customer-id")).thenReturn(Optional.of(customerUser));

        assertThatThrownBy(() -> driverService.getDriverById(customerToken, "dp-id-1"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Insufficient permissions");
    }

    @Test
    @DisplayName("Driver can update their own driver profile")
    void updateDriverProfile_selfDriverSuccess() {
        when(userRepository.findByIdOptional("driver-id-1")).thenReturn(Optional.of(driverUser1));
        when(driverProfileRepository.findByIdOptional("dp-id-1")).thenReturn(Optional.of(driverProfile1));
        when(userRepository.findByIdOptional("driver-id-1")).thenReturn(Optional.of(driverUser1));

        UpdateDriverProfileRequest req = new UpdateDriverProfileRequest(
                "Nguyễn Văn Một Đã Đổi Tên", "0999888777", null, null,
                "TRUCK_3T", "29C-999.99", "Hải Phòng", null
        );

        DriverProfileDto updated = driverService.updateDriverProfile(driver1Token, "dp-id-1", req);

        assertThat(updated.getFullName()).isEqualTo("Nguyễn Văn Một Đã Đổi Tên");
        assertThat(updated.getPhoneNumber()).isEqualTo("0999888777");
        assertThat(updated.getVehiclePlate()).isEqualTo("29C-999.99");
        verify(driverProfileRepository).persist(driverProfile1);
    }

    @Test
    @DisplayName("Driver cannot update another driver's profile")
    void updateDriverProfile_otherDriverForbidden() {
        when(userRepository.findByIdOptional("driver-id-2")).thenReturn(Optional.of(driverUser2));
        when(driverProfileRepository.findByIdOptional("dp-id-1")).thenReturn(Optional.of(driverProfile1));

        UpdateDriverProfileRequest req = new UpdateDriverProfileRequest(
                "Hack Name", "0999888777", null, null, null, null, null, null
        );

        assertThatThrownBy(() -> driverService.updateDriverProfile(driver2Token, "dp-id-1", req))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You can only update your own driver profile");
    }

    @Test
    @DisplayName("Admin can update any driver profile and change driver status")
    void updateDriverProfile_adminSuccess() {
        when(userRepository.findByIdOptional("admin-id")).thenReturn(Optional.of(adminUser));
        when(driverProfileRepository.findByIdOptional("dp-id-1")).thenReturn(Optional.of(driverProfile1));
        when(userRepository.findByIdOptional("driver-id-1")).thenReturn(Optional.of(driverUser1));

        UpdateDriverProfileRequest req = new UpdateDriverProfileRequest(
                null, null, null, null, null, null, null, "SUSPENDED"
        );

        DriverProfileDto updated = driverService.updateDriverProfile(adminToken, "dp-id-1", req);

        assertThat(updated.getStatus()).isEqualTo("SUSPENDED");
        verify(driverProfileRepository).persist(driverProfile1);
    }

    @Test
    @DisplayName("Unauthenticated request is rejected with BadCredentialsException")
    void unauthenticated_rejected() {
        assertThatThrownBy(() -> driverService.getDriverById(null, "dp-id-1"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Missing or invalid");
    }

    @Test
    @DisplayName("Driver can get own availability status (default UNAVAILABLE)")
    void getMyAvailability_success() {
        when(userRepository.findByIdOptional("driver-id-1")).thenReturn(Optional.of(driverUser1));
        when(driverProfileRepository.findByUserId("driver-id-1")).thenReturn(Optional.of(driverProfile1));

        DriverAvailabilityDto dto = driverService.getMyAvailability(driver1Token);

        assertThat(dto).isNotNull();
        assertThat(dto.getDriverId()).isEqualTo("dp-id-1");
        assertThat(dto.getUserId()).isEqualTo("driver-id-1");
        assertThat(dto.getAvailability()).isEqualTo(DriverAvailability.UNAVAILABLE);
    }

    @Test
    @DisplayName("Non-driver caller is rejected when getting personal availability")
    void getMyAvailability_nonDriverRejected() {
        when(userRepository.findByIdOptional("customer-id")).thenReturn(Optional.of(customerUser));

        assertThatThrownBy(() -> driverService.getMyAvailability(customerToken))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only drivers can view personal availability");
    }

    @Test
    @DisplayName("Driver can toggle availability between AVAILABLE and UNAVAILABLE")
    void updateMyAvailability_success() {
        when(userRepository.findByIdOptional("driver-id-1")).thenReturn(Optional.of(driverUser1));
        when(driverProfileRepository.findByUserId("driver-id-1")).thenReturn(Optional.of(driverProfile1));

        // Switch to AVAILABLE
        DriverAvailabilityDto dto1 = driverService.updateMyAvailability(
                driver1Token, new UpdateDriverAvailabilityRequest(DriverAvailability.AVAILABLE));

        assertThat(dto1.getAvailability()).isEqualTo(DriverAvailability.AVAILABLE);
        assertThat(driverProfile1.getAvailability()).isEqualTo(DriverAvailability.AVAILABLE);
        verify(driverProfileRepository).persist(driverProfile1);

        // Switch back to UNAVAILABLE
        DriverAvailabilityDto dto2 = driverService.updateMyAvailability(
                driver1Token, new UpdateDriverAvailabilityRequest(DriverAvailability.UNAVAILABLE));

        assertThat(dto2.getAvailability()).isEqualTo(DriverAvailability.UNAVAILABLE);
        assertThat(driverProfile1.getAvailability()).isEqualTo(DriverAvailability.UNAVAILABLE);
    }

    @Test
    @DisplayName("Non-driver cannot update availability via updateMyAvailability")
    void updateMyAvailability_nonDriverForbidden() {
        when(userRepository.findByIdOptional("customer-id")).thenReturn(Optional.of(customerUser));

        UpdateDriverAvailabilityRequest req = new UpdateDriverAvailabilityRequest(DriverAvailability.AVAILABLE);

        assertThatThrownBy(() -> driverService.updateMyAvailability(customerToken, req))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only drivers can update their availability");
    }

    @Test
    @DisplayName("Update availability with null request throws IllegalArgumentException")
    void updateMyAvailability_nullAvailability() {
        when(userRepository.findByIdOptional("driver-id-1")).thenReturn(Optional.of(driverUser1));

        assertThatThrownBy(() -> driverService.updateMyAvailability(driver1Token, new UpdateDriverAvailabilityRequest(null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Availability status is required");
    }

    @Test
    @DisplayName("Admin can update any driver's availability")
    void updateDriverAvailability_adminSuccess() {
        when(userRepository.findByIdOptional("admin-id")).thenReturn(Optional.of(adminUser));
        when(driverProfileRepository.findByIdOptional("dp-id-1")).thenReturn(Optional.of(driverProfile1));

        DriverAvailabilityDto dto = driverService.updateDriverAvailability(
                adminToken, "dp-id-1", new UpdateDriverAvailabilityRequest(DriverAvailability.AVAILABLE));

        assertThat(dto.getAvailability()).isEqualTo(DriverAvailability.AVAILABLE);
        assertThat(driverProfile1.getAvailability()).isEqualTo(DriverAvailability.AVAILABLE);
    }

    @Test
    @DisplayName("Driver cannot update another driver's availability via ID endpoint")
    void updateDriverAvailability_otherDriverForbidden() {
        when(userRepository.findByIdOptional("driver-id-2")).thenReturn(Optional.of(driverUser2));
        when(driverProfileRepository.findByIdOptional("dp-id-1")).thenReturn(Optional.of(driverProfile1));

        UpdateDriverAvailabilityRequest req = new UpdateDriverAvailabilityRequest(DriverAvailability.AVAILABLE);

        assertThatThrownBy(() -> driverService.updateDriverAvailability(driver2Token, "dp-id-1", req))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You can only update your own availability");
    }
}
