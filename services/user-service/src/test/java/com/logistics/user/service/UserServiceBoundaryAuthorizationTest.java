package com.logistics.user.service;

import com.logistics.common.model.DriverAvailability;
import com.logistics.common.security.JwtTokenProvider;
import com.logistics.common.security.ServiceTokenProvider;
import com.logistics.user.dto.DriverAvailabilityDto;
import com.logistics.user.dto.DriverProfileDto;
import com.logistics.user.dto.PagedResponse;
import com.logistics.user.dto.UpdateDriverAvailabilityRequest;
import com.logistics.user.dto.UserProfileDto;
import com.logistics.user.entity.DriverProfile;
import com.logistics.user.entity.User;
import com.logistics.user.entity.UserRole;
import com.logistics.user.entity.UserStatus;
import com.logistics.user.exception.AccessDeniedException;
import com.logistics.user.exception.BadCredentialsException;
import com.logistics.user.repository.DriverProfileRepository;
import com.logistics.user.repository.UserRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@QuarkusTest
class UserServiceBoundaryAuthorizationTest {

    private static final String SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    @Inject
    UserService userService;

    @Inject
    DriverService driverService;

    @Inject
    JwtTokenProvider jwtTokenProvider;

    @InjectMock
    UserRepository userRepository;

    @InjectMock
    DriverProfileRepository driverProfileRepository;

    private ServiceTokenProvider serviceTokenProvider;

    private String adminToken;
    private String dispatcherToken;
    private String driverAToken;
    private String driverBToken;
    private String customerToken;

    private User adminUser;
    private User driverAUser;
    private User driverBUser;
    private DriverProfile driverAProfile;
    private DriverProfile driverBProfile;

    @BeforeEach
    void setUp() {
        serviceTokenProvider = new ServiceTokenProvider("shipment-service", SECRET, "shipment-service", 300L);

        adminToken = "Bearer " + jwtTokenProvider.generateToken("adm-1", "admin", "admin@test.com", "ADMIN");
        dispatcherToken = "Bearer " + jwtTokenProvider.generateToken("disp-1", "dispatcher", "disp@test.com", "DISPATCHER");
        driverAToken = "Bearer " + jwtTokenProvider.generateToken("usr-drv-A", "driverA", "dA@test.com", "DRIVER");
        driverBToken = "Bearer " + jwtTokenProvider.generateToken("usr-drv-B", "driverB", "dB@test.com", "DRIVER");
        customerToken = "Bearer " + jwtTokenProvider.generateToken("cust-1", "customer", "c@test.com", "CUSTOMER");

        adminUser = new User();
        adminUser.setId("adm-1");
        adminUser.setUsername("admin");
        adminUser.setEmail("admin@test.com");
        adminUser.setRole(UserRole.ADMIN);
        adminUser.setStatus(UserStatus.ACTIVE);

        driverAUser = new User();
        driverAUser.setId("usr-drv-A");
        driverAUser.setUsername("driverA");
        driverAUser.setEmail("dA@test.com");
        driverAUser.setRole(UserRole.DRIVER);
        driverAUser.setStatus(UserStatus.ACTIVE);

        driverBUser = new User();
        driverBUser.setId("usr-drv-B");
        driverBUser.setUsername("driverB");
        driverBUser.setEmail("dB@test.com");
        driverBUser.setRole(UserRole.DRIVER);
        driverBUser.setStatus(UserStatus.ACTIVE);

        User customerUser = new User();
        customerUser.setId("cust-1");
        customerUser.setUsername("customer");
        customerUser.setEmail("c@test.com");
        customerUser.setRole(UserRole.CUSTOMER);
        customerUser.setStatus(UserStatus.ACTIVE);

        driverAProfile = new DriverProfile("dp-A", "usr-drv-A", "Nguyen Van A", "0901111111",
                "29A-12345", "B2", "MOTORCYCLE", "59A-11111", "123 Hanoi", "ACTIVE");
        driverAProfile.setAvailability(DriverAvailability.AVAILABLE);

        driverBProfile = new DriverProfile("dp-B", "usr-drv-B", "Tran Van B", "0902222222",
                "29B-67890", "B2", "MOTORCYCLE", "59B-22222", "456 Hanoi", "ACTIVE");
        driverBProfile.setAvailability(DriverAvailability.UNAVAILABLE);

        when(userRepository.findByIdOptional("adm-1")).thenReturn(Optional.of(adminUser));
        when(userRepository.findByIdOptional("usr-drv-A")).thenReturn(Optional.of(driverAUser));
        when(userRepository.findByIdOptional("usr-drv-B")).thenReturn(Optional.of(driverBUser));
        when(userRepository.findByIdOptional("cust-1")).thenReturn(Optional.of(customerUser));

        when(driverProfileRepository.findByUserId("usr-drv-A")).thenReturn(Optional.of(driverAProfile));
        when(driverProfileRepository.findByUserId("usr-drv-B")).thenReturn(Optional.of(driverBProfile));
        when(driverProfileRepository.findByIdOptional("dp-A")).thenReturn(Optional.of(driverAProfile));
        when(driverProfileRepository.findByIdOptional("dp-B")).thenReturn(Optional.of(driverBProfile));
    }

