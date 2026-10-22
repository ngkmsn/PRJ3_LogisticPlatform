package com.logistics.shipment.dashboard.service;

import com.logistics.common.dto.AttentionItemDto;
import com.logistics.common.dto.DriverOperationalMetricsDto;
import com.logistics.common.dto.OperationsDashboardDto;
import com.logistics.common.dto.OrderMetricsDto;
import com.logistics.common.model.DriverAvailability;
import com.logistics.common.security.JwtTokenProvider;
import com.logistics.shipment.dashboard.client.OrderMetricsClient;
import com.logistics.shipment.eligibility.client.DriverClient;
import com.logistics.shipment.entity.Assignment;
import com.logistics.shipment.entity.DriverLocation;
import com.logistics.shipment.exception.AccessDeniedException;
import com.logistics.shipment.exception.BadCredentialsException;
import com.logistics.shipment.model.AssignmentStatus;
import com.logistics.shipment.repository.AssignmentRepository;
import com.logistics.shipment.repository.DriverLocationRepository;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OperationsDashboardServiceTest {

    @Mock
    private OrderMetricsClient orderMetricsClient;

    @Mock
    private DriverClient driverClient;

    @Mock
    private AssignmentRepository assignmentRepository;

    @Mock
    private DriverLocationRepository driverLocationRepository;

    @Mock
    private PanacheQuery<Assignment> activeAssignmentsQuery;

    private JwtTokenProvider jwtTokenProvider;
    private DefaultOperationsDashboardService dashboardService;

    private String dispatcherToken;
    private String adminToken;
    private String customerToken;
    private String driverToken;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                86400000L,
                "logistics-platform"
        );

        dashboardService = new DefaultOperationsDashboardService(
                orderMetricsClient,
                driverClient,
                assignmentRepository,
                driverLocationRepository,
                jwtTokenProvider,
                15L
        );

        dispatcherToken = "Bearer " + jwtTokenProvider.generateToken("disp-1", "dispatcher1", "disp@test.com", "DISPATCHER");
        adminToken = "Bearer " + jwtTokenProvider.generateToken("admin-1", "admin1", "admin@test.com", "ADMIN");
        customerToken = "Bearer " + jwtTokenProvider.generateToken("cust-1", "customer1", "cust@test.com", "CUSTOMER");
        driverToken = "Bearer " + jwtTokenProvider.generateToken("driver-1", "driver1", "driver@test.com", "DRIVER");
    }

    @Test
    @DisplayName("PB-048: Dispatcher successfully retrieves aggregated dashboard with order and driver metrics")
    void getDashboard_dispatcherSuccess() {
        // Order metrics from order-service
        OrderMetricsDto orderDto = new OrderMetricsDto();
        orderDto.setTotalOrders(50L);
        orderDto.setPendingOrders(10L);
        orderDto.setAssignedOrders(15L);
        orderDto.setInTransitOrders(10L);
        orderDto.setDeliveredOrders(15L);
        orderDto.setFailedOrders(0L);

        when(orderMetricsClient.getOrderMetrics(dispatcherToken)).thenReturn(Optional.of(orderDto));

        // Driver metrics from user-service
        DriverOperationalMetricsDto driverDto = new DriverOperationalMetricsDto();
        driverDto.setTotalDrivers(10L);
        driverDto.setAvailableDrivers(8L);
        driverDto.setUnavailableDrivers(2L);

        when(driverClient.getDriverMetrics(dispatcherToken)).thenReturn(Optional.of(driverDto));

        // Local assignment repository state
        when(assignmentRepository.count(eq("status = ?1"), eq(AssignmentStatus.UNASSIGNED))).thenReturn(2L);
        when(assignmentRepository.count(eq("status = ?1"), eq(AssignmentStatus.FAILED))).thenReturn(1L);
        when(assignmentRepository.count(eq("status = ?1"), eq(AssignmentStatus.ASSIGNED))).thenReturn(2L);
        when(assignmentRepository.count(eq("status = ?1"), eq(AssignmentStatus.IN_TRANSIT))).thenReturn(1L);

        Assignment active1 = new Assignment();
        active1.setDriverId("driver-01");
        active1.setStatus(AssignmentStatus.ASSIGNED);

        Assignment active2 = new Assignment();
        active2.setDriverId("driver-02");
        active2.setStatus(AssignmentStatus.IN_TRANSIT);

        when(assignmentRepository.find(eq("status in (?1, ?2)"), eq(AssignmentStatus.ASSIGNED), eq(AssignmentStatus.IN_TRANSIT)))
                .thenReturn(activeAssignmentsQuery);
        when(activeAssignmentsQuery.list()).thenReturn(List.of(active1, active2));

        // GPS metrics
        when(driverLocationRepository.countActiveSince(any())).thenReturn(6L);
        when(driverLocationRepository.countStaleBefore(any())).thenReturn(2L);

        // driver-01 has fresh GPS, driver-02 has stale GPS (> 30 mins)
        DriverLocation freshLoc = new DriverLocation();
        freshLoc.setDriverId("driver-01");
        freshLoc.setTimestamp(Instant.now().minus(5, ChronoUnit.MINUTES));

        DriverLocation staleLoc = new DriverLocation();
        staleLoc.setDriverId("driver-02");
        staleLoc.setTimestamp(Instant.now().minus(45, ChronoUnit.MINUTES));

        when(driverLocationRepository.findByDriverId("driver-01")).thenReturn(Optional.of(freshLoc));
        when(driverLocationRepository.findByDriverId("driver-02")).thenReturn(Optional.of(staleLoc));

        OperationsDashboardDto dashboard = dashboardService.getDashboard(dispatcherToken, false);

        assertThat(dashboard).isNotNull();
        assertThat(dashboard.getOrderMetrics().getTotalOrders()).isEqualTo(50L);
        assertThat(dashboard.getOrderMetrics().getFailedOrders()).isEqualTo(1L); // reconciled from assignment status

        // Driver metrics verification (no double counting)
        assertThat(dashboard.getDriverMetrics().getTotalDrivers()).isEqualTo(10L);
        assertThat(dashboard.getDriverMetrics().getBusyDrivers()).isEqualTo(2L);
        assertThat(dashboard.getDriverMetrics().getAvailableDrivers()).isEqualTo(6L); // 8 base - 2 busy = 6
        assertThat(dashboard.getDriverMetrics().getActiveGpsDrivers()).isEqualTo(6L);
        assertThat(dashboard.getDriverMetrics().getStaleGpsDrivers()).isEqualTo(2L);

        // Attention items
        assertThat(dashboard.getAttentionItems()).hasSize(3);
        assertThat(dashboard.getAttentionItems())
                .extracting(AttentionItemDto::getType)
                .containsExactlyInAnyOrder("UNASSIGNED_ORDER", "FAILED_ORDER", "STALE_GPS");
    }

    @Test
    @DisplayName("PB-048: Gracefully degrades when downstream services are unavailable")
    void getDashboard_downstreamUnavailable_gracefulFallback() {
        when(orderMetricsClient.getOrderMetrics(dispatcherToken)).thenReturn(Optional.empty());
        when(driverClient.getDriverMetrics(dispatcherToken)).thenReturn(Optional.empty());
        when(driverClient.getAllDrivers(dispatcherToken)).thenReturn(List.of());

        when(assignmentRepository.count(eq("status = ?1"), eq(AssignmentStatus.UNASSIGNED))).thenReturn(1L);
        when(assignmentRepository.count(eq("status = ?1"), eq(AssignmentStatus.FAILED))).thenReturn(0L);
        when(assignmentRepository.count(eq("status = ?1"), eq(AssignmentStatus.ASSIGNED))).thenReturn(1L);
        when(assignmentRepository.count(eq("status = ?1"), eq(AssignmentStatus.IN_TRANSIT))).thenReturn(0L);
        when(assignmentRepository.count(eq("status = ?1"), eq(AssignmentStatus.SEARCHING_DRIVER))).thenReturn(0L);
        when(assignmentRepository.count(eq("status = ?1"), eq(AssignmentStatus.DELIVERED))).thenReturn(0L);

        when(assignmentRepository.find(eq("status in (?1, ?2)"), eq(AssignmentStatus.ASSIGNED), eq(AssignmentStatus.IN_TRANSIT)))
                .thenReturn(activeAssignmentsQuery);
        when(activeAssignmentsQuery.list()).thenReturn(List.of());

        when(driverLocationRepository.countActiveSince(any())).thenReturn(0L);
        when(driverLocationRepository.countStaleBefore(any())).thenReturn(0L);

        OperationsDashboardDto dashboard = dashboardService.getDashboard(dispatcherToken, true);

        assertThat(dashboard).isNotNull();
        assertThat(dashboard.getOrderMetrics()).isNotNull();
        assertThat(dashboard.getOrderMetrics().getTotalOrders()).isEqualTo(2L);
        assertThat(dashboard.getDriverMetrics()).isNotNull();
        assertThat(dashboard.getAttentionItems()).hasSize(1);
        assertThat(dashboard.getAttentionItems().get(0).getType()).isEqualTo("UNASSIGNED_ORDER");
    }

    @Test
    @DisplayName("PB-048: Customer role is denied access (403 Forbidden)")
    void getDashboard_customerRole_forbidden() {
        assertThatThrownBy(() -> dashboardService.getDashboard(customerToken, false))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Operations or Admin role required");
    }

    @Test
    @DisplayName("PB-048: Driver role is denied access (403 Forbidden)")
    void getDashboard_driverRole_forbidden() {
        assertThatThrownBy(() -> dashboardService.getDashboard(driverToken, false))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Operations or Admin role required");
    }

    @Test
    @DisplayName("PB-048: Missing or invalid token throws BadCredentialsException (401)")
    void getDashboard_invalidToken_unauthorized() {
        assertThatThrownBy(() -> dashboardService.getDashboard("Bearer invalid.token", false))
                .isInstanceOf(BadCredentialsException.class);

        assertThatThrownBy(() -> dashboardService.getDashboard(null, false))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("PB-048: Caching works within TTL and forceRefresh bypasses cache")
    void getDashboard_cacheAndForceRefresh() {
        when(orderMetricsClient.getOrderMetrics(dispatcherToken)).thenReturn(Optional.of(new OrderMetricsDto()));
        when(driverClient.getDriverMetrics(dispatcherToken)).thenReturn(Optional.of(new DriverOperationalMetricsDto()));

        when(assignmentRepository.find(eq("status in (?1, ?2)"), eq(AssignmentStatus.ASSIGNED), eq(AssignmentStatus.IN_TRANSIT)))
                .thenReturn(activeAssignmentsQuery);
        when(activeAssignmentsQuery.list()).thenReturn(List.of());

        // Call 1: uncached
        OperationsDashboardDto d1 = dashboardService.getDashboard(dispatcherToken, false);
        assertThat(d1).isNotNull();

        // Call 2: cached (verify orderMetricsClient called only once)
        OperationsDashboardDto d2 = dashboardService.getDashboard(dispatcherToken, false);
        assertThat(d2).isSameAs(d1);
        verify(orderMetricsClient, times(1)).getOrderMetrics(dispatcherToken);

        // Call 3: forceRefresh = true -> recomputes
        OperationsDashboardDto d3 = dashboardService.getDashboard(dispatcherToken, true);
        assertThat(d3).isNotNull();
        verify(orderMetricsClient, times(2)).getOrderMetrics(dispatcherToken);
    }
}
