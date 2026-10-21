package com.logistics.shipment.service;

import com.logistics.common.security.JwtTokenProvider;
import com.logistics.common.security.Role;
import com.logistics.shipment.dto.ReportDeliveryFailureRequest;
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
class DriverDeliveryFailureServiceTest {

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

    private final String driver1UserId = "drv-fail-srv-001";
    private final String driver2UserId = "drv-fail-srv-002";

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
        if (status == AssignmentStatus.PICKED_UP || status == AssignmentStatus.IN_TRANSIT) {
            a.setPickedUpAt(now);
            a.setPickupNotes("Package picked up");
        }
        if (status == AssignmentStatus.IN_TRANSIT) {
            a.setTransitStartedAt(now);
            a.setTransitNotes("Transit started");
        }
        if (status == AssignmentStatus.DELIVERED) {
            a.setPickedUpAt(now);
            a.setTransitStartedAt(now);
            a.setDeliveredAt(now);
        }
        if (status == AssignmentStatus.FAILED) {
            a.setPickedUpAt(now);
            a.setTransitStartedAt(now);
            a.setFailedAt(now);
            a.setFailureReason("Pre-failed reason");
        }
        a.setNotes("Handle with care");
        a.setCreatedAt(now);
        a.setUpdatedAt(now);
        assignmentRepository.persist(a);
        return a;
    }

    @Test
    @Transactional
    @DisplayName("Assigned driver can report delivery failure successfully from IN_TRANSIT")
    void assignedDriverCanReportFailure_fromInTransit() {
        persistAssignment("ORD-F1", "NUM-F1", driver1UserId, AssignmentStatus.IN_TRANSIT);

        ReportDeliveryFailureRequest req = new ReportDeliveryFailureRequest(
                "RECIPIENT_UNREACHABLE",
                "Called 3 times over 20 minutes, phone busy, gate locked"
        );
        Assignment result = assignmentService.reportDeliveryFailure(driver1Token, "ORD-F1", req);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(AssignmentStatus.FAILED);
        assertThat(result.getFailedAt()).isNotNull();
        assertThat(result.getFailureReason()).isEqualTo("RECIPIENT_UNREACHABLE");
        assertThat(result.getFailureNotes()).isEqualTo("Called 3 times over 20 minutes, phone busy, gate locked");

        // Verify history entry
        List<AssignmentHistory> histories = assignmentHistoryRepository.findByOrderIdOrderByCreatedAtAsc("ORD-F1");
        assertThat(histories).hasSize(1);
        AssignmentHistory history = histories.get(0);
        assertThat(history.getActionType()).isEqualTo(AssignmentActionType.DELIVERY_FAILED);
        assertThat(history.getPerformedBy()).isIn(driver1UserId, "driver1");
        assertThat(history.getNewDriverId()).isEqualTo(driver1UserId);
        assertThat(history.getReason()).contains("RECIPIENT_UNREACHABLE");
    }

    @Test
    @Transactional
    @DisplayName("Assigned driver can report delivery failure successfully from PICKED_UP")
    void assignedDriverCanReportFailure_fromPickedUp() {
        persistAssignment("ORD-F1-PICKED", "NUM-F1P", driver1UserId, AssignmentStatus.PICKED_UP);

        ReportDeliveryFailureRequest req = new ReportDeliveryFailureRequest(
                "CUSTOMER_CANCELLED_AT_HUB",
                "Customer called and cancelled delivery immediately after pickup"
        );
        Assignment result = assignmentService.reportDeliveryFailure(driver1Token, "ORD-F1-PICKED", req);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(AssignmentStatus.FAILED);
        assertThat(result.getFailedAt()).isNotNull();
        assertThat(result.getFailureReason()).isEqualTo("CUSTOMER_CANCELLED_AT_HUB");

        List<AssignmentHistory> histories = assignmentHistoryRepository.findByOrderIdOrderByCreatedAtAsc("ORD-F1-PICKED");
        assertThat(histories).hasSize(1);
        assertThat(histories.get(0).getActionType()).isEqualTo(AssignmentActionType.DELIVERY_FAILED);
    }

    @Test
    @Transactional
    @DisplayName("Missing or invalid failure reason throws IllegalArgumentException")
    void missingOrInvalidFailureReason_throwsIllegalArgument() {
        persistAssignment("ORD-F2", "NUM-F2", driver1UserId, AssignmentStatus.IN_TRANSIT);

        // Null request
        assertThatThrownBy(() -> assignmentService.reportDeliveryFailure(driver1Token, "ORD-F2", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failure reason must not be null or empty");

        // Empty request
        assertThatThrownBy(() -> assignmentService.reportDeliveryFailure(driver1Token, "ORD-F2", new ReportDeliveryFailureRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failure reason must not be null or empty");

        // Whitespace only
        assertThatThrownBy(() -> assignmentService.reportDeliveryFailure(driver1Token, "ORD-F2", new ReportDeliveryFailureRequest("   ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failure reason must not be null or empty");

        // Too short (< 3 characters)
        assertThatThrownBy(() -> assignmentService.reportDeliveryFailure(driver1Token, "ORD-F2", new ReportDeliveryFailureRequest("no")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failure reason must be at least 3 characters");
    }

    @Test
    @Transactional
    @DisplayName("Reporting delivery failure is idempotent if already FAILED by same driver")
    void reportDeliveryFailureIdempotent() {
        persistAssignment("ORD-F3", "NUM-F3", driver1UserId, AssignmentStatus.IN_TRANSIT);

        ReportDeliveryFailureRequest req1 = new ReportDeliveryFailureRequest("RECIPIENT_NOT_AT_HOME", "Initial failure");
        Assignment firstResult = assignmentService.reportDeliveryFailure(driver1Token, "ORD-F3", req1);
        Instant originalFailedAt = firstResult.getFailedAt();
        assertThat(originalFailedAt).isNotNull();

        // Second call with same driver
        ReportDeliveryFailureRequest req2 = new ReportDeliveryFailureRequest("RECIPIENT_NOT_AT_HOME", "Duplicate call");
        Assignment secondResult = assignmentService.reportDeliveryFailure(driver1Token, "ORD-F3", req2);

        assertThat(secondResult.getStatus()).isEqualTo(AssignmentStatus.FAILED);
        assertThat(secondResult.getFailedAt()).isEqualTo(originalFailedAt);

        // Ensure no duplicate history records created on idempotent second call
        List<AssignmentHistory> histories = assignmentHistoryRepository.findByOrderIdOrderByCreatedAtAsc("ORD-F3");
        assertThat(histories).hasSize(1);
    }

    @Test
    @Transactional
    @DisplayName("Unassigned driver cannot report failure (throws 403 AccessDeniedException)")
    void unassignedDriverCannotReportFailure() {
        persistAssignment("ORD-F4", "NUM-F4", driver1UserId, AssignmentStatus.IN_TRANSIT);

        ReportDeliveryFailureRequest req = new ReportDeliveryFailureRequest("WRONG_DRIVER_ATTEMPT");

        assertThatThrownBy(() -> assignmentService.reportDeliveryFailure(driver2Token, "ORD-F4", req))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You are not the assigned driver");

        List<AssignmentHistory> histories = assignmentHistoryRepository.findByOrderIdOrderByCreatedAtAsc("ORD-F4");
        assertThat(histories).isEmpty();
    }

    @Test
    @Transactional
    @DisplayName("Non-DRIVER roles cannot report failure (throws 403 AccessDeniedException)")
    void nonDriverRolesCannotReportFailure() {
        persistAssignment("ORD-F5", "NUM-F5", driver1UserId, AssignmentStatus.IN_TRANSIT);

        ReportDeliveryFailureRequest req = new ReportDeliveryFailureRequest("RECIPIENT_UNREACHABLE");

        assertThatThrownBy(() -> assignmentService.reportDeliveryFailure(adminToken, "ORD-F5", req))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only drivers");

        assertThatThrownBy(() -> assignmentService.reportDeliveryFailure(dispatcherToken, "ORD-F5", req))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only drivers");

        assertThatThrownBy(() -> assignmentService.reportDeliveryFailure(customerToken, "ORD-F5", req))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only drivers");
    }

    @Test
    @Transactional
    @DisplayName("Report failure throws IllegalStateException if order is in invalid state (ASSIGNED, DELIVERED, CANCELLED)")
    void invalidStatusCannotReportFailure() {
        persistAssignment("ORD-F6-ASSIGNED", "NUM-F6A", driver1UserId, AssignmentStatus.ASSIGNED);
        persistAssignment("ORD-F6-DELIVERED", "NUM-F6D", driver1UserId, AssignmentStatus.DELIVERED);
        persistAssignment("ORD-F6-CANCELLED", "NUM-F6C", driver1UserId, AssignmentStatus.CANCELLED);

        ReportDeliveryFailureRequest req = new ReportDeliveryFailureRequest("RECIPIENT_REFUSED");

        assertThatThrownBy(() -> assignmentService.reportDeliveryFailure(driver1Token, "ORD-F6-ASSIGNED", req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expected IN_TRANSIT or PICKED_UP");

        assertThatThrownBy(() -> assignmentService.reportDeliveryFailure(driver1Token, "ORD-F6-DELIVERED", req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expected IN_TRANSIT or PICKED_UP");

        assertThatThrownBy(() -> assignmentService.reportDeliveryFailure(driver1Token, "ORD-F6-CANCELLED", req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expected IN_TRANSIT or PICKED_UP");
    }

    @Test
    @Transactional
    @DisplayName("Report failure throws AssignmentNotFoundException if order does not exist")
    void nonExistentOrderThrowsNotFound() {
        ReportDeliveryFailureRequest req = new ReportDeliveryFailureRequest("RECIPIENT_UNREACHABLE");

        assertThatThrownBy(() -> assignmentService.reportDeliveryFailure(driver1Token, "NON-EXISTENT-ORDER", req))
                .isInstanceOf(AssignmentNotFoundException.class)
                .hasMessageContaining("No assignment record found");
    }

    @Test
    @DisplayName("Missing or invalid Authorization header throws BadCredentialsException")
    void missingOrInvalidAuthHeaderThrowsBadCredentials() {
        ReportDeliveryFailureRequest req = new ReportDeliveryFailureRequest("RECIPIENT_UNREACHABLE");

        assertThatThrownBy(() -> assignmentService.reportDeliveryFailure(null, "ORD-F7", req))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Missing or invalid Authorization header");

        assertThatThrownBy(() -> assignmentService.reportDeliveryFailure("InvalidHeader", "ORD-F7", req))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Missing or invalid Authorization header");

        assertThatThrownBy(() -> assignmentService.reportDeliveryFailure("Bearer invalid.token", "ORD-F7", req))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Token is invalid or expired");
    }
}
