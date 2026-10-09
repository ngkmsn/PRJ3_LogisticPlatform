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
import com.logistics.user.exception.AccountNotActiveException;
import com.logistics.user.exception.BadCredentialsException;
import com.logistics.user.exception.CannotLockLastAdminException;
import com.logistics.user.exception.UserAlreadyExistsException;
import com.logistics.user.exception.UserNotFoundException;
import com.logistics.user.repository.UserRepository;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @Inject
    public UserService(UserRepository userRepository, JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public User requireAdmin(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Admin authorization failed: missing or invalid Authorization header");
            throw new BadCredentialsException("Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7).trim();
        if (!jwtTokenProvider.validateToken(token)) {
            log.warn("Admin authorization failed: token is invalid or expired");
            throw new BadCredentialsException("Token is invalid or expired");
        }

        String role = jwtTokenProvider.extractRole(token);
        if (!UserRole.ADMIN.name().equalsIgnoreCase(role)) {
            log.warn("Admin authorization rejected: caller role '{}' does not have Administrator privileges", role);
            throw new AccessDeniedException("Access denied: Administrator privileges required");
        }

        String userId = jwtTokenProvider.extractUserId(token);
        User adminUser = userRepository.findByIdOptional(userId)
                .orElseThrow(() -> {
                    log.warn("Admin authorization failed: admin user id '{}' not found in database", userId);
                    return new BadCredentialsException("User identity not found");
                });

        if (adminUser.getStatus() != UserStatus.ACTIVE) {
            log.warn("Admin authorization rejected: admin account status is {}", adminUser.getStatus());
            throw new AccountNotActiveException(adminUser.getStatus().name());
        }

        return adminUser;
    }

    public PagedResponse<UserProfileDto> listUsers(String authHeader, int page, int size, String roleFilter, String statusFilter) {
        requireAdmin(authHeader);

        int pageIndex = Math.max(0, page);
        int pageSize = (size <= 0 || size > 100) ? 10 : size;

        StringBuilder query = new StringBuilder("1=1");
        Parameters params = new Parameters();

        if (roleFilter != null && !roleFilter.isBlank()) {
            UserRole r = UserRole.fromString(roleFilter);
            if (r != null) {
                query.append(" and role = :role");
                params.and("role", r);
            }
        }

        if (statusFilter != null && !statusFilter.isBlank()) {
            UserStatus s = UserStatus.fromString(statusFilter);
            if (s != null) {
                query.append(" and status = :status");
                params.and("status", s);
            }
        }

        var panacheQuery = userRepository.find(query.toString() + " order by createdAt desc", params);
        long totalElements = panacheQuery.count();

        List<User> users = panacheQuery.page(Page.of(pageIndex, pageSize)).list();
        List<UserProfileDto> dtos = users.stream()
                .map(UserProfileDto::fromEntity)
                .toList();

        return PagedResponse.of(dtos, pageIndex, pageSize, totalElements);
    }

    public UserProfileDto getUserById(String authHeader, String id) {
        requireAdmin(authHeader);

        User user = userRepository.findByIdOptional(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));

        return UserProfileDto.fromEntity(user);
    }

    @Transactional
    public UserProfileDto createUser(String authHeader, CreateUserRequest request) {
        requireAdmin(authHeader);

        String username = request.getUsername().trim();
        String email = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByUsername(username)) {
            log.warn("User creation rejected: username '{}' already exists", username);
            throw new UserAlreadyExistsException("Username '" + username + "' is already taken");
        }

        if (userRepository.existsByEmail(email)) {
            log.warn("User creation rejected: email '{}' already exists", email);
            throw new UserAlreadyExistsException("Email '" + email + "' is already registered");
        }

        String passwordHash = BcryptUtil.bcryptHash(request.getPassword());

        User user = new User();
        user.setId(UUID.randomUUID().toString());
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        user.setRole(request.getRole());
        user.setStatus(request.getStatus() != null ? request.getStatus() : UserStatus.ACTIVE);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());

        userRepository.persist(user);

        log.info("Administrator created new user: id='{}', username='{}', role='{}'", user.getId(), user.getUsername(), user.getRole());

        return UserProfileDto.fromEntity(user);
    }

    @Transactional
    public UserProfileDto updateUser(String authHeader, String id, UpdateUserRequest request) {
        requireAdmin(authHeader);

        User user = userRepository.findByIdOptional(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));

        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            String newEmail = request.getEmail().trim().toLowerCase();
            if (!newEmail.equalsIgnoreCase(user.getEmail())) {
                if (userRepository.existsByEmailAndIdNot(newEmail, id)) {
                    log.warn("User update rejected: email '{}' already taken by another user", newEmail);
                    throw new UserAlreadyExistsException("Email '" + newEmail + "' is already registered to another user");
                }
                user.setEmail(newEmail);
            }
        }

        // Guard against locking/demoting the last active administrator
        boolean isCurrentlyActiveAdmin = (user.getRole() == UserRole.ADMIN && user.getStatus() == UserStatus.ACTIVE);
        boolean wouldDemoteAdmin = (request.getRole() != null && request.getRole() != UserRole.ADMIN);
        boolean wouldDeactivateAdmin = (request.getStatus() != null && request.getStatus() != UserStatus.ACTIVE);

        if (isCurrentlyActiveAdmin && (wouldDemoteAdmin || wouldDeactivateAdmin)) {
            long activeAdminsCount = userRepository.countActiveAdmins();
            if (activeAdminsCount <= 1) {
                log.warn("Admin update rejected: attempt to demote/deactivate the last active administrator");
                throw new CannotLockLastAdminException("Cannot lock, deactivate or demote the last remaining active Administrator in the system");
            }
        }

        if (request.getRole() != null) {
            user.setRole(request.getRole());
        }

        if (request.getStatus() != null) {
            user.setStatus(request.getStatus());
        }

        user.setUpdatedAt(Instant.now());
        userRepository.persist(user);

        log.info("Administrator updated user: id='{}', role='{}', status='{}'", user.getId(), user.getRole(), user.getStatus());

        return UserProfileDto.fromEntity(user);
    }

    @Transactional
    public UserProfileDto updateUserStatus(String authHeader, String id, UpdateUserStatusRequest request) {
        requireAdmin(authHeader);

        User user = userRepository.findByIdOptional(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));

        // Guard against locking the last active administrator
        if (user.getRole() == UserRole.ADMIN && user.getStatus() == UserStatus.ACTIVE && request.getStatus() != UserStatus.ACTIVE) {
            long activeAdminsCount = userRepository.countActiveAdmins();
            if (activeAdminsCount <= 1) {
                log.warn("Lock user rejected: attempt to lock the last active administrator");
                throw new CannotLockLastAdminException("Cannot lock or deactivate the last remaining active Administrator in the system");
            }
        }

        user.setStatus(request.getStatus());
        user.setUpdatedAt(Instant.now());
        userRepository.persist(user);

        log.info("Administrator changed user status: id='{}', newStatus='{}'", user.getId(), user.getStatus());

        return UserProfileDto.fromEntity(user);
    }
}
