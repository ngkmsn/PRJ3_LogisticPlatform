package com.logistics.shipment.dashboard.service;

import com.logistics.common.dto.AttentionItemDto;
import com.logistics.common.dto.DriverOperationalMetricsDto;
import com.logistics.common.dto.OperationsDashboardDto;
import com.logistics.common.dto.OrderMetricsDto;
import com.logistics.common.model.DriverAvailability;
import com.logistics.common.security.AuthPrincipal;
import com.logistics.common.security.JwtTokenProvider;
import com.logistics.common.security.Role;
import com.logistics.shipment.dashboard.client.OrderMetricsClient;
import com.logistics.shipment.eligibility.client.DriverClient;
import com.logistics.shipment.eligibility.model.EligibleDriver;
import com.logistics.shipment.entity.Assignment;
import com.logistics.shipment.entity.DriverLocation;
import com.logistics.shipment.exception.AccessDeniedException;
import com.logistics.shipment.exception.BadCredentialsException;
import com.logistics.shipment.model.AssignmentStatus;
import com.logistics.shipment.repository.AssignmentRepository;
import com.logistics.shipment.repository.DriverLocationRepository;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@ApplicationScoped
@DefaultBean
public class DefaultOperationsDashboardService implements OperationsDashboardService {

    private static final Logger log = LoggerFactory.getLogger(DefaultOperationsDashboardService.class);

    @Inject
    OrderMetricsClient orderMetricsClient;

    @Inject
    DriverClient driverClient;

    @Inject
    AssignmentRepository assignmentRepository;

    @Inject
    DriverLocationRepository driverLocationRepository;

    @Inject
    JwtTokenProvider jwtTokenProvider;

    @ConfigProperty(name = "logistics.dashboard.cache.ttl-seconds", defaultValue = "15")
    long cacheTtlSeconds;

    private final AtomicReference<CachedDashboard> cachedDashboardRef = new AtomicReference<>();

    public DefaultOperationsDashboardService() {
    }

    public DefaultOperationsDashboardService(OrderMetricsClient orderMetricsClient,
                                            DriverClient driverClient,
                                            AssignmentRepository assignmentRepository,
                                            DriverLocationRepository driverLocationRepository,
                                            JwtTokenProvider jwtTokenProvider,
                                            long cacheTtlSeconds) {
        this.orderMetricsClient = orderMetricsClient;
        this.driverClient = driverClient;
        this.assignmentRepository = assignmentRepository;
        this.driverLocationRepository = driverLocationRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.cacheTtlSeconds = cacheTtlSeconds;
    }

