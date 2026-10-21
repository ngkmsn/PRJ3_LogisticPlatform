package com.logistics.shipment.service;

import com.logistics.common.dto.OrderTrackingDto;
import com.logistics.common.security.JwtTokenProvider;
import com.logistics.common.security.Role;
import com.logistics.shipment.entity.Assignment;
import com.logistics.shipment.entity.DriverLocation;
import com.logistics.shipment.exception.AccessDeniedException;
import com.logistics.shipment.exception.AssignmentNotFoundException;
import com.logistics.shipment.exception.BadCredentialsException;
import com.logistics.shipment.exception.TrackingServiceException;
import com.logistics.shipment.model.AssignmentStatus;
import com.logistics.shipment.repository.AssignmentRepository;
import com.logistics.shipment.repository.DriverLocationRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

@QuarkusTest
class CustomerOrderTrackingServiceTest {

    @Inject
    AssignmentRepository assignmentRepository;

    @Inject
    DriverLocationRepository driverLocationRepository;

    @Inject
    TrackingService trackingService;

    @Inject
    JwtTokenProvider jwtTokenProvider;

    @InjectSpy
    AssignmentRepository spyAssignmentRepository;

    private String customerAToken;
    private String customerBToken;
    private String adminToken;
    private final String customerAId = "cust-trk-uuid-01";
    private final String customerBId = "cust-trk-uuid-02";
    private final String driverId = "drv-trk-uuid-01";

    @BeforeEach
    @Transactional
    void setUp() {
        driverLocationRepository.deleteAll();
        assignmentRepository.deleteAll();

        customerAToken = "Bearer " + jwtTokenProvider.generateToken(
                customerAId, "customerA", "custA@test.com", Role.CUSTOMER.name()
        );
        customerBToken = "Bearer " + jwtTokenProvider.generateToken(
                customerBId, "customerB", "custB@test.com", Role.CUSTOMER.name()
        );
        adminToken = "Bearer " + jwtTokenProvider.generateToken(
                "admin-user-01", "adminUser", "admin@logistics.com", Role.ADMIN.name()
        );
    }

    private Assignment createAssignment(String orderId, String customerId, String driverId, AssignmentStatus status) {
        Assignment assignment = new Assignment(
                UUID.randomUUID().toString(),
                orderId,
                "ORD-" + orderId,
                customerId,
                "Recipient Name",
                "0987654321",
                "123 Delivery St, City",
                new BigDecimal("150000"),
                status,
                driverId,
                status == AssignmentStatus.ASSIGNED ? Instant.now() : null,
                "Tracking Test Note",
                Instant.now(),
                Instant.now()
        );
        assignmentRepository.persist(assignment);
        return assignment;
    }

    private DriverLocation recordDriverGps(String targetDriverId, double lat, double lon, Instant timestamp) {
        DriverLocation location = new DriverLocation(
                UUID.randomUUID().toString(),
                targetDriverId,
                targetDriverId + "-user",
                lat,
                lon,
                5.0,
                25.0,
                90.0,
                null,
                timestamp,
                timestamp,
                timestamp
        );
        driverLocationRepository.persist(location);
        return location;
    }

    @Test
    @Transactional
    @DisplayName("Customer successfully views driver location when order is in transit")
    void getOrderTracking_inTransit_success() {
        String orderId = "ord-trk-1001";
        createAssignment(orderId, customerAId, driverId, AssignmentStatus.IN_TRANSIT);
        Instant recentGps = Instant.now().minus(2, ChronoUnit.MINUTES);
        recordDriverGps(driverId, 21.028511, 105.854444, recentGps);

        OrderTrackingDto tracking = trackingService.getOrderTracking(customerAToken, orderId);

        assertThat(tracking).isNotNull();
        assertThat(tracking.getOrderId()).isEqualTo(orderId);
        assertThat(tracking.getCustomerId()).isEqualTo(customerAId);
        assertThat(tracking.getDriverId()).isEqualTo(driverId);
        assertThat(tracking.getLatitude()).isEqualTo(21.028511);
        assertThat(tracking.getLongitude()).isEqualTo(105.854444);
        assertThat(tracking.isLiveTrackingActive()).isTrue();
        assertThat(tracking.isStale()).isFalse();
        assertThat(tracking.getStaleWarning()).isNull();
    }

