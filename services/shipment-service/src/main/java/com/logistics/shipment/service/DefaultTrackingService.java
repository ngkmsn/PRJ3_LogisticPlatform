package com.logistics.shipment.service;

import com.logistics.common.security.AuthPrincipal;
import com.logistics.common.security.JwtTokenProvider;
import com.logistics.common.security.Role;
import com.logistics.shipment.dto.UpdateDriverLocationRequest;
import com.logistics.shipment.eligibility.client.DriverClient;
import com.logistics.shipment.eligibility.model.EligibleDriver;
import com.logistics.shipment.entity.DriverLocation;
import com.logistics.shipment.exception.AccessDeniedException;
import com.logistics.shipment.exception.BadCredentialsException;
import com.logistics.shipment.exception.TrackingServiceException;
import com.logistics.shipment.repository.DriverLocationRepository;
import com.logistics.common.dto.OrderTrackingDto;
import com.logistics.shipment.entity.Assignment;
import com.logistics.shipment.exception.AssignmentNotFoundException;
import com.logistics.shipment.model.AssignmentStatus;
import com.logistics.shipment.repository.AssignmentRepository;
import com.logistics.common.dto.DriverMonitoringDto;
import com.logistics.common.model.DriverAvailability;
import com.logistics.shipment.dto.PagedResponse;
import com.logistics.shipment.exception.DriverDataProviderUnavailableException;
import com.logistics.shipment.exception.DriverNotFoundException;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

@ApplicationScoped
@DefaultBean
public class DefaultTrackingService implements TrackingService {

    private static final Logger log = LoggerFactory.getLogger(DefaultTrackingService.class);

    @Inject
    DriverLocationRepository driverLocationRepository;

    @Inject
    JwtTokenProvider jwtTokenProvider;

    @Inject
    DriverClient driverClient;

    @Inject
    AssignmentRepository assignmentRepository;

