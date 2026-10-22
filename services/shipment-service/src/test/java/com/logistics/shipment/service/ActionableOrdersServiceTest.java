package com.logistics.shipment.service;

import com.logistics.common.dto.ActionableOrderDto;
import com.logistics.common.dto.ActionableOrdersPageDto;
import com.logistics.common.security.JwtTokenProvider;
import com.logistics.common.security.Role;
import com.logistics.shipment.entity.Assignment;
import com.logistics.shipment.exception.AccessDeniedException;
import com.logistics.shipment.exception.AssignmentNotFoundException;
import com.logistics.shipment.exception.BadCredentialsException;
import com.logistics.shipment.model.AssignmentStatus;
import com.logistics.shipment.repository.AssignmentRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@QuarkusTest
class ActionableOrdersServiceTest {

    @Inject
    AssignmentRepository assignmentRepository;

    @Inject
    AssignmentService assignmentService;

    @Inject
    JwtTokenProvider jwtTokenProvider;

    private String dispatcherToken;
    private String adminToken;
    private String customerToken;
    private String driverToken;

    @BeforeEach
    @jakarta.transaction.Transactional
    void setUp() {
        assignmentRepository.deleteAll();

        dispatcherToken = "Bearer " + jwtTokenProvider.generateToken("disp-1", "dispatcher", "disp@test.com", Role.DISPATCHER.name());
        adminToken = "Bearer " + jwtTokenProvider.generateToken("admin-1", "admin", "admin@test.com", Role.ADMIN.name());
        customerToken = "Bearer " + jwtTokenProvider.generateToken("cust-1", "customer", "cust@test.com", Role.CUSTOMER.name());
        driverToken = "Bearer " + jwtTokenProvider.generateToken("drv-1", "driver", "drv@test.com", Role.DRIVER.name());
    }

    private Assignment persistAssignment(String orderId, String orderNumber, AssignmentStatus status,
                                         String driverId, String deliveryAddress,
                                         String unassignedReason, Instant unassignedAt,
                                         String failureReason, Instant failedAt,
                                         Instant createdAt) {
        Assignment a = new Assignment();
        a.setId(UUID.randomUUID().toString());
        a.setOrderId(orderId);
        a.setOrderNumber(orderNumber);
        a.setCustomerId("cust-uuid");
        a.setRecipientName("Recipient " + orderNumber);
        a.setRecipientPhone("0901234567");
        a.setDeliveryAddress(deliveryAddress);
        a.setTotalAmount(new BigDecimal("150000.00"));
        a.setStatus(status);
        a.setDriverId(driverId);
        a.setUnassignedReason(unassignedReason);
        a.setUnassignedAt(unassignedAt);
        a.setFailureReason(failureReason);
        a.setFailedAt(failedAt);
        a.setCreatedAt(createdAt);
        a.setUpdatedAt(createdAt);
        assignmentRepository.persist(a);
        return a;
    }