    @Test
    @Transactional
    @DisplayName("Customer successfully views driver location when order is assigned (en route to pickup)")
    void getOrderTracking_assigned_success() {
        String orderId = "ord-trk-1002";
        createAssignment(orderId, customerAId, driverId, AssignmentStatus.ASSIGNED);
        recordDriverGps(driverId, 10.776889, 106.700806, Instant.now().minus(5, ChronoUnit.MINUTES));

        OrderTrackingDto tracking = trackingService.getOrderTracking(customerAToken, orderId);

        assertThat(tracking).isNotNull();
        assertThat(tracking.getOrderId()).isEqualTo(orderId);
        assertThat(tracking.getAssignmentStatus()).isEqualTo("ASSIGNED");
        assertThat(tracking.getLatitude()).isEqualTo(10.776889);
        assertThat(tracking.getLongitude()).isEqualTo(106.700806);
    }

    @Test
    @Transactional
    @DisplayName("Customer cannot view tracking of order owned by another customer (403 Access Denied)")
    void getOrderTracking_differentCustomer_throwsAccessDenied() {
        String orderId = "ord-trk-1003";
        createAssignment(orderId, customerAId, driverId, AssignmentStatus.IN_TRANSIT);
        recordDriverGps(driverId, 21.01, 105.81, Instant.now());

        assertThatThrownBy(() -> trackingService.getOrderTracking(customerBToken, orderId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You do not own this order");
    }

    @Test
    @DisplayName("Missing or invalid token throws BadCredentialsException (401)")
    void getOrderTracking_unauthenticated_throwsBadCredentials() {
        assertThatThrownBy(() -> trackingService.getOrderTracking(null, "ord-1001"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Missing or invalid");

        assertThatThrownBy(() -> trackingService.getOrderTracking("Bearer invalid.token", "ord-1001"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("invalid or expired");
    }

    @Test
    @DisplayName("Non-existent order throws AssignmentNotFoundException (404)")
    void getOrderTracking_notFound_throwsNotFound() {
        assertThatThrownBy(() -> trackingService.getOrderTracking(customerAToken, "non-existent-order"))
                .isInstanceOf(AssignmentNotFoundException.class)
                .hasMessageContaining("Assignment/Order not found");
    }

    @Test
    @Transactional
    @DisplayName("Order in PENDING status (driver not yet assigned) throws IllegalStateException")
    void getOrderTracking_pendingStatus_throwsIllegalState() {
        String orderId = "ord-trk-pending";
        createAssignment(orderId, customerAId, null, AssignmentStatus.PENDING);

        assertThatThrownBy(() -> trackingService.getOrderTracking(customerAToken, orderId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Order is not in a trackable state")
                .hasMessageContaining("Driver has not been assigned yet");
    }

    @Test
    @Transactional
    @DisplayName("Order already DELIVERED throws IllegalStateException")
    void getOrderTracking_deliveredStatus_throwsIllegalState() {
        String orderId = "ord-trk-delivered";
        createAssignment(orderId, customerAId, driverId, AssignmentStatus.DELIVERED);

        assertThatThrownBy(() -> trackingService.getOrderTracking(customerAToken, orderId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Order is not in a trackable state")
                .hasMessageContaining("already been delivered");
    }

    @Test
    @Transactional
    @DisplayName("Order CANCELLED throws IllegalStateException")
    void getOrderTracking_cancelledStatus_throwsIllegalState() {
        String orderId = "ord-trk-cancelled";
        createAssignment(orderId, customerAId, driverId, AssignmentStatus.CANCELLED);

        assertThatThrownBy(() -> trackingService.getOrderTracking(customerAToken, orderId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Order is not in a trackable state")
                .hasMessageContaining("Order has been cancelled");
    }

    @Test
    @Transactional
    @DisplayName("Order FAILED throws IllegalStateException")
    void getOrderTracking_failedStatus_throwsIllegalState() {
        String orderId = "ord-trk-failed";
        createAssignment(orderId, customerAId, driverId, AssignmentStatus.FAILED);

        assertThatThrownBy(() -> trackingService.getOrderTracking(customerAToken, orderId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Order is not in a trackable state")
                .hasMessageContaining("Order delivery failed");
    }

    @Test
    @Transactional
    @DisplayName("Driver assigned but no GPS location recorded throws IllegalStateException")
    void getOrderTracking_noGpsRecorded_throwsIllegalState() {
        String orderId = "ord-trk-no-gps";
        createAssignment(orderId, customerAId, "drv-with-no-gps", AssignmentStatus.IN_TRANSIT);

        assertThatThrownBy(() -> trackingService.getOrderTracking(customerAToken, orderId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No GPS location recorded for the assigned driver yet");
    }

    @Test
    @Transactional
    @DisplayName("Driver GPS location older than 30 minutes throws IllegalStateException by default")
    void getOrderTracking_staleLocation_default_throwsIllegalState() {
        String orderId = "ord-trk-stale";
        createAssignment(orderId, customerAId, driverId, AssignmentStatus.IN_TRANSIT);
        Instant staleGpsTime = Instant.now().minus(45, ChronoUnit.MINUTES);
        recordDriverGps(driverId, 21.05, 105.85, staleGpsTime);

        assertThatThrownBy(() -> trackingService.getOrderTracking(customerAToken, orderId, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Driver GPS location is stale")
                .hasMessageContaining("exceeds 30 minute freshness threshold");
    }

    @Test
    @Transactional
    @DisplayName("Driver GPS location older than 30 minutes returns DTO with stale warning when allowStale=true")
    void getOrderTracking_staleLocation_allowStale_returnsDtoWithWarning() {
        String orderId = "ord-trk-stale-allowed";
        createAssignment(orderId, customerAId, driverId, AssignmentStatus.IN_TRANSIT);
        Instant staleGpsTime = Instant.now().minus(50, ChronoUnit.MINUTES);
        recordDriverGps(driverId, 21.05, 105.85, staleGpsTime);

        OrderTrackingDto tracking = trackingService.getOrderTracking(customerAToken, orderId, true);

        assertThat(tracking).isNotNull();
        assertThat(tracking.isStale()).isTrue();
        assertThat(tracking.getStaleWarning()).contains("Driver GPS location is stale");
        assertThat(tracking.getLocationAgeMinutes()).isGreaterThanOrEqualTo(49L);
        assertThat(tracking.getLatitude()).isEqualTo(21.05);
    }

    @Test
    @Transactional
    @DisplayName("Admin can view tracking of any customer's active order")
    void getOrderTracking_adminRole_success() {
        String orderId = "ord-trk-admin";
        createAssignment(orderId, customerAId, driverId, AssignmentStatus.IN_TRANSIT);
        recordDriverGps(driverId, 21.03, 105.84, Instant.now().minus(1, ChronoUnit.MINUTES));

        OrderTrackingDto tracking = trackingService.getOrderTracking(adminToken, orderId);

        assertThat(tracking).isNotNull();
        assertThat(tracking.getOrderId()).isEqualTo(orderId);
    }

    @Test
    @DisplayName("Database failure throws TrackingServiceException (500)")
    void getOrderTracking_dbFailure_throwsTrackingServiceException() {
        doThrow(new RuntimeException("Simulated DB connection failure"))
                .when(spyAssignmentRepository).findByOrderId(anyString());

        assertThatThrownBy(() -> trackingService.getOrderTracking(customerAToken, "ord-1001"))
                .isInstanceOf(TrackingServiceException.class)
                .hasMessageContaining("Database error retrieving order tracking");

        Mockito.reset(spyAssignmentRepository);
    }
}
