package com.logistics.user.service;

import com.logistics.common.security.JwtTokenProvider;
import com.logistics.user.dto.LoginRequest;
import com.logistics.user.dto.LoginResponse;
import com.logistics.user.dto.UserProfileDto;
import com.logistics.user.dto.UserSummaryDto;
import com.logistics.user.entity.User;
import com.logistics.user.entity.UserStatus;
import com.logistics.user.exception.AccountNotActiveException;
import com.logistics.user.exception.BadCredentialsException;
import com.logistics.user.exception.UserNotFoundException;
import com.logistics.user.repository.UserRepository;
import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class AuthenticationService {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @Inject
    public AuthenticationService(UserRepository userRepository, JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public LoginResponse login(LoginRequest request) {
        String identifier = request.getUsername();

        User user = userRepository.findByUsernameOrEmail(identifier)
                .orElseThrow(() -> {
                    log.warn("Authentication failed: user identifier not found");
                    return new BadCredentialsException();
                });

        if (user.getStatus() != UserStatus.ACTIVE) {
            log.warn("Authentication rejected: user '{}' account status is {}", user.getUsername(), user.getStatus());
            throw new AccountNotActiveException(user.getStatus().name());
        }

        if (!BcryptUtil.matches(request.getPassword(), user.getPasswordHash())) {
            log.warn("Authentication failed: invalid password for user '{}'", user.getUsername());
            throw new BadCredentialsException();
        }

        String roleName = user.getRole() != null ? user.getRole().name() : "CUSTOMER";
        String token = jwtTokenProvider.generateToken(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                roleName
        );

        long expiresInSeconds = jwtTokenProvider.getExpirationMs() / 1000L;

        log.info("User '{}' (role={}) successfully authenticated", user.getUsername(), roleName);

        return LoginResponse.of(
                token,
                expiresInSeconds,
                UserSummaryDto.fromEntity(user)
        );
    }

    public UserProfileDto getCurrentUserProfile(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Get current user failed: missing or invalid Authorization header");
            throw new BadCredentialsException("Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7).trim();
        return getCurrentUserProfileByToken(token);
    }

    public UserProfileDto getCurrentUserProfileByToken(String token) {
        if (token == null || token.isBlank() || !jwtTokenProvider.validateToken(token)) {
            log.warn("Get current user failed: token is invalid or expired");
            throw new BadCredentialsException("Token is invalid or expired");
        }

        String userId = jwtTokenProvider.extractUserId(token);
        if (userId == null || userId.isBlank()) {
            log.warn("Get current user failed: token does not contain a valid userId claim");
            throw new BadCredentialsException("Token does not contain valid user identity");
        }

        User user = userRepository.findByIdOptional(userId)
                .orElseThrow(() -> {
                    log.warn("Get current user failed: user with id '{}' not found in database", userId);
                    return new UserNotFoundException("User not found");
                });

        if (user.getStatus() != UserStatus.ACTIVE) {
            log.warn("Get current user rejected: user '{}' account status is {}", user.getUsername(), user.getStatus());
            throw new AccountNotActiveException(user.getStatus().name());
        }

        return UserProfileDto.fromEntity(user);
    }
}