    @Test
    @DisplayName("getActionableOrders consolidates UNASSIGNED and FAILED orders, excluding other statuses")
    @jakarta.transaction.Transactional
    void getActionableOrders_all_consolidatesCorrectly() {
        Instant now = Instant.now();

        // 2 Unassigned orders
        persistAssignment("ord-act-1", "ORD-ACT-1", AssignmentStatus.UNASSIGNED, null,
                "123 Le Loi, D1", "NO_ELIGIBLE_DRIVER", now.minus(30, ChronoUnit.MINUTES), null, null, now.minus(35, ChronoUnit.MINUTES));
        persistAssignment("ord-act-2", "ORD-ACT-2", AssignmentStatus.UNASSIGNED, null,
                "456 Nguyen Hue, D1", "DRIVER_TIMEOUT", now.minus(20, ChronoUnit.MINUTES), null, null, now.minus(25, ChronoUnit.MINUTES));

        // 2 Failed delivery orders
        persistAssignment("ord-act-3", "ORD-ACT-3", AssignmentStatus.FAILED, "drv-101",
                "789 Tran Hung Dao, D5", null, null, "Customer unreachable", now.minus(15, ChronoUnit.MINUTES), now.minus(60, ChronoUnit.MINUTES));
        persistAssignment("ord-act-4", "ORD-ACT-4", AssignmentStatus.FAILED, "drv-102",
                "321 Ba Thang Hai, D10", null, null, "Wrong delivery address", now.minus(10, ChronoUnit.MINUTES), now.minus(50, ChronoUnit.MINUTES));

        // Non-actionable orders: ASSIGNED, DELIVERED, CANCELLED
        persistAssignment("ord-act-5", "ORD-ACT-5", AssignmentStatus.ASSIGNED, "drv-103",
                "999 Cach Mang Thang 8, Tan Binh", null, null, null, null, now.minus(40, ChronoUnit.MINUTES));
        persistAssignment("ord-act-6", "ORD-ACT-6", AssignmentStatus.DELIVERED, "drv-104",
                "888 Vo Van Kiet, D1", null, null, null, null, now.minus(120, ChronoUnit.MINUTES));
        persistAssignment("ord-act-7", "ORD-ACT-7", AssignmentStatus.CANCELLED, null,
                "777 Hoang Dieu, D4", null, null, null, null, now.minus(100, ChronoUnit.MINUTES));

        ActionableOrdersPageDto result = assignmentService.getActionableOrders(
                dispatcherToken, "ALL", null, null, 0, 10, "newest_first");

        assertThat(result.getContent()).hasSize(4);
        assertThat(result.getTotalElements()).isEqualTo(4);
        assertThat(result.getTotalActionable()).isEqualTo(4);
        assertThat(result.getUnassignedCount()).isEqualTo(2);
        assertThat(result.getFailedCount()).isEqualTo(2);

        // Verify ordering: newest failure first
        ActionableOrderDto first = result.getContent().get(0);
        assertThat(first.getOrderId()).isEqualTo("ord-act-4");
        assertThat(first.getStatus()).isEqualTo("FAILED");
        assertThat(first.getIssueSeverity()).isEqualTo("CRITICAL");
        assertThat(first.getAllowedActions()).contains("REASSIGN");

        // Verify unassigned item properties
        ActionableOrderDto unassigned = result.getContent().stream()
                .filter(x -> "ord-act-1".equals(x.getOrderId()))
                .findFirst()
                .orElseThrow();
        assertThat(unassigned.getStatus()).isEqualTo("UNASSIGNED");
        assertThat(unassigned.getIssueSeverity()).isEqualTo("HIGH");
        assertThat(unassigned.getUnassignedReason()).isEqualTo("NO_ELIGIBLE_DRIVER");
        assertThat(unassigned.getAllowedActions()).contains("ASSIGN");
    }

    @Test
    @DisplayName("getActionableOrders category filter filters accurately by UNASSIGNED or FAILED")
    @jakarta.transaction.Transactional
    void getActionableOrders_categoryFilter() {
        Instant now = Instant.now();
        persistAssignment("ord-u1", "ORD-U1", AssignmentStatus.UNASSIGNED, null, "Address 1", "REASON_1", now, null, null, now);
        persistAssignment("ord-u2", "ORD-U2", AssignmentStatus.UNASSIGNED, null, "Address 2", "REASON_2", now, null, null, now);
        persistAssignment("ord-f1", "ORD-F1", AssignmentStatus.FAILED, "drv-1", "Address 3", null, null, "Customer refused", now, now);

        // Filter UNASSIGNED only
        ActionableOrdersPageDto unassignedRes = assignmentService.getActionableOrders(
                dispatcherToken, "UNASSIGNED", null, null, 0, 10, "newest_first");
        assertThat(unassignedRes.getContent()).hasSize(2);
        assertThat(unassignedRes.getContent()).allMatch(o -> "UNASSIGNED".equals(o.getStatus()));
        assertThat(unassignedRes.getUnassignedCount()).isEqualTo(2);
        assertThat(unassignedRes.getFailedCount()).isEqualTo(1);

        // Filter FAILED only
        ActionableOrdersPageDto failedRes = assignmentService.getActionableOrders(
                dispatcherToken, "FAILED", null, null, 0, 10, "newest_first");
        assertThat(failedRes.getContent()).hasSize(1);
        assertThat(failedRes.getContent().get(0).getOrderId()).isEqualTo("ord-f1");
    }

    @Test
    @DisplayName("getActionableOrders search filters by orderNumber, address, or failure reason")
    @jakarta.transaction.Transactional
    void getActionableOrders_searchKeyword() {
        Instant now = Instant.now();
        persistAssignment("ord-x1", "ORD-VIP-999", AssignmentStatus.UNASSIGNED, null, "100 Nguyen Van Linh, D7", "OVERLOAD", now, null, null, now);
        persistAssignment("ord-x2", "ORD-STD-123", AssignmentStatus.FAILED, "drv-9", "200 Pham Van Dong, Thu Duc", null, null, "Package damaged", now, now);

        // Search by orderNumber
        ActionableOrdersPageDto res1 = assignmentService.getActionableOrders(
                adminToken, "ALL", null, "VIP-999", 0, 10, "newest_first");
        assertThat(res1.getContent()).hasSize(1);
        assertThat(res1.getContent().get(0).getOrderId()).isEqualTo("ord-x1");

        // Search by address
        ActionableOrdersPageDto res2 = assignmentService.getActionableOrders(
                adminToken, "ALL", null, "Thu Duc", 0, 10, "newest_first");
        assertThat(res2.getContent()).hasSize(1);
        assertThat(res2.getContent().get(0).getOrderId()).isEqualTo("ord-x2");

        // Search by failure reason
        ActionableOrdersPageDto res3 = assignmentService.getActionableOrders(
                adminToken, "ALL", null, "damaged", 0, 10, "newest_first");
        assertThat(res3.getContent()).hasSize(1);
        assertThat(res3.getContent().get(0).getOrderId()).isEqualTo("ord-x2");
    }

