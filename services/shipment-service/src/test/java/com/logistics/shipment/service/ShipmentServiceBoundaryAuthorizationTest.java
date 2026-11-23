package com.logistics.shipment.service;

import com.logistics.common.security.JwtTokenProvider;
import com.logistics.common.security.ServiceTokenProvider;
import com.logistics.shipment.dto.ConfirmPickupRequest;
import com.logistics.shipment.dto.ManualAssignDriverRequest;
import com.logistics.shipment.dto.PagedResponse;
import com.logistics.shipment.dto.UnassignedOrderDto;
import com.logistics.shipment.eligibility.client.DriverClient;
import com.logistics.shipment.entity.Assignment;
import com.logistics.shipment.entity.DriverLocation;
import com.logistics.shipment.exception.AccessDeniedException;
import com.logistics.shipment.exception.BadCredentialsException;
import com.logistics.shipment.model.AssignmentStatus;
import com.logistics.shipment.repository.AssignmentRepository;
import com.logistics.shipment.repository.DriverLocationRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@QuarkusTest
class ShipmentServiceBoundaryAuthorizationTest {

    private static final String SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    @Inject
    AssignmentService assignmentService;

    @Inject
    TrackingService trackingService;

    @Inject
    JwtTokenProvider jwtTokenProvider;

    @InjectMock
    AssignmentRepository assignmentRepository;

    @InjectMock
    DriverLocationRepository driverLocationRepository;

    @InjectMock
    DriverClient driverClient;

    private ServiceTokenProvider serviceTokenProvider;

    private String assignedDriverToken;
    private String intruderDriverToken;
    private String customerOwnerToken;
    private String customerStrangerToken;
    private String dispatcherToken;
    private String adminToken;

    private Assignment activeAssignment;

    @BeforeEach
    void setUp() {
        serviceTokenProvider = new ServiceTokenProvider("order-service", SECRET, "order-service", 300L);

        assignedDriverToken = "Bearer " + jwtTokenProvider.generateToken("drv-assigned", "driverA", "d1@test.com", "DRIVER");
        intruderDriverToken = "Bearer " + jwtTokenProvider.generateToken("drv-intruder", "driverB", "d2@test.com", "DRIVER");
        customerOwnerToken = "Bearer " + jwtTokenProvider.generateToken("cust-owner", "alice", "c1@test.com", "CUSTOMER");
        customerStrangerToken = "Bearer " + jwtTokenProvider.generateToken("cust-stranger", "bob", "c2@test.com", "CUSTOMER");
        dispatcherToken = "Bearer " + jwtTokenProvider.generateToken("disp-1", "operator", "op@test.com", "DISPATCHER");
        adminToken = "Bearer " + jwtTokenProvider.generateToken("adm-1", "admin", "admin@test.com", "ADMIN");

        activeAssignment = new Assignment();
        activeAssignment.setId("asg-100");
        activeAssignment.setOrderId("ORD-999");
        activeAssignment.setOrderNumber("NUM-ORD-999");
        activeAssignment.setCustomerId("cust-owner");
        activeAssignment.setDriverId("drv-assigned");
        activeAssignment.setStatus(AssignmentStatus.ASSIGNED);
        activeAssignment.setDeliveryAddress("456 Le Loi, District 1, HCMC");
        activeAssignment.setTotalAmount(BigDecimal.valueOf(150000));
        activeAssignment.setCreatedAt(Instant.now());
        activeAssignment.setAssignedAt(Instant.now());

        when(assignmentRepository.findByOrderId("ORD-999")).thenReturn(Optional.of(activeAssignment));
    }

    @Test
    @DisplayName("PB-058 [401]: Missing or invalid token fails authentication at boundary")
    void unauthenticated_rejected() {
        assertThatThrownBy(() -> assignmentService.confirmPickup(null, "ORD-999", new ConfirmPickupRequest()))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Missing or invalid Authorization header");

        assertThatThrownBy(() -> assignmentService.confirmPickup("Bearer invalid-jwt", "ORD-999", new ConfirmPickupRequest()))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Token is invalid or expired");
    }

    @Test
    @DisplayName("PB-058 [Driver Ownership]: Assigned driver can confirm pickup successfully")
    void assignedDriver_confirmPickup_success() {
        ConfirmPickupRequest req = new ConfirmPickupRequest();
        req.setNotes("Picked up parcels at sender hub");

        Assignment updated = assignmentService.confirmPickup(assignedDriverToken, "ORD-999", req);
        assertThat(updated).isNotNull();
        assertThat(updated.getStatus()).isEqualTo(AssignmentStatus.PICKED_UP);
    }

    @Test
    @DisplayName("PB-058 [IDOR Prevention]: Intruder driver cannot confirm pickup on another driver's order")
    void intruderDriver_confirmPickup_rejectedWith403() {
        ConfirmPickupRequest req = new ConfirmPickupRequest();
        req.setNotes("Unauthorized attempt");

        assertThatThrownBy(() -> assignmentService.confirmPickup(intruderDriverToken, "ORD-999", req))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You are not the assigned driver for order 'ORD-999'");
    }

