package com.logistics.shipment.service;

import com.logistics.common.dto.DriverMonitoringDto;
import com.logistics.common.model.DriverAvailability;
import com.logistics.common.security.JwtTokenProvider;
import com.logistics.common.security.Role;
import com.logistics.shipment.dto.PagedResponse;
import com.logistics.shipment.eligibility.client.DriverClient;
import com.logistics.shipment.eligibility.model.EligibleDriver;
import com.logistics.shipment.entity.Assignment;
import com.logistics.shipment.entity.DriverLocation;
import com.logistics.shipment.exception.AccessDeniedException;
import com.logistics.shipment.exception.BadCredentialsException;
import com.logistics.shipment.exception.DriverDataProviderUnavailableException;
import com.logistics.shipment.exception.DriverNotFoundException;
import com.logistics.shipment.model.AssignmentStatus;
import com.logistics.shipment.repository.AssignmentRepository;
import com.logistics.shipment.repository.DriverLocationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationsDriverMonitoringServiceTest {

    @Mock
    DriverLocationRepository driverLocationRepository;

    @Mock
    DriverClient driverClient;

    @Mock
    AssignmentRepository assignmentRepository;

    @InjectMocks
    DefaultTrackingService trackingService;

    private JwtTokenProvider jwtTokenProvider;
    private String dispatcherToken;
    private String adminToken;
    private String driverToken;
    private String customerToken;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider("TestSecretKeyForOperationsDriverMonitoringTesting1234567890", 3600000L, "test-issuer");
        trackingService.jwtTokenProvider = jwtTokenProvider;

        dispatcherToken = "Bearer " + jwtTokenProvider.generateToken("disp-001", "dispatcher", "disp@test.com", Role.DISPATCHER.name());
        adminToken = "Bearer " + jwtTokenProvider.generateToken("admin-001", "admin", "admin@test.com", Role.ADMIN.name());
        driverToken = "Bearer " + jwtTokenProvider.generateToken("drv-001", "driver", "driver@test.com", Role.DRIVER.name());
        customerToken = "Bearer " + jwtTokenProvider.generateToken("cust-001", "customer", "customer@test.com", Role.CUSTOMER.name());
    }

    private List<EligibleDriver> createSampleDrivers() {
        EligibleDriver d1 = new EligibleDriver(
                "drv-01", "user-01", "Nguyen Van A", "0901111111",
                "MOTORBIKE", "29A-11111", DriverAvailability.AVAILABLE, "ACTIVE"
        );
        EligibleDriver d2 = new EligibleDriver(
                "drv-02", "user-02", "Tran Thi B", "0902222222",
                "VAN", "29B-22222", DriverAvailability.AVAILABLE, "ACTIVE"
        );
        EligibleDriver d3 = new EligibleDriver(
                "drv-03", "user-03", "Le Van C", "0903333333",
                "TRUCK", "29C-33333", DriverAvailability.UNAVAILABLE, "ACTIVE"
        );
        return List.of(d1, d2, d3);
    }

    @Test
    @DisplayName("Operations DISPATCHER successfully retrieves driver monitoring list")
    void getDriversMonitoring_asDispatcher_success() {
        List<EligibleDriver> drivers = createSampleDrivers();
        when(driverClient.getAllDrivers(dispatcherToken)).thenReturn(drivers);

        // d1 has active order
        Assignment assignment = new Assignment(
                UUID.randomUUID().toString(), "ord-101", "ORD-101", "cust-01",
                "Recipient A", "0912345678", "Hanoi", BigDecimal.valueOf(100000),
                AssignmentStatus.IN_TRANSIT, "drv-01", Instant.now(), "Note", Instant.now(), Instant.now()
        );
        when(assignmentRepository.findAllActiveAssignments()).thenReturn(List.of(assignment));

        // d1 has fresh GPS
        DriverLocation loc1 = new DriverLocation(
                UUID.randomUUID().toString(), "drv-01", "user-01",
                21.0285, 105.8542, 5.0, 25.0, 90.0,
                "ord-101", Instant.now().minus(2, ChronoUnit.MINUTES), Instant.now(), Instant.now()
        );
        when(driverLocationRepository.findByDriverId("drv-01")).thenReturn(Optional.of(loc1));

        // d2 has stale GPS (35 mins old)
        DriverLocation loc2 = new DriverLocation(
                UUID.randomUUID().toString(), "drv-02", "user-02",
                21.0300, 105.8500, 10.0, 0.0, 0.0,
                null, Instant.now().minus(35, ChronoUnit.MINUTES), Instant.now(), Instant.now()
        );
        when(driverLocationRepository.findByDriverId("drv-02")).thenReturn(Optional.of(loc2));

        // d3 has no GPS
        when(driverLocationRepository.findByDriverId("drv-03")).thenReturn(Optional.empty());

        PagedResponse<DriverMonitoringDto> response = trackingService.getDriversMonitoring(
                dispatcherToken, 0, 10, null, null, null, null
        );

        assertThat(response).isNotNull();
        assertThat(response.getTotalElements()).isEqualTo(3);
        assertThat(response.getContent()).hasSize(3);

        // Inspect d1 (Active order, fresh GPS)
        DriverMonitoringDto dto1 = response.getContent().stream()
                .filter(d -> "drv-01".equals(d.getDriverId()))
                .findFirst().orElseThrow();
        assertThat(dto1.getFullName()).isEqualTo("Nguyen Van A");
        assertThat(dto1.isHasActiveOrder()).isTrue();
        assertThat(dto1.getActiveOrderId()).isEqualTo("ord-101");
        assertThat(dto1.getActiveOrderStatus()).isEqualTo("IN_TRANSIT");
        assertThat(dto1.getOperationalStatus()).isEqualTo("BUSY");
        assertThat(dto1.isHasGps()).isTrue();
        assertThat(dto1.getLatitude()).isEqualTo(21.0285);
        assertThat(dto1.isLocationStale()).isFalse();

        // Inspect d2 (No active order, stale GPS)
        DriverMonitoringDto dto2 = response.getContent().stream()
                .filter(d -> "drv-02".equals(d.getDriverId()))
                .findFirst().orElseThrow();
        assertThat(dto2.isHasActiveOrder()).isFalse();
        assertThat(dto2.getOperationalStatus()).isEqualTo("AVAILABLE");
        assertThat(dto2.isHasGps()).isTrue();
        assertThat(dto2.isLocationStale()).isTrue();
        assertThat(dto2.getStaleWarning()).contains("Driver GPS location is stale");

        // Inspect d3 (No active order, no GPS)
        DriverMonitoringDto dto3 = response.getContent().stream()
                .filter(d -> "drv-03".equals(d.getDriverId()))
                .findFirst().orElseThrow();
        assertThat(dto3.isHasActiveOrder()).isFalse();
        assertThat(dto3.isHasGps()).isFalse();
        assertThat(dto3.getLatitude()).isNull();
        assertThat(dto3.getLongitude()).isNull();
        assertThat(dto3.isLocationStale()).isFalse();
    }

    @Test
    @DisplayName("Operations ADMIN successfully retrieves driver monitoring list")
    void getDriversMonitoring_asAdmin_success() {
        when(driverClient.getAllDrivers(adminToken)).thenReturn(Collections.emptyList());
        when(assignmentRepository.findAllActiveAssignments()).thenReturn(Collections.emptyList());

        PagedResponse<DriverMonitoringDto> response = trackingService.getDriversMonitoring(
                adminToken, 0, 10, null, null, null, null
        );

        assertThat(response).isNotNull();
        assertThat(response.getTotalElements()).isEqualTo(0);
    }

    @Test
    @DisplayName("Non-operations DRIVER role is rejected with 403 Forbidden")
    void getDriversMonitoring_asDriver_forbidden() {
        assertThatThrownBy(() -> trackingService.getDriversMonitoring(driverToken, 0, 10, null, null, null, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Operations (Dispatcher or Administrator) privileges required");
    }

    @Test
    @DisplayName("Non-operations CUSTOMER role is rejected with 403 Forbidden")
    void getDriversMonitoring_asCustomer_forbidden() {
        assertThatThrownBy(() -> trackingService.getDriversMonitoring(customerToken, 0, 10, null, null, null, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Operations (Dispatcher or Administrator) privileges required");
    }

    @Test
    @DisplayName("Unauthenticated caller missing token is rejected with 401 BadCredentialsException")
    void getDriversMonitoring_unauthenticated_rejected() {
        assertThatThrownBy(() -> trackingService.getDriversMonitoring(null, 0, 10, null, null, null, null))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Missing or invalid Authorization header");

        assertThatThrownBy(() -> trackingService.getDriversMonitoring("InvalidToken", 0, 10, null, null, null, null))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("Filtering by search query matches name, phone, or vehicle plate")
    void getDriversMonitoring_filterBySearch() {
        when(driverClient.getAllDrivers(dispatcherToken)).thenReturn(createSampleDrivers());
        when(assignmentRepository.findAllActiveAssignments()).thenReturn(Collections.emptyList());

        PagedResponse<DriverMonitoringDto> response = trackingService.getDriversMonitoring(
                dispatcherToken, 0, 10, "Tran Thi", null, null, null
        );

        assertThat(response.getTotalElements()).isEqualTo(1);
        assertThat(response.getContent().get(0).getFullName()).isEqualTo("Tran Thi B");
    }

    @Test
    @DisplayName("Filtering by status (BUSY) returns only busy drivers")
    void getDriversMonitoring_filterByStatus() {
        List<EligibleDriver> drivers = createSampleDrivers();
        when(driverClient.getAllDrivers(dispatcherToken)).thenReturn(drivers);

        Assignment assignment = new Assignment(
                UUID.randomUUID().toString(), "ord-101", "ORD-101", "cust-01",
                "Recipient A", "0912345678", "Hanoi", BigDecimal.valueOf(100000),
                AssignmentStatus.PICKED_UP, "drv-01", Instant.now(), "Note", Instant.now(), Instant.now()
        );
        when(assignmentRepository.findAllActiveAssignments()).thenReturn(List.of(assignment));

        PagedResponse<DriverMonitoringDto> response = trackingService.getDriversMonitoring(
                dispatcherToken, 0, 10, null, "BUSY", null, null
        );

        assertThat(response.getTotalElements()).isEqualTo(1);
        assertThat(response.getContent().get(0).getDriverId()).isEqualTo("drv-01");
        assertThat(response.getContent().get(0).getOperationalStatus()).isEqualTo("BUSY");
    }

    @Test
    @DisplayName("Filtering by hasActiveOrder and hasGps")
    void getDriversMonitoring_filterByFlags() {
        List<EligibleDriver> drivers = createSampleDrivers();
        when(driverClient.getAllDrivers(dispatcherToken)).thenReturn(drivers);
        when(assignmentRepository.findAllActiveAssignments()).thenReturn(Collections.emptyList());

        DriverLocation loc1 = new DriverLocation(
                UUID.randomUUID().toString(), "drv-01", "user-01",
                21.0285, 105.8542, 5.0, 25.0, 90.0,
                null, Instant.now(), Instant.now(), Instant.now()
        );
        when(driverLocationRepository.findByDriverId("drv-01")).thenReturn(Optional.of(loc1));
        when(driverLocationRepository.findByDriverId("drv-02")).thenReturn(Optional.empty());
        when(driverLocationRepository.findByDriverId("drv-03")).thenReturn(Optional.empty());

        // Filter hasGps = true
        PagedResponse<DriverMonitoringDto> withGps = trackingService.getDriversMonitoring(
                dispatcherToken, 0, 10, null, null, null, true
        );
        assertThat(withGps.getTotalElements()).isEqualTo(1);
        assertThat(withGps.getContent().get(0).getDriverId()).isEqualTo("drv-01");

        // Filter hasGps = false
        PagedResponse<DriverMonitoringDto> withoutGps = trackingService.getDriversMonitoring(
                dispatcherToken, 0, 10, null, null, null, false
        );
        assertThat(withoutGps.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("Pagination slices results and computes total pages")
    void getDriversMonitoring_pagination() {
        when(driverClient.getAllDrivers(dispatcherToken)).thenReturn(createSampleDrivers());
        when(assignmentRepository.findAllActiveAssignments()).thenReturn(Collections.emptyList());

        PagedResponse<DriverMonitoringDto> page0 = trackingService.getDriversMonitoring(
                dispatcherToken, 0, 2, null, null, null, null
        );
        assertThat(page0.getTotalElements()).isEqualTo(3);
        assertThat(page0.getTotalPages()).isEqualTo(2);
        assertThat(page0.getContent()).hasSize(2);

        PagedResponse<DriverMonitoringDto> page1 = trackingService.getDriversMonitoring(
                dispatcherToken, 1, 2, null, null, null, null
        );
        assertThat(page1.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("When DriverClient / User Service is unavailable, throws DriverDataProviderUnavailableException")
    void getDriversMonitoring_providerUnavailable_throwsException() {
        when(driverClient.getAllDrivers(anyString()))
                .thenThrow(new DriverDataProviderUnavailableException("User service connection timeout"));

        assertThatThrownBy(() -> trackingService.getDriversMonitoring(dispatcherToken, 0, 10, null, null, null, null))
                .isInstanceOf(DriverDataProviderUnavailableException.class)
                .hasMessageContaining("User service connection timeout");
    }

    @Test
    @DisplayName("getDriverMonitoringDetails returns single driver details for operations")
    void getDriverMonitoringDetails_success() {
        EligibleDriver driver = new EligibleDriver(
                "drv-01", "user-01", "Nguyen Van A", "0901111111",
                "MOTORBIKE", "29A-11111", DriverAvailability.AVAILABLE, "ACTIVE"
        );
        when(driverClient.findDriverById("drv-01")).thenReturn(Optional.of(driver));

        Assignment assignment = new Assignment(
                UUID.randomUUID().toString(), "ord-101", "ORD-101", "cust-01",
                "Recipient A", "0912345678", "Hanoi", BigDecimal.valueOf(100000),
                AssignmentStatus.IN_TRANSIT, "drv-01", Instant.now(), "Note", Instant.now(), Instant.now()
        );
        when(assignmentRepository.findActiveByDriverId("drv-01", "user-01")).thenReturn(Optional.of(assignment));

        DriverLocation loc = new DriverLocation(
                UUID.randomUUID().toString(), "drv-01", "user-01",
                21.0285, 105.8542, 5.0, 25.0, 90.0,
                "ord-101", Instant.now().minus(5, ChronoUnit.MINUTES), Instant.now(), Instant.now()
        );
        when(driverLocationRepository.findByDriverId("drv-01")).thenReturn(Optional.of(loc));

        DriverMonitoringDto dto = trackingService.getDriverMonitoringDetails(dispatcherToken, "drv-01");

        assertThat(dto).isNotNull();
        assertThat(dto.getDriverId()).isEqualTo("drv-01");
        assertThat(dto.getFullName()).isEqualTo("Nguyen Van A");
        assertThat(dto.isHasActiveOrder()).isTrue();
        assertThat(dto.getActiveOrderId()).isEqualTo("ord-101");
        assertThat(dto.isHasGps()).isTrue();
        assertThat(dto.getLatitude()).isEqualTo(21.0285);
        assertThat(dto.isLocationStale()).isFalse();
    }

    @Test
    @DisplayName("getDriverMonitoringDetails throws DriverNotFoundException for non-existent driver")
    void getDriverMonitoringDetails_notFound_throwsException() {
        when(driverClient.findDriverById("drv-nonexistent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> trackingService.getDriverMonitoringDetails(dispatcherToken, "drv-nonexistent"))
                .isInstanceOf(DriverNotFoundException.class)
                .hasMessageContaining("Driver not found with identifier: drv-nonexistent");
    }
}