    @Override
    public OperationsDashboardDto getDashboard(String authHeader, boolean forceRefresh) {
        // 1. RBAC authentication validation
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Dashboard access rejected: Missing or malformed Authorization header");
            throw new BadCredentialsException("Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7).trim();
        if (!jwtTokenProvider.validateToken(token)) {
            log.warn("Dashboard access rejected: Invalid or expired token");
            throw new BadCredentialsException("Token is invalid or expired");
        }

        AuthPrincipal principal = AuthPrincipal.fromJwtToken(token, jwtTokenProvider);
        if (principal == null || (principal.getRole() != Role.DISPATCHER && principal.getRole() != Role.ADMIN)) {
            log.warn("Dashboard access denied: principal '{}' does not have DISPATCHER or ADMIN role",
                    principal != null ? principal.getUserId() : "null");
            throw new AccessDeniedException("Access denied: Operations or Admin role required");
        }

        Instant now = Instant.now();

        // 2. Cache evaluation
        CachedDashboard cached = cachedDashboardRef.get();
        if (!forceRefresh && cached != null && now.isBefore(cached.expiryTime)) {
            log.debug("Dashboard served from cache (expires at {})", cached.expiryTime);
            return cached.dto;
        }

        // 3. Aggregate Order Metrics
        OrderMetricsDto orderMetrics = null;
        try {
            orderMetrics = orderMetricsClient.getOrderMetrics(authHeader).orElse(null);
        } catch (Exception e) {
            log.warn("Failed to fetch order metrics from Order Service: {}", e.getMessage());
        }

        long unassignedCount = assignmentRepository.count("status = ?1", AssignmentStatus.UNASSIGNED);
        long failedCount = assignmentRepository.count("status = ?1", AssignmentStatus.FAILED);
        long assignedCount = assignmentRepository.count("status = ?1", AssignmentStatus.ASSIGNED);
        long inTransitCount = assignmentRepository.count("status = ?1", AssignmentStatus.IN_TRANSIT);

        if (orderMetrics == null) {
            // Graceful fallback with local assignment metrics
            orderMetrics = new OrderMetricsDto();
            orderMetrics.setPendingOrders(unassignedCount);
            orderMetrics.setSearchingDriverOrders(assignmentRepository.count("status = ?1", AssignmentStatus.SEARCHING_DRIVER));
            orderMetrics.setAssignedOrders(assignedCount);
            orderMetrics.setInTransitOrders(inTransitCount);
            orderMetrics.setFailedOrders(failedCount);
            orderMetrics.setDeliveredOrders(assignmentRepository.count("status = ?1", AssignmentStatus.DELIVERED));
            orderMetrics.setTotalOrders(unassignedCount + assignedCount + inTransitCount + failedCount);
            Map<String, Long> statusMap = new HashMap<>();
            statusMap.put("UNASSIGNED", unassignedCount);
            statusMap.put("ASSIGNED", assignedCount);
            statusMap.put("IN_TRANSIT", inTransitCount);
            statusMap.put("FAILED", failedCount);
            orderMetrics.setOrdersByStatus(statusMap);
        } else {
            // Reconcile failed orders from shipment-service authoritative assignment status
            if (failedCount > 0 && orderMetrics.getFailedOrders() == 0) {
                orderMetrics.setFailedOrders(failedCount);
                if (orderMetrics.getOrdersByStatus() != null) {
                    orderMetrics.getOrdersByStatus().put("FAILED", failedCount);
                }
            }
        }

        // 4. Aggregate Driver Operational Metrics without double counting
        DriverOperationalMetricsDto driverMetrics = null;
        try {
            driverMetrics = driverClient.getDriverMetrics(authHeader).orElse(null);
        } catch (Exception e) {
            log.warn("Failed to fetch driver metrics from User Service: {}", e.getMessage());
        }

        List<Assignment> activeAssignments = assignmentRepository.find(
                "status in (?1, ?2)", AssignmentStatus.ASSIGNED, AssignmentStatus.IN_TRANSIT).list();

        Set<String> busyDriverIds = activeAssignments.stream()
                .map(Assignment::getDriverId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());

        long busyDriversCount = busyDriverIds.size();

        Instant thirtyMinsAgo = now.minus(Duration.ofMinutes(30));
        long activeGps = driverLocationRepository.countActiveSince(thirtyMinsAgo);
        long staleGps = driverLocationRepository.countStaleBefore(thirtyMinsAgo);

        if (driverMetrics == null) {
            // Fallback: try getAllDrivers or use active assignment counts
            driverMetrics = new DriverOperationalMetricsDto();
            try {
                List<EligibleDriver> allDrivers = driverClient.getAllDrivers(authHeader);
                if (allDrivers != null && !allDrivers.isEmpty()) {
                    driverMetrics.setTotalDrivers(allDrivers.size());
                    long unavailableCount = allDrivers.stream()
                            .filter(d -> d.getAvailability() == DriverAvailability.UNAVAILABLE)
                            .count();
                    driverMetrics.setUnavailableDrivers(unavailableCount);
                    long totalAvail = allDrivers.size() - unavailableCount;
                    driverMetrics.setBusyDrivers(busyDriversCount);
                    driverMetrics.setAvailableDrivers(Math.max(0, totalAvail - busyDriversCount));
                } else {
                    driverMetrics.setBusyDrivers(busyDriversCount);
                    driverMetrics.setTotalDrivers(busyDriversCount);
                }
            } catch (Exception e) {
                log.warn("Fallback driver list query failed: {}", e.getMessage());
                driverMetrics.setBusyDrivers(busyDriversCount);
                driverMetrics.setTotalDrivers(busyDriversCount);
            }
        } else {
            // Authoritative user service gives base availability;
            // Cross-reference with active assignments in shipment-service to calculate actual BUSY vs AVAILABLE without double-counting
            long baseAvailable = driverMetrics.getAvailableDrivers();
            driverMetrics.setBusyDrivers(busyDriversCount);
            driverMetrics.setAvailableDrivers(Math.max(0, baseAvailable - busyDriversCount));
        }

        driverMetrics.setActiveGpsDrivers(activeGps);
        driverMetrics.setStaleGpsDrivers(staleGps);

        // 5. Generate Operational Attention Items
        List<AttentionItemDto> attentionItems = new ArrayList<>();

        if (unassignedCount > 0) {
            attentionItems.add(new AttentionItemDto(
                    "UNASSIGNED_ORDER",
                    "HIGH",
                    unassignedCount + " unassigned orders awaiting driver assignment",
                    "Orders waiting in UNASSIGNED status need manual driver allocation."
            ));
        }

        if (failedCount > 0) {
            attentionItems.add(new AttentionItemDto(
                    "FAILED_ORDER",
                    "CRITICAL",
                    failedCount + " failed orders requiring operational review",
                    "Deliveries marked as FAILED require investigation or reassignment."
            ));
        }

        long busyDriversWithStaleGps = 0;
        for (String driverId : busyDriverIds) {
            Optional<DriverLocation> loc = driverLocationRepository.findByDriverId(driverId)
                    .or(() -> driverLocationRepository.findByUserId(driverId));
            if (loc.isEmpty() || loc.get().getTimestamp().isBefore(thirtyMinsAgo)) {
                busyDriversWithStaleGps++;
            }
        }

        if (busyDriversWithStaleGps > 0) {
            attentionItems.add(new AttentionItemDto(
                    "STALE_GPS",
                    "MEDIUM",
                    busyDriversWithStaleGps + " active drivers have stale GPS updates (> 30m)",
                    "Drivers currently assigned to orders have not sent GPS pings within 30 minutes."
            ));
        }

        // 6. Build final OperationsDashboardDto
        OperationsDashboardDto dashboardDto = new OperationsDashboardDto();
        dashboardDto.setOrderMetrics(orderMetrics);
        dashboardDto.setDriverMetrics(driverMetrics);
        dashboardDto.setAttentionItems(attentionItems);
        dashboardDto.setGeneratedAt(now);

        // 7. Store in cache
        Instant expiryTime = now.plusSeconds(Math.max(1, cacheTtlSeconds));
        cachedDashboardRef.set(new CachedDashboard(dashboardDto, expiryTime));

        return dashboardDto;
    }

    private static class CachedDashboard {
        final OperationsDashboardDto dto;
        final Instant expiryTime;

        CachedDashboard(OperationsDashboardDto dto, Instant expiryTime) {
            this.dto = dto;
            this.expiryTime = expiryTime;
        }
    }
}