    @Test
    @DisplayName("PB-058 [RBAC]: Customer cannot perform driver delivery actions")
    void customer_confirmPickup_rejectedWith403() {
        assertThatThrownBy(() -> assignmentService.confirmPickup(customerOwnerToken, "ORD-999", new ConfirmPickupRequest()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only drivers are permitted to confirm order pickup");
    }

    @Test
    @DisplayName("PB-058 [IDOR Prevention]: Customer cannot query driver GPS locations directly")
    void customer_getDriverLocation_rejectedWith403() {
        assertThatThrownBy(() -> trackingService.getDriverLocation(customerOwnerToken, "drv-assigned"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Customers are not permitted to query driver GPS locations directly");
    }

    @Test
    @DisplayName("PB-058 [IDOR Prevention]: Driver cannot query another driver's GPS location")
    void driver_getOtherDriverLocation_rejectedWith403() {
        assertThatThrownBy(() -> trackingService.getDriverLocation(intruderDriverToken, "drv-assigned"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Drivers may only view their own GPS location");
    }

    @Test
    @DisplayName("PB-058 [RBAC]: Operations and Admin can query driver GPS location")
    void operations_getDriverLocation_success() {
        DriverLocation loc = new DriverLocation();
        loc.setId("loc-1");
        loc.setDriverId("drv-assigned");
        loc.setLatitude(10.7769);
        loc.setLongitude(106.7009);
        loc.setTimestamp(Instant.now());
        loc.setUpdatedAt(Instant.now());

        when(driverLocationRepository.findByDriverId("drv-assigned")).thenReturn(Optional.of(loc));

        Optional<DriverLocation> result = trackingService.getDriverLocation(dispatcherToken, "drv-assigned");
        assertThat(result).isPresent();
        assertThat(result.get().getDriverId()).isEqualTo("drv-assigned");
    }

    @Test
    @DisplayName("PB-058 [Ownership]: Customer owner can track order; stranger customer is rejected")
    void customer_orderTracking_ownershipEnforcement() {
        DriverLocation loc = new DriverLocation();
        loc.setId("loc-1");
        loc.setDriverId("drv-assigned");
        loc.setLatitude(10.7769);
        loc.setLongitude(106.7009);
        loc.setTimestamp(Instant.now());
        loc.setUpdatedAt(Instant.now());

        when(driverLocationRepository.findByDriverId("drv-assigned")).thenReturn(Optional.of(loc));

        // Owner customer succeeds
        assertThatCode(() -> trackingService.getOrderTracking(customerOwnerToken, "ORD-999"))
                .doesNotThrowAnyException();

        // Stranger customer is rejected with 403
        assertThatThrownBy(() -> trackingService.getOrderTracking(customerStrangerToken, "ORD-999"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You do not own this order");
    }

    @Test
    @DisplayName("PB-058 [Operations RBAC]: Only Dispatcher/Admin can view unassigned orders and manually assign")
    void operations_boundaryEnforcement() {
        when(assignmentRepository.findUnassigned(any(), eq(0), eq(10), any()))
                .thenReturn(new PagedResponse<>(List.of(), 0, 10, 0, 0));

        // Dispatcher succeeds
        assertThatCode(() -> assignmentService.getUnassignedOrders(dispatcherToken, 0, 10, "UNASSIGNED", "oldest_first"))
                .doesNotThrowAnyException();

        // Admin succeeds
        assertThatCode(() -> assignmentService.getUnassignedOrders(adminToken, 0, 10, "UNASSIGNED", "oldest_first"))
                .doesNotThrowAnyException();

        // Customer rejected
        assertThatThrownBy(() -> assignmentService.getUnassignedOrders(customerOwnerToken, 0, 10, "UNASSIGNED", "oldest_first"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only administrators and dispatchers are permitted to view unassigned orders");

        // Driver rejected
        assertThatThrownBy(() -> assignmentService.getUnassignedOrders(assignedDriverToken, 0, 10, "UNASSIGNED", "oldest_first"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only administrators and dispatchers are permitted to view unassigned orders");
    }

    @Test
    @DisplayName("PB-058 [S2S Authorization]: Service with valid audience and scope succeeds; missing scope fails")
    void serviceToService_trackingAccess() {
        DriverLocation loc = new DriverLocation();
        loc.setId("loc-1");
        loc.setDriverId("drv-assigned");
        loc.setLatitude(10.7769);
        loc.setLongitude(106.7009);
        loc.setTimestamp(Instant.now());
        loc.setUpdatedAt(Instant.now());

        when(driverLocationRepository.findByDriverId("drv-assigned")).thenReturn(Optional.of(loc));

        // Valid S2S token
        String validS2SToken = "Bearer " + serviceTokenProvider.generateToken("shipment-service", Set.of("shipment:tracking:read"));
        assertThatCode(() -> trackingService.getOrderTracking(validS2SToken, "ORD-999"))
                .doesNotThrowAnyException();

        // Wrong audience
        String wrongAudToken = "Bearer " + serviceTokenProvider.generateToken("order-service", Set.of("shipment:tracking:read"));
        assertThatThrownBy(() -> trackingService.getOrderTracking(wrongAudToken, "ORD-999"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Invalid token audience: expected shipment-service");

        // Missing scope
        String missingScopeToken = "Bearer " + serviceTokenProvider.generateToken("shipment-service", Set.of("unrelated:scope"));
        assertThatThrownBy(() -> trackingService.getOrderTracking(missingScopeToken, "ORD-999"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Insufficient service permissions");
    }
}
