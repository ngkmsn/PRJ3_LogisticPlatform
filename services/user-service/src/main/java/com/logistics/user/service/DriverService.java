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
import com.logistics.user.exception.AccountNotActiveException;
import com.logistics.user.exception.BadCredentialsException;
import com.logistics.user.exception.DriverProfileAlreadyExistsException;
import com.logistics.user.exception.DriverProfileNotFoundException;
import com.logistics.user.exception.InvalidDriverUserException;
import com.logistics.user.exception.LicenseAlreadyRegisteredException;
import com.logistics.user.repository.DriverProfileRepository;
import com.logistics.user.repository.UserRepository;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class DriverService {

    private static final Logger log = LoggerFactory.getLogger(DriverService.class);

    private final DriverProfileRepository driverProfileRepository;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @Inject
    public DriverService(DriverProfileRepository driverProfileRepository,
                         UserRepository userRepository,
                         JwtTokenProvider jwtTokenProvider) {
        this.driverProfileRepository = driverProfileRepository;
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public User resolveAuthenticatedUser(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Authentication failed: missing or invalid Authorization header");
            throw new BadCredentialsException("Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7).trim();
        if (!jwtTokenProvider.validateToken(token)) {
            log.warn("Authentication failed: token is invalid or expired");
            throw new BadCredentialsException("Token is invalid or expired");
        }

        String userId = jwtTokenProvider.extractUserId(token);
        User user = userRepository.findByIdOptional(userId)
                .orElseThrow(() -> {
                    log.warn("Authentication failed: user id '{}' not found in database", userId);
                    return new BadCredentialsException("User identity not found");
                });

        if (user.getStatus() != UserStatus.ACTIVE) {
            log.warn("Access rejected: user account status is {}", user.getStatus());
            throw new AccountNotActiveException(user.getStatus().name());
        }

        return user;
    }

    public PagedResponse<DriverProfileDto> listDrivers(String authHeader, int page, int size, String statusFilter) {
        User caller = resolveAuthenticatedUser(authHeader);

        // Only ADMIN and DISPATCHER can view the list of all drivers
        if (caller.getRole() != UserRole.ADMIN && caller.getRole() != UserRole.DISPATCHER) {
            log.warn("Access denied: caller role '{}' cannot list all driver profiles", caller.getRole());
            throw new AccessDeniedException("Access denied: Administrator or Dispatcher privileges required");
        }

        int pageIndex = Math.max(0, page);
        int pageSize = (size <= 0 || size > 100) ? 10 : size;

        StringBuilder query = new StringBuilder("1=1");
        Parameters params = new Parameters();

        if (statusFilter != null && !statusFilter.isBlank()) {
            query.append(" and status = :status");
            params.and("status", statusFilter.trim().toUpperCase());
        }

        var panacheQuery = driverProfileRepository.find(query.toString() + " order by createdAt desc", params);
        long totalElements = panacheQuery.count();

        List<DriverProfile> profiles = panacheQuery.page(Page.of(pageIndex, pageSize)).list();
        List<DriverProfileDto> dtos = profiles.stream()
                .map(p -> {
                    User driverUser = userRepository.findByIdOptional(p.getUserId()).orElse(null);
                    return DriverProfileDto.from(p, driverUser);
                })
                .toList();

        return PagedResponse.of(dtos, pageIndex, pageSize, totalElements);
    }

    public DriverProfileDto getDriverById(String authHeader, String id) {
        User caller = resolveAuthenticatedUser(authHeader);

        if (caller.getRole() == UserRole.CUSTOMER) {
            log.warn("Access denied: CUSTOMER role cannot view driver profiles");
            throw new AccessDeniedException("Access denied: Insufficient permissions");
        }

        DriverProfile profile = findDriverProfileByIdOrUserId(id);

        // If caller is DRIVER, they can only view their own profile
        if (caller.getRole() == UserRole.DRIVER && !profile.getUserId().equals(caller.getId())) {
            log.warn("Access denied: Driver '{}' attempted to view driver profile '{}'", caller.getId(), id);
            throw new AccessDeniedException("Access denied: You can only view your own driver profile");
        }

        User driverUser = userRepository.findByIdOptional(profile.getUserId()).orElse(null);
        return DriverProfileDto.from(profile, driverUser);
    }

    public DriverProfileDto getMyDriverProfile(String authHeader) {
        User caller = resolveAuthenticatedUser(authHeader);

        if (caller.getRole() != UserRole.DRIVER && caller.getRole() != UserRole.ADMIN && caller.getRole() != UserRole.DISPATCHER) {
            log.warn("Access denied: caller role '{}' does not have driver profile", caller.getRole());
            throw new AccessDeniedException("Access denied: Only drivers have driver profiles");
        }

        DriverProfile profile = driverProfileRepository.findByUserId(caller.getId())
                .orElseThrow(() -> new DriverProfileNotFoundException("Driver profile not found for current user"));

        return DriverProfileDto.from(profile, caller);
    }

    @Transactional
    public DriverProfileDto createDriverProfile(String authHeader, CreateDriverProfileRequest request) {
        User caller = resolveAuthenticatedUser(authHeader);

        if (caller.getRole() != UserRole.ADMIN && caller.getRole() != UserRole.DISPATCHER) {
            log.warn("Access denied: caller role '{}' cannot create driver profile", caller.getRole());
            throw new AccessDeniedException("Access denied: Administrator or Dispatcher privileges required");
        }

        String targetUserId = request.getUserId().trim();
        User targetUser = userRepository.findByIdOptional(targetUserId)
                .orElseThrow(() -> new InvalidDriverUserException("User not found with id: " + targetUserId));

        if (targetUser.getRole() != UserRole.DRIVER) {
            log.warn("Driver creation rejected: target user '{}' has role '{}', expected DRIVER", targetUserId, targetUser.getRole());
            throw new InvalidDriverUserException("Cannot create driver profile: User '" + targetUser.getUsername()
                    + "' does not have DRIVER role (current role: " + targetUser.getRole() + ")");
        }

        if (driverProfileRepository.existsByUserId(targetUserId)) {
            log.warn("Driver creation rejected: driver profile already exists for user '{}'", targetUserId);
            throw new DriverProfileAlreadyExistsException("Driver profile already exists for user: " + targetUser.getUsername());
        }

        String licenseNumber = request.getLicenseNumber().trim().toUpperCase();
        if (driverProfileRepository.existsByLicenseNumber(licenseNumber)) {
            log.warn("Driver creation rejected: license number '{}' already registered", licenseNumber);
            throw new LicenseAlreadyRegisteredException("Driver license number '" + licenseNumber + "' is already registered");
        }

        DriverProfile profile = new DriverProfile();
        profile.setId(UUID.randomUUID().toString());
        profile.setUserId(targetUserId);
        profile.setFullName(request.getFullName().trim());
        profile.setPhoneNumber(request.getPhoneNumber().trim());
        profile.setLicenseNumber(licenseNumber);
        profile.setLicenseClass(request.getLicenseClass().trim().toUpperCase());
        profile.setVehicleType(request.getVehicleType().trim());
        profile.setVehiclePlate(request.getVehiclePlate().trim().toUpperCase());
        profile.setAddress(request.getAddress() != null ? request.getAddress().trim() : null);
        profile.setStatus(request.getStatus() != null && !request.getStatus().isBlank()
                ? request.getStatus().trim().toUpperCase() : "ACTIVE");
        profile.setAvailability(DriverAvailability.UNAVAILABLE);
        profile.setCreatedAt(Instant.now());
        profile.setUpdatedAt(Instant.now());

        driverProfileRepository.persist(profile);

        log.info("Driver profile created successfully: id='{}', userId='{}', fullName='{}'",
                profile.getId(), profile.getUserId(), profile.getFullName());

        return DriverProfileDto.from(profile, targetUser);
    }

    @Transactional
    public DriverProfileDto updateDriverProfile(String authHeader, String id, UpdateDriverProfileRequest request) {
        User caller = resolveAuthenticatedUser(authHeader);

        if (caller.getRole() == UserRole.CUSTOMER) {
            log.warn("Access denied: CUSTOMER role cannot update driver profile");
            throw new AccessDeniedException("Access denied: Insufficient permissions");
        }

        DriverProfile profile = findDriverProfileByIdOrUserId(id);

        boolean isManagement = (caller.getRole() == UserRole.ADMIN || caller.getRole() == UserRole.DISPATCHER);
        boolean isSelf = profile.getUserId().equals(caller.getId());

        if (!isManagement && !isSelf) {
            log.warn("Access denied: Driver '{}' attempted to update driver profile '{}'", caller.getId(), id);
            throw new AccessDeniedException("Access denied: You can only update your own driver profile");
        }

        if (request.getLicenseNumber() != null && !request.getLicenseNumber().isBlank()) {
            String newLicense = request.getLicenseNumber().trim().toUpperCase();
            if (!newLicense.equalsIgnoreCase(profile.getLicenseNumber())) {
                if (driverProfileRepository.existsByLicenseNumberAndIdNot(newLicense, profile.getId())) {
                    log.warn("Driver update rejected: license number '{}' already taken", newLicense);
                    throw new LicenseAlreadyRegisteredException("Driver license number '" + newLicense + "' is already registered to another driver");
                }
                profile.setLicenseNumber(newLicense);
            }
        }

        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            profile.setFullName(request.getFullName().trim());
        }

        if (request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank()) {
            profile.setPhoneNumber(request.getPhoneNumber().trim());
        }

        if (request.getLicenseClass() != null && !request.getLicenseClass().isBlank()) {
            profile.setLicenseClass(request.getLicenseClass().trim().toUpperCase());
        }

        if (request.getVehicleType() != null && !request.getVehicleType().isBlank()) {
            profile.setVehicleType(request.getVehicleType().trim());
        }

        if (request.getVehiclePlate() != null && !request.getVehiclePlate().isBlank()) {
            profile.setVehiclePlate(request.getVehiclePlate().trim().toUpperCase());
        }

        if (request.getAddress() != null) {
            profile.setAddress(request.getAddress().trim());
        }

        // Only Administrator or Dispatcher can change driver status (ACTIVE, SUSPENDED, INACTIVE)
        if (isManagement && request.getStatus() != null && !request.getStatus().isBlank()) {
            profile.setStatus(request.getStatus().trim().toUpperCase());
        }

        profile.setUpdatedAt(Instant.now());
        driverProfileRepository.persist(profile);

        log.info("Driver profile updated successfully: id='{}', userId='{}'", profile.getId(), profile.getUserId());

        User driverUser = userRepository.findByIdOptional(profile.getUserId()).orElse(null);
        return DriverProfileDto.from(profile, driverUser);
    }

    @Transactional
    public DriverAvailabilityDto getMyAvailability(String authHeader) {
        User caller = resolveAuthenticatedUser(authHeader);

        if (caller.getRole() != UserRole.DRIVER) {
            log.warn("Access denied: caller role '{}' is not DRIVER for /drivers/me/availability", caller.getRole());
            throw new AccessDeniedException("Access denied: Only drivers can view personal availability");
        }

        DriverProfile profile = driverProfileRepository.findByUserId(caller.getId())
                .orElseThrow(() -> new DriverProfileNotFoundException("Driver profile not found for current user"));

        return DriverAvailabilityDto.from(profile);
    }

    @Transactional
    public DriverAvailabilityDto updateMyAvailability(String authHeader, UpdateDriverAvailabilityRequest request) {
        User caller = resolveAuthenticatedUser(authHeader);

        if (caller.getRole() != UserRole.DRIVER) {
            log.warn("Access denied: caller role '{}' cannot update driver availability", caller.getRole());
            throw new AccessDeniedException("Access denied: Only drivers can update their availability");
        }

        if (request == null || request.getAvailability() == null) {
            throw new IllegalArgumentException("Availability status is required");
        }

        DriverProfile profile = driverProfileRepository.findByUserId(caller.getId())
                .orElseThrow(() -> new DriverProfileNotFoundException("Driver profile not found for current user"));

        profile.setAvailability(request.getAvailability());
        profile.setUpdatedAt(Instant.now());
        driverProfileRepository.persist(profile);

        log.info("Driver availability updated by self: userId='{}', driverId='{}', availability={}",
                caller.getId(), profile.getId(), profile.getAvailability());

        return DriverAvailabilityDto.from(profile);
    }

    public DriverAvailabilityDto getDriverAvailability(String authHeader, String id) {
        User caller = resolveAuthenticatedUser(authHeader);

        if (caller.getRole() == UserRole.CUSTOMER) {
            log.warn("Access denied: CUSTOMER role cannot view driver availability");
            throw new AccessDeniedException("Access denied: Insufficient permissions");
        }

        DriverProfile profile = findDriverProfileByIdOrUserId(id);

        if (caller.getRole() == UserRole.DRIVER && !profile.getUserId().equals(caller.getId())) {
            log.warn("Access denied: Driver '{}' attempted to view availability of '{}'", caller.getId(), id);
            throw new AccessDeniedException("Access denied: You can only view your own availability");
        }

        return DriverAvailabilityDto.from(profile);
    }

    @Transactional
    public DriverAvailabilityDto updateDriverAvailability(String authHeader, String id, UpdateDriverAvailabilityRequest request) {
        User caller = resolveAuthenticatedUser(authHeader);

        if (caller.getRole() == UserRole.CUSTOMER) {
            log.warn("Access denied: CUSTOMER role cannot update driver availability");
            throw new AccessDeniedException("Access denied: Insufficient permissions");
        }

        DriverProfile profile = findDriverProfileByIdOrUserId(id);

        boolean isManagement = (caller.getRole() == UserRole.ADMIN || caller.getRole() == UserRole.DISPATCHER);
        boolean isSelf = profile.getUserId().equals(caller.getId());

        if (!isManagement && !isSelf) {
            log.warn("Access denied: Driver '{}' attempted to update availability of driver '{}'", caller.getId(), id);
            throw new AccessDeniedException("Access denied: You can only update your own availability");
        }

        if (request == null || request.getAvailability() == null) {
            throw new IllegalArgumentException("Availability status is required");
        }

        profile.setAvailability(request.getAvailability());
        profile.setUpdatedAt(Instant.now());
        driverProfileRepository.persist(profile);

        log.info("Driver availability updated: id='{}', userId='{}', availability={}",
                profile.getId(), profile.getUserId(), profile.getAvailability());

        return DriverAvailabilityDto.from(profile);
    }

    private DriverProfile findDriverProfileByIdOrUserId(String id) {
        return driverProfileRepository.findByIdOptional(id)
                .or(() -> driverProfileRepository.findByUserId(id))
                .orElseThrow(() -> new DriverProfileNotFoundException("Driver profile not found with identifier: " + id));
    }
}