    @Test
    @DisplayName("getActionableOrders pagination partitions records accurately")
    @jakarta.transaction.Transactional
    void getActionableOrders_pagination() {
        Instant now = Instant.now();
        for (int i = 1; i <= 7; i++) {
            persistAssignment("ord-p" + i, "ORD-P" + i, AssignmentStatus.UNASSIGNED, null,
                    "Address " + i, "REASON", now.minus(i, ChronoUnit.MINUTES), null, null, now.minus(i, ChronoUnit.MINUTES));
        }

        // Page 0 size 3
        ActionableOrdersPageDto page0 = assignmentService.getActionableOrders(
                dispatcherToken, "ALL", null, null, 0, 3, "newest_first");
        assertThat(page0.getContent()).hasSize(3);
        assertThat(page0.getPage()).isEqualTo(0);
        assertThat(page0.getTotalElements()).isEqualTo(7);
        assertThat(page0.getTotalPages()).isEqualTo(3);

        // Page 2 size 3 (last page with 1 element)
        ActionableOrdersPageDto page2 = assignmentService.getActionableOrders(
                dispatcherToken, "ALL", null, null, 2, 3, "newest_first");
        assertThat(page2.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("getActionableOrderDetail returns full detail for UNASSIGNED and FAILED orders")
    @jakarta.transaction.Transactional
    void getActionableOrderDetail_success() {
        Instant now = Instant.now();
        persistAssignment("ord-detail-u", "ORD-DT-U", AssignmentStatus.UNASSIGNED, null,
                "123 Nguyen Trai", "NO_ELIGIBLE_DRIVER", now, null, null, now);
        persistAssignment("ord-detail-f", "ORD-DT-F", AssignmentStatus.FAILED, "drv-55",
                "456 Tran Phu", null, null, "Customer unreachable", now, now);

        ActionableOrderDto unassigned = assignmentService.getActionableOrderDetail(dispatcherToken, "ord-detail-u");
        assertThat(unassigned.getOrderId()).isEqualTo("ord-detail-u");
        assertThat(unassigned.getStatus()).isEqualTo("UNASSIGNED");
        assertThat(unassigned.getAllowedActions()).contains("ASSIGN");

        ActionableOrderDto failed = assignmentService.getActionableOrderDetail(dispatcherToken, "ord-detail-f");
        assertThat(failed.getOrderId()).isEqualTo("ord-detail-f");
        assertThat(failed.getStatus()).isEqualTo("FAILED");
        assertThat(failed.getAllowedActions()).contains("REASSIGN");
    }

    @Test
    @DisplayName("getActionableOrderDetail throws AssignmentNotFoundException when order was already handled (ASSIGNED/DELIVERED)")
    @jakarta.transaction.Transactional
    void getActionableOrderDetail_alreadyProcessed_throwsNotFound() {
        Instant now = Instant.now();
        persistAssignment("ord-handled", "ORD-HANDLED", AssignmentStatus.ASSIGNED, "drv-77",
                "789 Le Duan", null, null, null, null, now);

        assertThatThrownBy(() -> assignmentService.getActionableOrderDetail(dispatcherToken, "ord-handled"))
                .isInstanceOf(AssignmentNotFoundException.class)
                .hasMessageContaining("No actionable order found with orderId: ord-handled");
    }

    @Test
    @DisplayName("getActionableOrders rejects CUSTOMER and DRIVER with AccessDeniedException (403)")
    void getActionableOrders_forbiddenRoles() {
        assertThatThrownBy(() -> assignmentService.getActionableOrders(customerToken, "ALL", null, null, 0, 10, "newest_first"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only administrators and dispatchers are permitted to view actionable orders");

        assertThatThrownBy(() -> assignmentService.getActionableOrders(driverToken, "ALL", null, null, 0, 10, "newest_first"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only administrators and dispatchers are permitted to view actionable orders");
    }

    @Test
    @DisplayName("getActionableOrders rejects unauthenticated or invalid token with BadCredentialsException (401)")
    void getActionableOrders_unauthenticated() {
        assertThatThrownBy(() -> assignmentService.getActionableOrders(null, "ALL", null, null, 0, 10, "newest_first"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Missing or invalid Authorization header");

        assertThatThrownBy(() -> assignmentService.getActionableOrders("Bearer invalid.token", "ALL", null, null, 0, 10, "newest_first"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Token is invalid or expired");
    }
}
