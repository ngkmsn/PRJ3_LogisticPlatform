package com.logistics.shipment.service;

import com.logistics.common.security.JwtTokenProvider;
import com.logistics.common.security.Role;
import com.logistics.shipment.dto.ConfirmPickupRequest;
import com.logistics.shipment.entity.Assignment;
import com.logistics.shipment.entity.AssignmentHistory;
import com.logistics.shipment.exception.AccessDeniedException;
import com.logistics.shipment.exception.AssignmentNotFoundException;
import com.logistics.shipment.exception.BadCredentialsException;
import com.logistics.shipment.model.AssignmentActionType;
import com.logistics.shipment.model.AssignmentStatus;
import com.logistics.shipment.repository.AssignmentHistoryRepository;
import com.logistics.shipment.repository.AssignmentRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@QuarkusTest
class DriverPickupServiceTest {

    @Inject
    AssignmentRepository assignmentRepository;

    @Inject
    AssignmentHistoryRepository assignmentHistoryRepository;

    @Inject
    AssignmentService assignmentService;

    @Inject
    JwtTokenProvider jwtTokenProvider;

    private String driver1Token;
    private String driver2Token;
    private String customerToken;
    private String dispatcherToken;
    private String adminToken;

    private final String driver1UserId = "drv-user-001";
    private final String driver2UserId = "drv-user-002";

    @BeforeEach
    @Transactional
    void setUp() {
        assignmentHistoryRepository.deleteAll();
        assignmentRepository.deleteAll();

        driver1Token = "Bearer " + jwtTokenProvider.generateToken(
                driver1UserId, "driver1", "driver1@test.com", Role.DRIVER.name()
        );
        driver2Token = "Bearer " + jwtTokenProvider.generateToken(
                driver2UserId, "driver2", "driver2@test.com", Role.DRIVER.name()
        );
        customerToken = "Bearer " + jwtTokenProvider.generateToken(
                "cust-user-001", "customer1", "cust@test.com", Role.CUSTOMER.name()
        );
        dispatcherToken = "Bearer " + jwtTokenProvider.generateToken(
                "disp-user-001", "dispatcher1", "disp@test.com", Role.DISPATCHER.name()
        );
        adminToken = "Bearer " + jwtTokenProvider.generateToken(
                "admin-user-001", "admin1", "admin@test.com", Role.ADMIN.name()
        );
    }

    private Assignment persistAssignment(String orderId, String orderNumber, String driverId, AssignmentStatus status) {
        Instant now = Instant.now();
        Assignment a = new Assignment();
        a.setId(UUID.randomUUID().toString());
        a.setOrderId(orderId);
        a.setOrderNumber(orderNumber);
        a.setCustomerId("cust-123");
        a.setRecipientName("Tran Van B");
        a.setRecipientPhone("0912345678");
        a.setDeliveryAddress("456 Nguyen Trai, Q5, TP.HCM");
        a.setTotalAmount(new BigDecimal("150000.00"));
        a.setStatus(status);
        a.setDriverId(driverId);
        a.setAssignedAt(now);
        a.setNotes("Handle with care");
        a.setCreatedAt(now);
        a.setUpdatedAt(now);
        assignmentRepository.persist(a);
        return a;
    }

    @Test
    @Transactional
    @DisplayName("Assigned driver can confirm pickup successfully")
    void assignedDriverCanConfirmPickupSuccessfully() {
        persistAssignment("ORD-P1", "NUM-P1", driver1UserId, AssignmentStatus.ASSIGNED);

        ConfirmPickupRequest req = new ConfirmPickupRequest("Package received in good condition, fragile tag intact");
        Assignment result = assignmentService.confirmPickup(driver1Token, "ORD-P1", req);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(AssignmentStatus.PICKED_UP);
        assertThat(result.getPickedUpAt()).isNotNull();
        assertThat(result.getPickupNotes()).isEqualTo("Package received in good condition, fragile tag intact");

        // Verify history entry created
        List<AssignmentHistory> histories = assignmentHistoryRepository.findByOrderIdOrderByCreatedAtAsc("ORD-P1");
        assertThat(histories).hasSize(1);
        AssignmentHistory history = histories.get(0);
        assertThat(history.getActionType()).isEqualTo(AssignmentActionType.PICKUP);
        assertThat(history.getPerformedBy()).isIn(driver1UserId, "driver1");
        assertThat(history.getNewDriverId()).isEqualTo(driver1UserId);
        assertThat(history.getReason()).contains("fragile tag intact");
    }

    @Test
    @Transactional
    @DisplayName("Confirm pickup succeeds with null request / empty notes")
    void confirmPickupWithEmptyNotes() {
        persistAssignment("ORD-P2", "NUM-P2", driver1UserId, AssignmentStatus.ASSIGNED);

        Assignment result = assignmentService.confirmPickup(driver1Token, "ORD-P2", null);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(AssignmentStatus.PICKED_UP);
        assertThat(result.getPickedUpAt()).isNotNull();
        assertThat(result.getPickupNotes()).isNull();

        List<AssignmentHistory> histories = assignmentHistoryRepository.findByOrderIdOrderByCreatedAtAsc("ORD-P2");
        assertThat(histories).hasSize(1);
        assertThat(histories.get(0).getActionType()).isEqualTo(AssignmentActionType.PICKUP);
    }

