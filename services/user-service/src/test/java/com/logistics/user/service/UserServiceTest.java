package com.logistics.user.service;

import com.logistics.common.security.JwtTokenProvider;
import com.logistics.user.dto.CreateUserRequest;
import com.logistics.user.dto.PagedResponse;
import com.logistics.user.dto.UpdateUserRequest;
import com.logistics.user.dto.UpdateUserStatusRequest;
import com.logistics.user.dto.UserProfileDto;
import com.logistics.user.entity.User;
import com.logistics.user.entity.UserRole;
import com.logistics.user.entity.UserStatus;
import com.logistics.user.exception.AccessDeniedException;
import com.logistics.user.exception.BadCredentialsException;
import com.logistics.user.exception.CannotLockLastAdminException;
import com.logistics.user.exception.UserAlreadyExistsException;
import com.logistics.user.exception.UserNotFoundException;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PanacheQuery<User> panacheQuery;

    private JwtTokenProvider jwtTokenProvider;
    private UserService userService;

    private User adminUser;
    private String adminToken;
    private String driverToken;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                86400000L,
                "logistics-platform"
        );
        userService = new UserService(userRepository, jwtTokenProvider);

        adminUser = new User();
        adminUser.setId("admin-uuid-001");
        adminUser.setUsername("admin");
        adminUser.setEmail("admin@logistics.com");
        adminUser.setRole(UserRole.ADMIN);
        adminUser.setStatus(UserStatus.ACTIVE);

        adminToken = "Bearer " + jwtTokenProvider.generateToken(
                "admin-uuid-001", "admin", "admin@logistics.com", "ADMIN"
        );

        driverToken = "Bearer " + jwtTokenProvider.generateToken(
                "driver-uuid-002", "driver", "driver@logistics.com", "DRIVER"
        );
    }

    @Test
    @DisplayName("Non-administrator access is denied with 403 AccessDeniedException")
    void nonAdmin_accessDenied() {
        assertThatThrownBy(() -> userService.listUsers(driverToken, 0, 10, null, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Administrator privileges required");
    }

    @Test
    @DisplayName("Unauthenticated request is rejected with BadCredentialsException")
    void unauthenticated_rejected() {
        assertThatThrownBy(() -> userService.listUsers(null, 0, 10, null, null))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Missing or invalid");
    }

    @Test
    @DisplayName("Admin can list users with pagination")
    void listUsers_success() {
        when(userRepository.findByIdOptional("admin-uuid-001")).thenReturn(Optional.of(adminUser));
        when(userRepository.find(anyString(), any(io.quarkus.panache.common.Parameters.class))).thenReturn(panacheQuery);
        when(panacheQuery.count()).thenReturn(1L);
        when(panacheQuery.page(any(io.quarkus.panache.common.Page.class))).thenReturn(panacheQuery);
        when(panacheQuery.list()).thenReturn(List.of(adminUser));

        PagedResponse<UserProfileDto> result = userService.listUsers(adminToken, 0, 10, null, null);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getUsername()).isEqualTo("admin");
        assertThat(result.getTotalElements()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Admin can get user by ID")
    void getUserById_success() {
        when(userRepository.findByIdOptional("admin-uuid-001")).thenReturn(Optional.of(adminUser));
        User target = new User("target-01", "targetuser", "target@logistics.com", "hash", UserRole.CUSTOMER, UserStatus.ACTIVE);
        when(userRepository.findByIdOptional("target-01")).thenReturn(Optional.of(target));

        UserProfileDto dto = userService.getUserById(adminToken, "target-01");

        assertThat(dto).isNotNull();
        assertThat(dto.getUserId()).isEqualTo("target-01");
        assertThat(dto.getUsername()).isEqualTo("targetuser");
        assertThat(dto.getRole()).isEqualTo("CUSTOMER");
    }

    @Test
    @DisplayName("Admin get user by ID throws UserNotFoundException when user does not exist")
    void getUserById_notFound() {
        when(userRepository.findByIdOptional("admin-uuid-001")).thenReturn(Optional.of(adminUser));
        when(userRepository.findByIdOptional("missing-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(adminToken, "missing-id"))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("User not found with id: missing-id");
    }

    @Test
    @DisplayName("Admin can create a new user with hashed password")
    void createUser_success() {
        when(userRepository.findByIdOptional("admin-uuid-001")).thenReturn(Optional.of(adminUser));
        when(userRepository.existsByUsername("newdriver")).thenReturn(false);
        when(userRepository.existsByEmail("newdriver@logistics.com")).thenReturn(false);

        CreateUserRequest req = new CreateUserRequest("newdriver", "newdriver@logistics.com", "DriverSecret123", UserRole.DRIVER);

        UserProfileDto created = userService.createUser(adminToken, req);

        assertThat(created).isNotNull();
        assertThat(created.getUsername()).isEqualTo("newdriver");
        assertThat(created.getEmail()).isEqualTo("newdriver@logistics.com");
        assertThat(created.getRole()).isEqualTo("DRIVER");
        assertThat(created.getStatus()).isEqualTo("ACTIVE");
        verify(userRepository).persist(any(User.class));
    }

    @Test
    @DisplayName("Creating user with duplicate username throws UserAlreadyExistsException")
    void createUser_duplicateUsername() {
        when(userRepository.findByIdOptional("admin-uuid-001")).thenReturn(Optional.of(adminUser));
        when(userRepository.existsByUsername("existingUser")).thenReturn(true);

        CreateUserRequest req = new CreateUserRequest("existingUser", "user@logistics.com", "pass123", UserRole.CUSTOMER);

        assertThatThrownBy(() -> userService.createUser(adminToken, req))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("already taken");
    }

    @Test
    @DisplayName("Creating user with duplicate email throws UserAlreadyExistsException")
    void createUser_duplicateEmail() {
        when(userRepository.findByIdOptional("admin-uuid-001")).thenReturn(Optional.of(adminUser));
        when(userRepository.existsByUsername("uniqueUser")).thenReturn(false);
        when(userRepository.existsByEmail("duplicate@logistics.com")).thenReturn(true);

        CreateUserRequest req = new CreateUserRequest("uniqueUser", "duplicate@logistics.com", "pass123", UserRole.CUSTOMER);

        assertThatThrownBy(() -> userService.createUser(adminToken, req))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("already registered");
    }

    @Test
    @DisplayName("Admin can update user fields")
    void updateUser_success() {
        when(userRepository.findByIdOptional("admin-uuid-001")).thenReturn(Optional.of(adminUser));
        User target = new User("target-01", "targetuser", "target@logistics.com", "hash", UserRole.CUSTOMER, UserStatus.ACTIVE);
        when(userRepository.findByIdOptional("target-01")).thenReturn(Optional.of(target));
        when(userRepository.existsByEmailAndIdNot(eq("updated@logistics.com"), eq("target-01"))).thenReturn(false);

        UpdateUserRequest req = new UpdateUserRequest("updated@logistics.com", UserRole.DISPATCHER, UserStatus.ACTIVE);
        UserProfileDto updated = userService.updateUser(adminToken, "target-01", req);

        assertThat(updated.getEmail()).isEqualTo("updated@logistics.com");
        assertThat(updated.getRole()).isEqualTo("DISPATCHER");
        verify(userRepository).persist(target);
    }

    @Test
    @DisplayName("Admin can update user status to LOCKED and ACTIVE")
    void updateUserStatus_success() {
        when(userRepository.findByIdOptional("admin-uuid-001")).thenReturn(Optional.of(adminUser));
        User target = new User("target-01", "targetuser", "target@logistics.com", "hash", UserRole.CUSTOMER, UserStatus.ACTIVE);
        when(userRepository.findByIdOptional("target-01")).thenReturn(Optional.of(target));

        UpdateUserStatusRequest req = new UpdateUserStatusRequest(UserStatus.LOCKED);
        UserProfileDto updated = userService.updateUserStatus(adminToken, "target-01", req);

        assertThat(updated.getStatus()).isEqualTo("LOCKED");
        verify(userRepository).persist(target);
    }

    @Test
    @DisplayName("Cannot lock the last remaining active Administrator in the system")
    void lockLastAdmin_prevented() {
        when(userRepository.findByIdOptional("admin-uuid-001")).thenReturn(Optional.of(adminUser));
        when(userRepository.countActiveAdmins()).thenReturn(1L);

        UpdateUserStatusRequest req = new UpdateUserStatusRequest(UserStatus.LOCKED);

        assertThatThrownBy(() -> userService.updateUserStatus(adminToken, "admin-uuid-001", req))
                .isInstanceOf(CannotLockLastAdminException.class)
                .hasMessageContaining("last remaining active Administrator");
    }

    @Test
    @DisplayName("Admin can view list of supported roles and their permissions")
    void listSupportedRoles_success() {
        when(userRepository.findByIdOptional("admin-uuid-001")).thenReturn(Optional.of(adminUser));

        var roles = userService.listSupportedRoles(adminToken);

        assertThat(roles).isNotNull();
        assertThat(roles).hasSize(4);
        assertThat(roles).extracting("role").containsExactlyInAnyOrder("ADMIN", "DISPATCHER", "DRIVER", "CUSTOMER");
    }

    @Test
    @DisplayName("Admin can update role of a user")
    void updateUserRole_success() {
        when(userRepository.findByIdOptional("admin-uuid-001")).thenReturn(Optional.of(adminUser));
        User target = new User("target-01", "targetuser", "target@logistics.com", "hash", UserRole.CUSTOMER, UserStatus.ACTIVE);
        when(userRepository.findByIdOptional("target-01")).thenReturn(Optional.of(target));

        var request = new com.logistics.user.dto.UpdateUserRoleRequest(UserRole.DRIVER);
        UserProfileDto updated = userService.updateUserRole(adminToken, "target-01", request);

        assertThat(updated).isNotNull();
        assertThat(updated.getRole()).isEqualTo("DRIVER");
        verify(userRepository).persist(target);
    }

    @Test
    @DisplayName("Cannot demote the last remaining active Administrator in the system")
    void demoteLastAdmin_prevented() {
        when(userRepository.findByIdOptional("admin-uuid-001")).thenReturn(Optional.of(adminUser));
        when(userRepository.countActiveAdmins()).thenReturn(1L);

        var request = new com.logistics.user.dto.UpdateUserRoleRequest(UserRole.CUSTOMER);

        assertThatThrownBy(() -> userService.updateUserRole(adminToken, "admin-uuid-001", request))
                .isInstanceOf(CannotLockLastAdminException.class)
                .hasMessageContaining("Cannot demote the last remaining active Administrator");
    }

    @Test
    @DisplayName("Revoked admin token is immediately rejected if role in database is no longer ADMIN")
    void revokedAdminRole_immediatelyDenied() {
        // Token has role ADMIN, but user in database was demoted to DRIVER
        User demotedUser = new User("admin-uuid-001", "admin", "admin@logistics.com", "hash", UserRole.DRIVER, UserStatus.ACTIVE);
        when(userRepository.findByIdOptional("admin-uuid-001")).thenReturn(Optional.of(demotedUser));

        assertThatThrownBy(() -> userService.listUsers(adminToken, 0, 10, null, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("revoked");
    }
}
