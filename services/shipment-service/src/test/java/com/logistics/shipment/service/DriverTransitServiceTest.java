package com.logistics.shipment.service;

import com.logistics.common.security.JwtTokenProvider;
import com.logistics.common.security.Role;
import com.logistics.shipment.dto.StartTransitRequest;
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
class DriverTransitServiceTest {

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
        if (status == AssignmentStatus.PICKED_UP) {
            a.setPickedUpAt(now);
            a.setPickupNotes("Package picked up");
        }
        a.setNotes("Handle with care");
        a.setCreatedAt(now);
        a.setUpdatedAt(now);
        assignmentRepository.persist(a);
        return a;
    }

    @Test
    @Transactional
    @DisplayName("Assigned driver can start transit successfully from PICKED_UP")
    void assignedDriverCanStartTransitSuccessfully() {
        persistAssignment("ORD-T1", "NUM-T1", driver1UserId, AssignmentStatus.PICKED_UP);

        StartTransitRequest req = new StartTransitRequest("Departing from hub, estimated delivery 45 mins");
        Assignment result = assignmentService.startTransit(driver1Token, "ORD-T1", req);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(AssignmentStatus.IN_TRANSIT);
        assertThat(result.getTransitStartedAt()).isNotNull();
        assertThat(result.getTransitNotes()).isEqualTo("Departing from hub, estimated delivery 45 mins");

        // Verify history entry created
        List<AssignmentHistory> histories = assignmentHistoryRepository.findByOrderIdOrderByCreatedAtAsc("ORD-T1");
        assertThat(histories).hasSize(1);
        AssignmentHistory history = histories.get(0);
        assertThat(history.getActionType()).isEqualTo(AssignmentActionType.START_TRANSIT);
        assertThat(history.getPerformedBy()).isIn(driver1UserId, "driver1");
        assertThat(history.getNewDriverId()).isEqualTo(driver1UserId);
        assertThat(history.getReason()).contains("Departing from hub");
    }

    @Test
    @Transactional
    @DisplayName("Start transit succeeds with null request / empty notes")
    void startTransitWithEmptyNotes() {
        persistAssignment("ORD-T2", "NUM-T2", driver1UserId, AssignmentStatus.PICKED_UP);

        Assignment result = assignmentService.startTransit(driver1Token, "ORD-T2", null);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(AssignmentStatus.IN_TRANSIT);
        assertThat(result.getTransitStartedAt()).isNotNull();
        assertThat(result.getTransitNotes()).isNull();

        List<AssignmentHistory> histories = assignmentHistoryRepository.findByOrderIdOrderByCreatedAtAsc("ORD-T2");
        assertThat(histories).hasSize(1);
        assertThat(histories.get(0).getActionType()).isEqualTo(AssignmentActionType.START_TRANSIT);
    }

    @Test
    @Transactional
    @DisplayName("Start transit is idempotent if already IN_TRANSIT by same driver")
    void startTransitIdempotent() {
        persistAssignment("ORD-T3", "NUM-T3", driver1UserId, AssignmentStatus.PICKED_UP);

        StartTransitRequest req1 = new StartTransitRequest("First transit call");
        Assignment firstResult = assignmentService.startTransit(driver1Token, "ORD-T3", req1);
        Instant originalTransitStartedAt = firstResult.getTransitStartedAt();
        assertThat(originalTransitStartedAt).isNotNull();

        // Second call with same driver
        StartTransitRequest req2 = new StartTransitRequest("Second redundant call");
        Assignment secondResult = assignmentService.startTransit(driver1Token, "ORD-T3", req2);

        assertThat(secondResult.getStatus()).isEqualTo(AssignmentStatus.IN_TRANSIT);
        assertThat(secondResult.getTransitStartedAt()).isEqualTo(originalTransitStartedAt);

        // History shouldn't be duplicated for idempotent call
        List<AssignmentHistory> histories = assignmentHistoryRepository.findByOrderIdOrderByCreatedAtAsc("ORD-T3");
        assertThat(histories).hasSize(1);
    }

    @Test
    @Transactional
    @DisplayName("Reject transit when caller is not the assigned driver (403 Forbidden)")
    void rejectTransitWhenNotAssignedDriver() {
        persistAssignment("ORD-T4", "NUM-T4", driver1UserId, AssignmentStatus.PICKED_UP);

        StartTransitRequest req = new StartTransitRequest("Attempt by other driver");
        assertThatThrownBy(() -> assignmentService.startTransit(driver2Token, "ORD-T4", req))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You are not the assigned driver");
    }

    @Test
    @Transactional
    @DisplayName("Reject transit when caller does not have DRIVER role (403 Forbidden)")
    void rejectTransitWhenWrongRole() {
        persistAssignment("ORD-T5", "NUM-T5", driver1UserId, AssignmentStatus.PICKED_UP);

        StartTransitRequest req = new StartTransitRequest("Transit attempt");

        assertThatThrownBy(() -> assignmentService.startTransit(customerToken, "ORD-T5", req))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only drivers");

        assertThatThrownBy(() -> assignmentService.startTransit(dispatcherToken, "ORD-T5", req))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only drivers");

        assertThatThrownBy(() -> assignmentService.startTransit(adminToken, "ORD-T5", req))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only drivers");
    }

    @Test
    @Transactional
    @DisplayName("Reject transit when order is not in PICKED_UP state (400 Bad Request)")
    void rejectTransitWhenInvalidState() {
        // ASSIGNED: package not picked up yet -> cannot start transit
        persistAssignment("ORD-ASSIGNED", "NUM-A", driver1UserId, AssignmentStatus.ASSIGNED);
        persistAssignment("ORD-UNASSIGNED", "NUM-U", driver1UserId, AssignmentStatus.UNASSIGNED);
        persistAssignment("ORD-CANCELLED", "NUM-C", driver1UserId, AssignmentStatus.CANCELLED);
        persistAssignment("ORD-FAILED", "NUM-F", driver1UserId, AssignmentStatus.FAILED);

        StartTransitRequest req = new StartTransitRequest("Invalid transit attempt");

        assertThatThrownBy(() -> assignmentService.startTransit(driver1Token, "ORD-ASSIGNED", req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expected PICKED_UP");

        assertThatThrownBy(() -> assignmentService.startTransit(driver1Token, "ORD-UNASSIGNED", req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expected PICKED_UP");

        assertThatThrownBy(() -> assignmentService.startTransit(driver1Token, "ORD-CANCELLED", req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expected PICKED_UP");

        assertThatThrownBy(() -> assignmentService.startTransit(driver1Token, "ORD-FAILED", req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expected PICKED_UP");
    }

    @Test
    @Transactional
    @DisplayName("Reject transit when order does not exist (404 Not Found)")
    void rejectTransitWhenOrderNotFound() {
        StartTransitRequest req = new StartTransitRequest("Not found");
        assertThatThrownBy(() -> assignmentService.startTransit(driver1Token, "NON-EXISTENT", req))
                .isInstanceOf(AssignmentNotFoundException.class)
                .hasMessageContaining("NON-EXISTENT");
    }

    @Test
    @Transactional
    @DisplayName("Reject transit when auth token is missing or invalid (401 Bad Credentials)")
    void rejectTransitWhenInvalidToken() {
        StartTransitRequest req = new StartTransitRequest("No auth");
        assertThatThrownBy(() -> assignmentService.startTransit(null, "ORD-1", req))
                .isInstanceOf(BadCredentialsException.class);

        assertThatThrownBy(() -> assignmentService.startTransit("invalid-token", "ORD-1", req))
                .isInstanceOf(BadCredentialsException.class);
    }
}
