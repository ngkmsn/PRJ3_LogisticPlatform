package com.logistics.user.service;

import com.logistics.common.security.JwtTokenProvider;
import com.logistics.user.dto.LoginRequest;
import com.logistics.user.dto.LoginResponse;
import com.logistics.user.entity.User;
import com.logistics.user.entity.UserRole;
import com.logistics.user.entity.UserStatus;
import com.logistics.user.exception.AccountNotActiveException;
import com.logistics.user.exception.BadCredentialsException;
import com.logistics.user.repository.UserRepository;
import io.quarkus.elytron.security.common.BcryptUtil;
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
class AuthenticationServiceTest {

    @Mock
    private UserRepository userRepository;

    private JwtTokenProvider jwtTokenProvider;
    private AuthenticationService authenticationService;
    private User activeUser;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                86400000L,
                "logistics-platform"
        );
        authenticationService = new AuthenticationService(userRepository, jwtTokenProvider);

        activeUser = new User();
        activeUser.setId("u-12345");
        activeUser.setUsername("testuser");
        activeUser.setEmail("test@logistics.com");
        // Hash for "rawSecret123" generated via BcryptUtil
        activeUser.setPasswordHash(BcryptUtil.bcryptHash("rawSecret123"));
        activeUser.setRole(UserRole.DISPATCHER);
        activeUser.setStatus(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("Login success when credentials are valid")
    void login_success() {
        LoginRequest request = new LoginRequest("testuser", "rawSecret123");

        when(userRepository.findByUsernameOrEmail("testuser")).thenReturn(Optional.of(activeUser));

        LoginResponse response = authenticationService.login(request);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(jwtTokenProvider.validateToken(response.getAccessToken())).isTrue();
        assertThat(jwtTokenProvider.extractUsername(response.getAccessToken())).isEqualTo("testuser");
        assertThat(jwtTokenProvider.extractRole(response.getAccessToken())).isEqualTo("DISPATCHER");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(86400L);
        assertThat(response.getUser()).isNotNull();
        assertThat(response.getUser().getId()).isEqualTo("u-12345");
        assertThat(response.getUser().getUsername()).isEqualTo("testuser");
        assertThat(response.getUser().getEmail()).isEqualTo("test@logistics.com");
        assertThat(response.getUser().getRole()).isEqualTo("DISPATCHER");
        assertThat(response.getUser().getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("Login failure when user not found")
    void login_fails_userNotFound() {
        LoginRequest request = new LoginRequest("unknown", "secret");
        when(userRepository.findByUsernameOrEmail("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authenticationService.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid username or password");
    }

    @Test
    @DisplayName("Login failure when password does not match")
    void login_fails_invalidPassword() {
        LoginRequest request = new LoginRequest("testuser", "wrongSecret");

        when(userRepository.findByUsernameOrEmail("testuser")).thenReturn(Optional.of(activeUser));

        assertThatThrownBy(() -> authenticationService.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid username or password");
    }

    @Test
    @DisplayName("Login failure when user account is inactive")
    void login_fails_accountInactive() {
        activeUser.setStatus(UserStatus.INACTIVE);
        LoginRequest request = new LoginRequest("testuser", "rawSecret123");

        when(userRepository.findByUsernameOrEmail("testuser")).thenReturn(Optional.of(activeUser));

        assertThatThrownBy(() -> authenticationService.login(request))
                .isInstanceOf(AccountNotActiveException.class)
                .hasMessageContaining("inactive");
    }

    @Test
    @DisplayName("Login failure when user account is locked")
    void login_fails_accountLocked() {
        activeUser.setStatus(UserStatus.LOCKED);
        LoginRequest request = new LoginRequest("testuser", "rawSecret123");

        when(userRepository.findByUsernameOrEmail("testuser")).thenReturn(Optional.of(activeUser));

        assertThatThrownBy(() -> authenticationService.login(request))
                .isInstanceOf(AccountNotActiveException.class)
                .hasMessageContaining("locked");
    }

    @Test
    @DisplayName("getCurrentUserProfile success with valid token returns correct user profile and role")
    void getCurrentUserProfile_success() {
        String token = jwtTokenProvider.generateToken("u-12345", "testuser", "test@logistics.com", "DISPATCHER");
        when(userRepository.findByIdOptional("u-12345")).thenReturn(Optional.of(activeUser));

        var profile = authenticationService.getCurrentUserProfile("Bearer " + token);

        assertThat(profile).isNotNull();
        assertThat(profile.getUserId()).isEqualTo("u-12345");
        assertThat(profile.getUsername()).isEqualTo("testuser");
        assertThat(profile.getEmail()).isEqualTo("test@logistics.com");
        assertThat(profile.getRole()).isEqualTo("DISPATCHER");
        assertThat(profile.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("getCurrentUserProfile fails when Authorization header is missing or malformed")
    void getCurrentUserProfile_missingHeader() {
        assertThatThrownBy(() -> authenticationService.getCurrentUserProfile(null))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Missing or invalid");

        assertThatThrownBy(() -> authenticationService.getCurrentUserProfile("Basic dXNlcjpwYXNz"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Missing or invalid");
    }

    @Test
    @DisplayName("getCurrentUserProfile fails when token is invalid or expired")
    void getCurrentUserProfile_invalidToken() {
        assertThatThrownBy(() -> authenticationService.getCurrentUserProfile("Bearer invalid-token-xyz"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("invalid or expired");
    }

    @Test
    @DisplayName("getCurrentUserProfile rejects if user not found in database")
    void getCurrentUserProfile_userNotFound() {
        String token = jwtTokenProvider.generateToken("nonexistent-id", "ghost", "ghost@logistics.com", "CUSTOMER");
        when(userRepository.findByIdOptional("nonexistent-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authenticationService.getCurrentUserProfile("Bearer " + token))
                .isInstanceOf(com.logistics.user.exception.UserNotFoundException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("User identity is strictly tied to token: user A cannot access user B profile")
    void getCurrentUserProfile_tokenBoundIdentity() {
        User userB = new User();
        userB.setId("u-99999");
        userB.setUsername("userB");
        userB.setEmail("userb@logistics.com");
        userB.setRole(UserRole.DRIVER);
        userB.setStatus(UserStatus.ACTIVE);

        // Generate token for user A
        String tokenA = jwtTokenProvider.generateToken("u-12345", "testuser", "test@logistics.com", "DISPATCHER");
        when(userRepository.findByIdOptional("u-12345")).thenReturn(Optional.of(activeUser));

        var result = authenticationService.getCurrentUserProfile("Bearer " + tokenA);
        // Result must strictly belong to user A (id u-12345), cannot return user B
        assertThat(result.getUserId()).isEqualTo("u-12345");
        assertThat(result.getUsername()).isEqualTo("testuser");
        assertThat(result.getRole()).isEqualTo("DISPATCHER");
        assertThat(result.getUserId()).isNotEqualTo(userB.getId());
    }
}