    @Test
    @Transactional
    @DisplayName("Confirm pickup is idempotent if already PICKED_UP by same driver")
    void confirmPickupIdempotent() {
        persistAssignment("ORD-P3", "NUM-P3", driver1UserId, AssignmentStatus.ASSIGNED);

        ConfirmPickupRequest req1 = new ConfirmPickupRequest("First pickup");
        Assignment firstResult = assignmentService.confirmPickup(driver1Token, "ORD-P3", req1);
        Instant originalPickedUpAt = firstResult.getPickedUpAt();
        assertThat(originalPickedUpAt).isNotNull();

        // Second call with same driver
        ConfirmPickupRequest req2 = new ConfirmPickupRequest("Second redundant call");
        Assignment secondResult = assignmentService.confirmPickup(driver1Token, "ORD-P3", req2);

        assertThat(secondResult.getStatus()).isEqualTo(AssignmentStatus.PICKED_UP);
        assertThat(secondResult.getPickedUpAt()).isEqualTo(originalPickedUpAt);

        // History shouldn't be duplicated for idempotent call
        List<AssignmentHistory> histories = assignmentHistoryRepository.findByOrderIdOrderByCreatedAtAsc("ORD-P3");
        assertThat(histories).hasSize(1);
    }

    @Test
    @Transactional
    @DisplayName("Reject pickup when caller is not the assigned driver (403 Forbidden)")
    void rejectPickupWhenNotAssignedDriver() {
        persistAssignment("ORD-P4", "NUM-P4", driver1UserId, AssignmentStatus.ASSIGNED);

        ConfirmPickupRequest req = new ConfirmPickupRequest("Attempt by other driver");
        assertThatThrownBy(() -> assignmentService.confirmPickup(driver2Token, "ORD-P4", req))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You are not the assigned driver");
    }

    @Test
    @Transactional
    @DisplayName("Reject pickup when caller does not have DRIVER role (403 Forbidden)")
    void rejectPickupWhenWrongRole() {
        persistAssignment("ORD-P5", "NUM-P5", driver1UserId, AssignmentStatus.ASSIGNED);

        ConfirmPickupRequest req = new ConfirmPickupRequest("Pickup attempt");

        assertThatThrownBy(() -> assignmentService.confirmPickup(customerToken, "ORD-P5", req))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only drivers");

        assertThatThrownBy(() -> assignmentService.confirmPickup(dispatcherToken, "ORD-P5", req))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only drivers");

        assertThatThrownBy(() -> assignmentService.confirmPickup(adminToken, "ORD-P5", req))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only drivers");
    }

    @Test
    @Transactional
    @DisplayName("Reject pickup when order is in invalid state (not ASSIGNED)")
    void rejectPickupWhenInvalidState() {
        persistAssignment("ORD-UNASSIGNED", "NUM-U", driver1UserId, AssignmentStatus.UNASSIGNED);
        persistAssignment("ORD-CANCELLED", "NUM-C", driver1UserId, AssignmentStatus.CANCELLED);
        persistAssignment("ORD-FAILED", "NUM-D", driver1UserId, AssignmentStatus.FAILED);

        ConfirmPickupRequest req = new ConfirmPickupRequest("Invalid attempt");

        assertThatThrownBy(() -> assignmentService.confirmPickup(driver1Token, "ORD-UNASSIGNED", req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("UNASSIGNED");

        assertThatThrownBy(() -> assignmentService.confirmPickup(driver1Token, "ORD-CANCELLED", req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CANCELLED");

        assertThatThrownBy(() -> assignmentService.confirmPickup(driver1Token, "ORD-FAILED", req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FAILED");
    }

    @Test
    @Transactional
    @DisplayName("Reject pickup when order does not exist (404 Not Found)")
    void rejectPickupWhenOrderNotFound() {
        ConfirmPickupRequest req = new ConfirmPickupRequest("Not found");
        assertThatThrownBy(() -> assignmentService.confirmPickup(driver1Token, "NON-EXISTENT", req))
                .isInstanceOf(AssignmentNotFoundException.class)
                .hasMessageContaining("NON-EXISTENT");
    }

    @Test
    @Transactional
    @DisplayName("Reject pickup when auth token is missing or invalid (401 Bad Credentials)")
    void rejectPickupWhenInvalidToken() {
        ConfirmPickupRequest req = new ConfirmPickupRequest("No auth");
        assertThatThrownBy(() -> assignmentService.confirmPickup(null, "ORD-1", req))
                .isInstanceOf(BadCredentialsException.class);

        assertThatThrownBy(() -> assignmentService.confirmPickup("invalid-token", "ORD-1", req))
                .isInstanceOf(BadCredentialsException.class);
    }
}
