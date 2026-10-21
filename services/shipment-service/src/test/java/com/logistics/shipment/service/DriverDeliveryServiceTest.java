package com.logistics.shipment.service;

import com.logistics.common.security.JwtTokenProvider;
import com.logistics.common.security.Role;
import com.logistics.shipment.dto.ConfirmDeliveryRequest;
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
class DriverDeliveryServiceTest {

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

    private final String driver1UserId = "drv-user-del-001";
    private final String driver2UserId = "drv-user-del-002";

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
        if (status == AssignmentStatus.PICKED_UP || status == AssignmentStatus.IN_TRANSIT || status == AssignmentStatus.DELIVERED) {
            a.setPickedUpAt(now);
            a.setPickupNotes("Package picked up");
        }
        if (status == AssignmentStatus.IN_TRANSIT || status == AssignmentStatus.DELIVERED) {
            a.setTransitStartedAt(now);
            a.setTransitNotes("Transit started");
        }
        if (status == AssignmentStatus.DELIVERED) {
            a.setDeliveredAt(now);
            a.setDeliveryNotes("Package delivered");
        }
        a.setNotes("Handle with care");
        a.setCreatedAt(now);
        a.setUpdatedAt(now);
        assignmentRepository.persist(a);
        return a;
    }

    @Test
    @Transactional
    @DisplayName("Assigned driver can confirm delivery successfully from IN_TRANSIT")
    void assignedDriverCanConfirmDeliverySuccessfully() {
        persistAssignment("ORD-D1", "NUM-D1", driver1UserId, AssignmentStatus.IN_TRANSIT);

        ConfirmDeliveryRequest req = new ConfirmDeliveryRequest(
                "Delivered in person to recipient",
                "Nguyen Van Recipient",
                "https://cdn.example.com/pod/ORD-D1.jpg"
        );
        Assignment result = assignmentService.confirmDelivery(driver1Token, "ORD-D1", req);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(AssignmentStatus.DELIVERED);
        assertThat(result.getDeliveredAt()).isNotNull();
        assertThat(result.getDeliveryNotes()).isEqualTo("Delivered in person to recipient");
        assertThat(result.getProofOfDelivery()).isEqualTo("https://cdn.example.com/pod/ORD-D1.jpg");

        // Verify history entry created
        List<AssignmentHistory> histories = assignmentHistoryRepository.findByOrderIdOrderByCreatedAtAsc("ORD-D1");
        assertThat(histories).hasSize(1);
        AssignmentHistory history = histories.get(0);
        assertThat(history.getActionType()).isEqualTo(AssignmentActionType.DELIVER);
        assertThat(history.getPerformedBy()).isIn(driver1UserId, "driver1");
        assertThat(history.getNewDriverId()).isEqualTo(driver1UserId);
        assertThat(history.getReason()).contains("Delivered in person to recipient");
    }

    @Test
    @Transactional
    @DisplayName("Confirm delivery succeeds with null request / empty notes")
    void confirmDeliveryWithEmptyNotes() {
        persistAssignment("ORD-D2", "NUM-D2", driver1UserId, AssignmentStatus.IN_TRANSIT);

        Assignment result = assignmentService.confirmDelivery(driver1Token, "ORD-D2", null);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(AssignmentStatus.DELIVERED);
        assertThat(result.getDeliveredAt()).isNotNull();
        assertThat(result.getDeliveryNotes()).isNull();

        List<AssignmentHistory> histories = assignmentHistoryRepository.findByOrderIdOrderByCreatedAtAsc("ORD-D2");
        assertThat(histories).hasSize(1);
        assertThat(histories.get(0).getActionType()).isEqualTo(AssignmentActionType.DELIVER);
    }

    @Test
    @Transactional
    @DisplayName("Confirm delivery is idempotent if already DELIVERED by same driver")
    void confirmDeliveryIdempotent() {
        persistAssignment("ORD-D3", "NUM-D3", driver1UserId, AssignmentStatus.IN_TRANSIT);

        ConfirmDeliveryRequest req1 = new ConfirmDeliveryRequest("First delivery call");
        Assignment firstResult = assignmentService.confirmDelivery(driver1Token, "ORD-D3", req1);
        Instant originalDeliveredAt = firstResult.getDeliveredAt();
        assertThat(originalDeliveredAt).isNotNull();

        // Second call with same driver
        ConfirmDeliveryRequest req2 = new ConfirmDeliveryRequest("Second redundant call");
        Assignment secondResult = assignmentService.confirmDelivery(driver1Token, "ORD-D3", req2);

        assertThat(secondResult.getStatus()).isEqualTo(AssignmentStatus.DELIVERED);
        assertThat(secondResult.getDeliveredAt()).isEqualTo(originalDeliveredAt);

        // Ensure no duplicate history records created on idempotent second call
        List<AssignmentHistory> histories = assignmentHistoryRepository.findByOrderIdOrderByCreatedAtAsc("ORD-D3");
        assertThat(histories).hasSize(1);
    }

    @Test
    @Transactional
    @DisplayName("Unassigned driver cannot confirm delivery (throws 403 AccessDeniedException)")
    void unassignedDriverCannotConfirmDelivery() {
        persistAssignment("ORD-D4", "NUM-D4", driver1UserId, AssignmentStatus.IN_TRANSIT);

        ConfirmDeliveryRequest req = new ConfirmDeliveryRequest("Attempted delivery by wrong driver");

        assertThatThrownBy(() -> assignmentService.confirmDelivery(driver2Token, "ORD-D4", req))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You are not the assigned driver");

        List<AssignmentHistory> histories = assignmentHistoryRepository.findByOrderIdOrderByCreatedAtAsc("ORD-D4");
        assertThat(histories).isEmpty();
    }

    @Test
    @Transactional
    @DisplayName("Non-DRIVER roles cannot confirm delivery (throws 403 AccessDeniedException)")
    void nonDriverRolesCannotConfirmDelivery() {
        persistAssignment("ORD-D5", "NUM-D5", driver1UserId, AssignmentStatus.IN_TRANSIT);

        ConfirmDeliveryRequest req = new ConfirmDeliveryRequest("Admin trying to deliver");

        assertThatThrownBy(() -> assignmentService.confirmDelivery(adminToken, "ORD-D5", req))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only drivers");

        assertThatThrownBy(() -> assignmentService.confirmDelivery(dispatcherToken, "ORD-D5", req))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only drivers");

        assertThatThrownBy(() -> assignmentService.confirmDelivery(customerToken, "ORD-D5", req))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only drivers");
    }

    @Test
    @Transactional
    @DisplayName("Confirm delivery throws IllegalStateException if order is not IN_TRANSIT (e.g. ASSIGNED or PICKED_UP)")
    void invalidStatusCannotConfirmDelivery() {
        persistAssignment("ORD-D6-ASSIGNED", "NUM-D6A", driver1UserId, AssignmentStatus.ASSIGNED);
        persistAssignment("ORD-D6-PICKED", "NUM-D6P", driver1UserId, AssignmentStatus.PICKED_UP);

        ConfirmDeliveryRequest req = new ConfirmDeliveryRequest("Delivery attempt");

        assertThatThrownBy(() -> assignmentService.confirmDelivery(driver1Token, "ORD-D6-ASSIGNED", req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expected IN_TRANSIT");

        assertThatThrownBy(() -> assignmentService.confirmDelivery(driver1Token, "ORD-D6-PICKED", req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expected IN_TRANSIT");
    }

    @Test
    @Transactional
    @DisplayName("Confirm delivery throws AssignmentNotFoundException if order does not exist")
    void nonExistentOrderThrowsNotFound() {
        ConfirmDeliveryRequest req = new ConfirmDeliveryRequest("Delivery non-existent order");

        assertThatThrownBy(() -> assignmentService.confirmDelivery(driver1Token, "NON-EXISTENT-ORDER", req))
                .isInstanceOf(AssignmentNotFoundException.class)
                .hasMessageContaining("No assignment record found");
    }

    @Test
    @DisplayName("Missing or invalid Authorization header throws BadCredentialsException")
    void missingOrInvalidAuthHeaderThrowsBadCredentials() {
        ConfirmDeliveryRequest req = new ConfirmDeliveryRequest("Delivery attempt");

        assertThatThrownBy(() -> assignmentService.confirmDelivery(null, "ORD-D7", req))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Missing or invalid Authorization header");

        assertThatThrownBy(() -> assignmentService.confirmDelivery("InvalidHeader", "ORD-D7", req))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Missing or invalid Authorization header");

        assertThatThrownBy(() -> assignmentService.confirmDelivery("Bearer invalid.jwt.token", "ORD-D7", req))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Token is invalid or expired");
    }
}