    @Test
    @DisplayName("PB-058 [401]: Missing or invalid token fails authentication at boundary")
    void unauthenticated_rejected() {
        assertThatThrownBy(() -> userService.listUsers(null, 0, 10, null, null))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Missing or invalid Authorization header");

        assertThatThrownBy(() -> userService.listUsers("Bearer invalid-token", 0, 10, null, null))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Token is invalid or expired");
    }

    @Test
    @DisplayName("PB-058 [RBAC]: Non-admin roles (DISPATCHER, DRIVER, CUSTOMER) cannot access user management")
    void nonAdmin_userManagement_rejectedWith403() {
        assertThatThrownBy(() -> userService.listUsers(dispatcherToken, 0, 10, null, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Administrator privileges required");

        assertThatThrownBy(() -> userService.listUsers(driverAToken, 0, 10, null, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Administrator privileges required");

        assertThatThrownBy(() -> userService.listUsers(customerToken, 0, 10, null, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Administrator privileges required");
    }

    @Test
    @DisplayName("PB-058 [Driver Ownership]: Driver A can view own profile and update personal availability")
    void driverA_manageSelf_success() {
        DriverProfileDto profile = driverService.getMyDriverProfile(driverAToken);
        assertThat(profile).isNotNull();
        assertThat(profile.getId()).isEqualTo("dp-A");

        UpdateDriverAvailabilityRequest req = new UpdateDriverAvailabilityRequest(DriverAvailability.UNAVAILABLE);
        DriverAvailabilityDto avail = driverService.updateMyAvailability(driverAToken, req);
        assertThat(avail).isNotNull();
        assertThat(avail.getAvailability()).isEqualTo(DriverAvailability.UNAVAILABLE);
    }

    @Test
    @DisplayName("PB-058 [IDOR Prevention]: Driver A cannot view or update Driver B's profile")
    void driverA_accessDriverB_rejectedWith403() {
        // Driver A trying to view Driver B profile
        assertThatThrownBy(() -> driverService.getDriverById(driverAToken, "dp-B"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You can only view your own driver profile");

        // Driver A trying to view Driver B availability
        assertThatThrownBy(() -> driverService.getDriverAvailability(driverAToken, "dp-B"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You can only view your own availability");
    }

    @Test
    @DisplayName("PB-058 [RBAC]: Customer role cannot view driver profiles or availability")
    void customer_accessDriver_rejectedWith403() {
        assertThatThrownBy(() -> driverService.getDriverById(customerToken, "dp-A"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Insufficient permissions");

        assertThatThrownBy(() -> driverService.getDriverAvailability(customerToken, "dp-A"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Insufficient permissions");
    }

    @Test
    @DisplayName("PB-058 [S2S Authorization]: Service with valid audience and scope succeeds; missing scope fails")
    void serviceToService_driverRead() {
        // Valid S2S token
        String validS2SToken = "Bearer " + serviceTokenProvider.generateToken("user-service", Set.of("driver:read"));
        DriverProfileDto dto = driverService.getDriverById(validS2SToken, "dp-A");
        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo("dp-A");

        // S2S with wrong audience
        String wrongAudToken = "Bearer " + serviceTokenProvider.generateToken("order-service", Set.of("driver:read"));
        assertThatThrownBy(() -> driverService.getDriverById(wrongAudToken, "dp-A"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Invalid token audience: expected user-service");

        // S2S with missing scope
        String missingScopeToken = "Bearer " + serviceTokenProvider.generateToken("user-service", Set.of("unrelated:scope"));
        assertThatThrownBy(() -> driverService.getDriverById(missingScopeToken, "dp-A"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Insufficient service permissions: required scope driver:read");
    }
}