    @Override
    @Transactional
    public DriverLocation recordDriverLocation(String authHeader, UpdateDriverLocationRequest request) {
        AuthPrincipal principal = resolveAuthenticatedDriver(authHeader, "send GPS location");
        String userId = principal.getUserId();
        String driverId = resolveDriverProfileId(userId);

        // 1. Validate request body
        if (request == null) {
            throw new IllegalArgumentException("Location update request cannot be null");
        }

        // 2. Validate coordinates
        Double lat = request.getLatitude();
        if (lat == null) {
            throw new IllegalArgumentException("Latitude is required");
        }
        if (Double.isNaN(lat) || Double.isInfinite(lat) || lat < -90.0 || lat > 90.0) {
            throw new IllegalArgumentException("Latitude must be between -90.0 and 90.0 degrees");
        }

        Double lon = request.getLongitude();
        if (lon == null) {
            throw new IllegalArgumentException("Longitude is required");
        }
        if (Double.isNaN(lon) || Double.isInfinite(lon) || lon < -180.0 || lon > 180.0) {
            throw new IllegalArgumentException("Longitude must be between -180.0 and 180.0 degrees");
        }

        // 3. Validate timestamp
        Instant now = Instant.now();
        Instant locationTime = request.getTimestamp() != null ? request.getTimestamp() : now;
        // Clock skew tolerance: reject if more than 5 minutes in the future
        if (locationTime.isAfter(now.plusSeconds(300))) {
            throw new IllegalArgumentException("Location timestamp cannot be in the future");
        }

        // 4. Validate optional metrics
        if (request.getAccuracy() != null) {
            Double acc = request.getAccuracy();
            if (Double.isNaN(acc) || Double.isInfinite(acc) || acc < 0) {
                throw new IllegalArgumentException("Accuracy must be greater than or equal to 0");
            }
        }
        if (request.getSpeed() != null) {
            Double spd = request.getSpeed();
            if (Double.isNaN(spd) || Double.isInfinite(spd) || spd < 0) {
                throw new IllegalArgumentException("Speed must be greater than or equal to 0");
            }
        }
        if (request.getBearing() != null) {
            Double brg = request.getBearing();
            if (Double.isNaN(brg) || Double.isInfinite(brg) || brg < 0 || brg > 360.0) {
                throw new IllegalArgumentException("Bearing must be between 0.0 and 360.0 degrees");
            }
        }

        try {
            // 5. Query existing location to guard against stale updates
            Optional<DriverLocation> existingOpt = driverLocationRepository.findByDriverId(driverId);
            if (existingOpt.isEmpty()) {
                existingOpt = driverLocationRepository.findByUserId(userId);
            }

            DriverLocation location;
            if (existingOpt.isPresent()) {
                DriverLocation existing = existingOpt.get();
                // Stale timestamp check: reject incoming timestamp if older than current recorded location
                if (existing.getTimestamp() != null && locationTime.isBefore(existing.getTimestamp())) {
                    log.warn("Rejected stale GPS location for driver '{}': incoming timestamp {} is older than current {}",
                            driverId, locationTime, existing.getTimestamp());
                    throw new IllegalArgumentException("Stale GPS location: incoming timestamp " + locationTime
                            + " is older than current location timestamp " + existing.getTimestamp());
                }

                existing.setLatitude(lat);
                existing.setLongitude(lon);
                existing.setAccuracy(request.getAccuracy());
                existing.setSpeed(request.getSpeed());
                existing.setBearing(request.getBearing());
                if (request.getOrderId() != null && !request.getOrderId().trim().isEmpty()) {
                    existing.setOrderId(request.getOrderId().trim());
                }
                existing.setTimestamp(locationTime);
                existing.setUpdatedAt(now);
                driverLocationRepository.persist(existing);
                location = existing;
            } else {
                location = new DriverLocation(
                        UUID.randomUUID().toString(),
                        driverId,
                        userId,
                        lat,
                        lon,
                        request.getAccuracy(),
                        request.getSpeed(),
                        request.getBearing(),
                        request.getOrderId() != null && !request.getOrderId().trim().isEmpty() ? request.getOrderId().trim() : null,
                        locationTime,
                        now,
                        now
                );
                driverLocationRepository.persist(location);
            }

            log.info("Driver '{}' successfully updated GPS location: lat={}, lon={}, time={}",
                    driverId, lat, lon, locationTime);
            return location;

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to record driver GPS location for driver '{}': {}", driverId, e.getMessage(), e);
            throw new TrackingServiceException("Database error saving driver GPS location: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<DriverLocation> getCurrentDriverLocation(String authHeader) {
        AuthPrincipal principal = resolveAuthenticatedDriver(authHeader, "view current GPS location");
        String userId = principal.getUserId();
        String driverId = resolveDriverProfileId(userId);

        Optional<DriverLocation> location = driverLocationRepository.findByDriverId(driverId);
        if (location.isEmpty()) {
            location = driverLocationRepository.findByUserId(userId);
        }
        return location;
    }

    @Override
    public Optional<DriverLocation> getDriverLocation(String authHeader, String driverId) {
        resolveAuthenticatedUser(authHeader, "view driver GPS location");

        if (driverId == null || driverId.trim().isEmpty()) {
            throw new IllegalArgumentException("driverId must not be null or empty");
        }

        String targetId = driverId.trim();
        Optional<DriverLocation> location = driverLocationRepository.findByDriverId(targetId);
        if (location.isEmpty()) {
            location = driverLocationRepository.findByUserId(targetId);
        }
        return location;
    }

    private AuthPrincipal resolveAuthenticatedDriver(String authHeader, String action) {
        AuthPrincipal principal = resolveAuthenticatedUser(authHeader, action);
        if (principal.getRole() != Role.DRIVER && principal.getRole() != Role.ADMIN) {
            log.warn("Access denied: caller role '{}' is not DRIVER or ADMIN for action: {}",
                    principal.getRole(), action);
            throw new AccessDeniedException("Access denied: Only drivers are permitted to " + action);
        }
        return principal;
    }

    private AuthPrincipal resolveAuthenticatedUser(String authHeader, String action) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Authentication failed: missing or invalid Authorization header");
            throw new BadCredentialsException("Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7).trim();
        if (jwtTokenProvider == null || !jwtTokenProvider.validateToken(token)) {
            log.warn("Authentication failed: token is invalid or expired");
            throw new BadCredentialsException("Token is invalid or expired");
        }

        AuthPrincipal principal = AuthPrincipal.fromJwtToken(token, jwtTokenProvider);
        if (principal == null) {
            throw new BadCredentialsException("Token identity could not be verified");
        }
        return principal;
    }

    @Override
    public OrderTrackingDto getOrderTracking(String authHeader, String orderId) {
        return getOrderTracking(authHeader, orderId, false);
    }

    @Override
    public OrderTrackingDto getOrderTracking(String authHeader, String orderId, boolean allowStale) {
        AuthPrincipal principal = resolveAuthenticatedUser(authHeader, "view order driver tracking");

        if (orderId == null || orderId.trim().isEmpty()) {
            throw new IllegalArgumentException("orderId must not be null or empty");
        }

        String targetOrderId = orderId.trim();

        try {
            // 1. Look up assignment
            Assignment assignment = assignmentRepository.findByOrderId(targetOrderId)
                    .orElseThrow(() -> new AssignmentNotFoundException("Assignment/Order not found: " + targetOrderId));

            // 2. Customer ownership verification
            if (principal.getRole() == Role.CUSTOMER) {
                if (!principal.getUserId().equals(assignment.getCustomerId())) {
                    log.warn("Access denied: Customer '{}' does not own order '{}' (owner: '{}')",
                            principal.getUserId(), targetOrderId, assignment.getCustomerId());
                    throw new AccessDeniedException("Access denied: You do not own this order");
                }
            } else if (principal.getRole() == Role.DRIVER) {
                // Driver can only view if assigned to this order or ADMIN
                String driverProfileId = resolveDriverProfileId(principal.getUserId());
                if (!principal.getUserId().equals(assignment.getDriverId()) && !driverProfileId.equals(assignment.getDriverId())) {
                    log.warn("Access denied: Driver '{}' is not assigned to order '{}'",
                            principal.getUserId(), targetOrderId);
                    throw new AccessDeniedException("Access denied: You are not assigned to this order");
                }
            }

            // 3. Verify order trackable state
            AssignmentStatus status = assignment.getStatus();
            if (status == AssignmentStatus.PENDING || status == AssignmentStatus.SEARCHING_DRIVER || status == AssignmentStatus.UNASSIGNED) {
                throw new IllegalStateException("Order is not in a trackable state: Driver has not been assigned yet (current status: " + status + ")");
            }
            if (status == AssignmentStatus.DELIVERED) {
                throw new IllegalStateException("Order is not in a trackable state: Order has already been delivered");
            }
            if (status == AssignmentStatus.CANCELLED) {
                throw new IllegalStateException("Order is not in a trackable state: Order has been cancelled");
            }
            if (status == AssignmentStatus.FAILED) {
                throw new IllegalStateException("Order is not in a trackable state: Order delivery failed (status: FAILED)");
            }

            // 4. Verify driver assignment
            String driverId = assignment.getDriverId();
            if (driverId == null || driverId.trim().isEmpty()) {
                throw new IllegalStateException("Order is not in a trackable state: No driver assigned to order: " + targetOrderId);
            }

            // 5. Query Driver GPS location
            Optional<DriverLocation> locationOpt = driverLocationRepository.findByDriverId(driverId);
            if (locationOpt.isEmpty()) {
                locationOpt = driverLocationRepository.findByUserId(driverId);
            }
            if (locationOpt.isEmpty()) {
                log.warn("Tracking requested for order '{}' but assigned driver '{}' has no recorded GPS location",
                        targetOrderId, driverId);
                throw new IllegalStateException("No GPS location recorded for the assigned driver yet");
            }

            DriverLocation location = locationOpt.get();
            Instant locationTime = location.getTimestamp() != null ? location.getTimestamp() : location.getUpdatedAt();
            long ageMinutes = locationTime != null ? Duration.between(locationTime, Instant.now()).toMinutes() : 0;
            boolean isStale = ageMinutes >= 30; // 30 minutes threshold

            if (isStale && !allowStale) {
                log.warn("Tracking rejected for order '{}': driver '{}' location is stale ({} minutes old)",
                        targetOrderId, driverId, ageMinutes);
                throw new IllegalStateException("Driver GPS location is stale: last updated " + ageMinutes + " minutes ago (exceeds 30 minute freshness threshold)");
            }

            OrderTrackingDto dto = new OrderTrackingDto();
            dto.setOrderId(assignment.getOrderId());
            dto.setOrderNumber(assignment.getOrderNumber());
            dto.setCustomerId(assignment.getCustomerId());
            dto.setAssignmentStatus(status.name());
            dto.setOrderStatus(status.name());
            dto.setDriverId(driverId);
            dto.setLatitude(location.getLatitude());
            dto.setLongitude(location.getLongitude());
            dto.setUpdatedAt(locationTime);
            dto.setAccuracy(location.getAccuracy());
            dto.setSpeed(location.getSpeed());
            dto.setBearing(location.getBearing());
            dto.setLiveTrackingActive(true);
            dto.setStale(isStale);
            dto.setLocationAgeMinutes(ageMinutes);
            dto.setStaleWarning(isStale ? ("Driver GPS location is stale: last updated " + ageMinutes + " minutes ago") : null);
            dto.setMessage("Driver location retrieved successfully");

            log.info("Successfully retrieved tracking info for order '{}': driver '{}', lat={}, lon={}, ageMinutes={}",
                    targetOrderId, driverId, location.getLatitude(), location.getLongitude(), ageMinutes);

            return dto;

        } catch (AssignmentNotFoundException | AccessDeniedException | IllegalStateException | IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to retrieve tracking for order '{}': {}", targetOrderId, e.getMessage(), e);
            throw new TrackingServiceException("Database error retrieving order tracking: " + e.getMessage(), e);
        }
    }

    private String resolveDriverProfileId(String userId) {
        if (driverClient != null) {
            try {
                Optional<EligibleDriver> driverOpt = driverClient.findDriverByUserId(userId);
                if (driverOpt.isEmpty()) {
                    driverOpt = driverClient.findDriverById(userId);
                }
                if (driverOpt.isPresent()) {
                    return driverOpt.get().getDriverId();
                }
            } catch (Exception e) {
                log.warn("Failed to resolve driver profile from driverClient: {}", e.getMessage());
            }
        }
        return userId;
    }

    @Override
    public PagedResponse<DriverMonitoringDto> getDriversMonitoring(
            String authHeader,
            int page,
            int size,
            String search,
            String status,
            Boolean hasActiveOrder,
            Boolean hasGps) {
        // 1. RBAC check: Operations (DISPATCHER, ADMIN) only
        resolveOperationsPrincipal(authHeader, "view driver monitoring data");

        // 2. Fetch driver profiles from authoritative provider (DriverClient)
        List<EligibleDriver> drivers;
        try {
            if (driverClient == null) {
                throw new DriverDataProviderUnavailableException("Driver client is not configured");
            }
            drivers = driverClient.getAllDrivers(authHeader);
        } catch (DriverDataProviderUnavailableException | AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to query drivers from provider service: {}", e.getMessage(), e);
            throw new DriverDataProviderUnavailableException("Driver provider service is currently unavailable: " + e.getMessage(), e);
        }

        if (drivers == null) {
            drivers = Collections.emptyList();
        }

        // 3. Query active assignments in shipment-service
        List<Assignment> activeAssignments = assignmentRepository.findAllActiveAssignments();
        Map<String, Assignment> assignmentByDriver = new HashMap<>();
        if (activeAssignments != null) {
            for (Assignment a : activeAssignments) {
                if (a.getDriverId() != null && !a.getDriverId().isBlank()) {
                    assignmentByDriver.putIfAbsent(a.getDriverId().trim(), a);
                }
            }
        }

        // 4. Map each driver to DriverMonitoringDto
        Instant now = Instant.now();
        List<DriverMonitoringDto> dtoList = new ArrayList<>();

        for (EligibleDriver d : drivers) {
            DriverMonitoringDto dto = buildDriverMonitoringDto(d, assignmentByDriver, now);
            dtoList.add(dto);
        }

        // 5. Apply filters
        Stream<DriverMonitoringDto> stream = dtoList.stream();

        if (search != null && !search.isBlank()) {
            String s = search.trim().toLowerCase();
            stream = stream.filter(m ->
                    (m.getDriverId() != null && m.getDriverId().toLowerCase().contains(s)) ||
                    (m.getUserId() != null && m.getUserId().toLowerCase().contains(s)) ||
                    (m.getFullName() != null && m.getFullName().toLowerCase().contains(s)) ||
                    (m.getPhoneNumber() != null && m.getPhoneNumber().toLowerCase().contains(s)) ||
                    (m.getVehiclePlate() != null && m.getVehiclePlate().toLowerCase().contains(s)) ||
                    (m.getActiveOrderNumber() != null && m.getActiveOrderNumber().toLowerCase().contains(s))
            );
        }

        if (status != null && !status.isBlank()) {
            String st = status.trim().toUpperCase();
            stream = stream.filter(m ->
                    (m.getOperationalStatus() != null && m.getOperationalStatus().equalsIgnoreCase(st)) ||
                    (m.getAvailability() != null && m.getAvailability().name().equalsIgnoreCase(st))
            );
        }

        if (hasActiveOrder != null) {
            stream = stream.filter(m -> m.isHasActiveOrder() == hasActiveOrder);
        }

        if (hasGps != null) {
            stream = stream.filter(m -> m.isHasGps() == hasGps);
        }

        List<DriverMonitoringDto> filtered = stream.toList();

        // 6. Pagination
        int pageIndex = Math.max(0, page);
        int pageSize = (size <= 0 || size > 100) ? 10 : size;
        long totalElements = filtered.size();
        int start = pageIndex * pageSize;
        List<DriverMonitoringDto> paged;
        if (start >= totalElements) {
            paged = Collections.emptyList();
        } else {
            int end = Math.min((int) totalElements, start + pageSize);
            paged = filtered.subList(start, end);
        }

        return PagedResponse.of(paged, pageIndex, pageSize, totalElements);
    }

    @Override
    public DriverMonitoringDto getDriverMonitoringDetails(String authHeader, String driverId) {
        // 1. RBAC check: Operations (DISPATCHER, ADMIN) only
        resolveOperationsPrincipal(authHeader, "view driver monitoring details");

        if (driverId == null || driverId.trim().isEmpty()) {
            throw new IllegalArgumentException("Driver identifier is required");
        }

        EligibleDriver driver;
        try {
            if (driverClient == null) {
                throw new DriverDataProviderUnavailableException("Driver client is not configured");
            }
            Optional<EligibleDriver> opt = driverClient.findDriverById(driverId.trim());
            if (opt.isEmpty()) {
                opt = driverClient.findDriverByUserId(driverId.trim());
            }
            if (opt.isEmpty()) {
                throw new DriverNotFoundException("Driver not found with identifier: " + driverId);
            }
            driver = opt.get();
        } catch (DriverNotFoundException | AccessDeniedException | DriverDataProviderUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to query driver details from data provider: {}", e.getMessage(), e);
            throw new DriverDataProviderUnavailableException("Driver data provider is currently unavailable: " + e.getMessage(), e);
        }

        Map<String, Assignment> assignmentMap = new HashMap<>();
        Optional<Assignment> activeAssignment = assignmentRepository.findActiveByDriverId(driver.getDriverId(), driver.getUserId());
        activeAssignment.ifPresent(a -> {
            if (driver.getDriverId() != null) {
                assignmentMap.put(driver.getDriverId(), a);
            }
            if (driver.getUserId() != null) {
                assignmentMap.put(driver.getUserId(), a);
            }
        });

        return buildDriverMonitoringDto(driver, assignmentMap, Instant.now());
    }

    private DriverMonitoringDto buildDriverMonitoringDto(
            EligibleDriver d,
            Map<String, Assignment> assignmentByDriver,
            Instant now) {
        DriverMonitoringDto dto = new DriverMonitoringDto();
        dto.setDriverId(d.getDriverId());
        dto.setUserId(d.getUserId());
        dto.setFullName(d.getFullName());
        dto.setPhoneNumber(d.getPhoneNumber());
        dto.setVehicleType(d.getVehicleType());
        dto.setVehiclePlate(d.getVehiclePlate());
        dto.setStatus(d.getStatus());
        dto.setAvailability(d.getAvailability());

        // Match active assignment
        Assignment active = null;
        if (d.getDriverId() != null) {
            active = assignmentByDriver.get(d.getDriverId().trim());
        }
        if (active == null && d.getUserId() != null) {
            active = assignmentByDriver.get(d.getUserId().trim());
        }

        if (active != null) {
            dto.setHasActiveOrder(true);
            dto.setActiveOrderId(active.getOrderId());
            dto.setActiveOrderNumber(active.getOrderNumber());
            dto.setActiveOrderStatus(active.getStatus().name());
            dto.setOperationalStatus("BUSY");
        } else {
            dto.setHasActiveOrder(false);
            dto.setOperationalStatus(d.getAvailability() == DriverAvailability.AVAILABLE ? "AVAILABLE" : "UNAVAILABLE");
        }

        // Match GPS location
        Optional<DriverLocation> locOpt = Optional.empty();
        if (d.getDriverId() != null) {
            locOpt = driverLocationRepository.findByDriverId(d.getDriverId().trim());
        }
        if (locOpt.isEmpty() && d.getUserId() != null) {
            locOpt = driverLocationRepository.findByUserId(d.getUserId().trim());
        }

        if (locOpt.isPresent()) {
            DriverLocation loc = locOpt.get();
            dto.setHasGps(true);
            dto.setLatitude(loc.getLatitude());
            dto.setLongitude(loc.getLongitude());
            Instant updateTime = loc.getTimestamp() != null ? loc.getTimestamp() : loc.getUpdatedAt();
            dto.setGpsUpdatedAt(updateTime);
            dto.setAccuracy(loc.getAccuracy());
            dto.setSpeed(loc.getSpeed());
            dto.setBearing(loc.getBearing());

            long ageMinutes = updateTime != null ? Duration.between(updateTime, now).toMinutes() : 0;
            boolean isStale = ageMinutes >= 30;
            dto.setLocationStale(isStale);
            dto.setLocationAgeMinutes(ageMinutes);
            if (isStale) {
                dto.setStaleWarning("Driver GPS location is stale: last updated " + ageMinutes + " minutes ago");
            }
        } else {
            dto.setHasGps(false);
            dto.setLocationStale(false);
            dto.setLocationAgeMinutes(null);
            dto.setStaleWarning(null);
        }

        return dto;
    }

    private AuthPrincipal resolveOperationsPrincipal(String authHeader, String action) {
        AuthPrincipal principal = resolveAuthenticatedUser(authHeader, action);
        if (principal.getRole() != Role.DISPATCHER && principal.getRole() != Role.ADMIN) {
            log.warn("Access denied for {}: user '{}' with role '{}' attempted operations access",
                    action, principal.getUserId(), principal.getRole());
            throw new AccessDeniedException("Access denied: Operations (Dispatcher or Administrator) privileges required");
        }
        return principal;
    }
}

