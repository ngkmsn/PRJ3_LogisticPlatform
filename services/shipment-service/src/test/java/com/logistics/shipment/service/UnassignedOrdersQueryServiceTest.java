package com.logistics.shipment.service;

import com.logistics.common.security.JwtTokenProvider;
import com.logistics.common.security.Role;
import com.logistics.shipment.dto.PagedResponse;
import com.logistics.shipment.dto.UnassignedOrderDto;
import com.logistics.shipment.entity.Assignment;
import com.logistics.shipment.exception.AccessDeniedException;
import com.logistics.shipment.exception.AssignmentNotFoundException;
import com.logistics.shipment.exception.BadCredentialsException;
import com.logistics.shipment.model.AssignmentStatus;
import com.logistics.shipment.repository.AssignmentRepository;
import io.quarkus.test.TestTransaction;
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
class UnassignedOrdersQueryServiceTest {

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

    private Assignment persistAssignment(String orderId, AssignmentStatus status, String driverId,
                                         String unassignedReason, Instant unassignedAt, Instant createdAt) {
        Assignment a = new Assignment();
        a.setId(UUID.randomUUID().toString());
        a.setOrderId(orderId);
        a.setOrderNumber("ORD-" + orderId);
        a.setCustomerId("cust-" + orderId);
        a.setRecipientName("Recipient " + orderId);
        a.setRecipientPhone("0901234567");
        a.setDeliveryAddress("Delivery Address " + orderId);
        a.setTotalAmount(new BigDecimal("150000.00"));
        a.setStatus(status);
        a.setDriverId(driverId);
        a.setUnassignedReason(unassignedReason);
        a.setUnassignedAt(unassignedAt);
        a.setCreatedAt(createdAt);
        a.setUpdatedAt(createdAt);
        assignmentRepository.persist(a);
        return a;
    }

    @Test
    @TestTransaction
    @DisplayName("getUnassignedOrders returns paginated results and strictly excludes ASSIGNED orders")
    void getUnassignedOrders_paginationAndIsolation() {
        Instant now = Instant.now();

        // 3 unassigned orders
        persistAssignment("ORD-UN-1", AssignmentStatus.UNASSIGNED, null, "NO_ELIGIBLE_DRIVER", now.minus(30, ChronoUnit.MINUTES), now.minus(35, ChronoUnit.MINUTES));
        persistAssignment("ORD-UN-2", AssignmentStatus.UNASSIGNED, null, "ALL_DRIVERS_BUSY", now.minus(20, ChronoUnit.MINUTES), now.minus(25, ChronoUnit.MINUTES));
        persistAssignment("ORD-UN-3", AssignmentStatus.UNASSIGNED, null, "NO_ELIGIBLE_DRIVER", now.minus(10, ChronoUnit.MINUTES), now.minus(15, ChronoUnit.MINUTES));

        // 2 assigned orders (from PB-031)
        persistAssignment("ORD-AS-1", AssignmentStatus.ASSIGNED, "DRV-100", null, null, now.minus(40, ChronoUnit.MINUTES));
        persistAssignment("ORD-AS-2", AssignmentStatus.ASSIGNED, "DRV-101", null, null, now.minus(50, ChronoUnit.MINUTES));

        // Page 0 with size 2
        PagedResponse<UnassignedOrderDto> page0 = assignmentService.getUnassignedOrders(dispatcherToken, 0, 2, "UNASSIGNED", "oldest_first");
        assertThat(page0.getContent()).hasSize(2);
        assertThat(page0.getTotalElements()).isEqualTo(3);
        assertThat(page0.getTotalPages()).isEqualTo(2);
        assertThat(page0.getPage()).isEqualTo(0);

        // Verify none of the items are ASSIGNED
        assertThat(page0.getContent())
                .extracting(UnassignedOrderDto::getOrderId)
                .containsExactly("ORD-UN-1", "ORD-UN-2")
                .doesNotContain("ORD-AS-1", "ORD-AS-2");

        // Page 1 with size 2
        PagedResponse<UnassignedOrderDto> page1 = assignmentService.getUnassignedOrders(dispatcherToken, 1, 2, "UNASSIGNED", "oldest_first");
        assertThat(page1.getContent()).hasSize(1);
        assertThat(page1.getContent().get(0).getOrderId()).isEqualTo("ORD-UN-3");
    }

    @Test
    @TestTransaction
    @DisplayName("getUnassignedOrders sorts oldest_first vs newest_first properly")
    void getUnassignedOrders_sorting() {
        Instant now = Instant.now();

        persistAssignment("ORD-SORT-OLD", AssignmentStatus.UNASSIGNED, null, "NO_ELIGIBLE_DRIVER", now.minus(60, ChronoUnit.MINUTES), now.minus(65, ChronoUnit.MINUTES));
        persistAssignment("ORD-SORT-MID", AssignmentStatus.UNASSIGNED, null, "ALL_DRIVERS_BUSY", now.minus(30, ChronoUnit.MINUTES), now.minus(35, ChronoUnit.MINUTES));
        persistAssignment("ORD-SORT-NEW", AssignmentStatus.UNASSIGNED, null, "NO_ELIGIBLE_DRIVER", now.minus(5, ChronoUnit.MINUTES), now.minus(10, ChronoUnit.MINUTES));

        // Oldest first: ORD-SORT-OLD, then MID, then NEW
        PagedResponse<UnassignedOrderDto> oldest = assignmentService.getUnassignedOrders(dispatcherToken, 0, 10, "UNASSIGNED", "oldest_first");
        assertThat(oldest.getContent())
                .extracting(UnassignedOrderDto::getOrderId)
                .containsSubsequence("ORD-SORT-OLD", "ORD-SORT-MID", "ORD-SORT-NEW");

        // Newest first: ORD-SORT-NEW, then MID, then OLD
        PagedResponse<UnassignedOrderDto> newest = assignmentService.getUnassignedOrders(dispatcherToken, 0, 10, "UNASSIGNED", "newest_first");
        assertThat(newest.getContent())
                .extracting(UnassignedOrderDto::getOrderId)
                .containsSubsequence("ORD-SORT-NEW", "ORD-SORT-MID", "ORD-SORT-OLD");
    }

    @Test
    @TestTransaction
    @DisplayName("getUnassignedOrderDetail returns order details or throws AssignmentNotFoundException")
    void getUnassignedOrderDetail_successAndNotFound() {
        Instant now = Instant.now();
        persistAssignment("ORD-DETAIL-1", AssignmentStatus.UNASSIGNED, null, "NO_ELIGIBLE_DRIVER", now.minus(15, ChronoUnit.MINUTES), now.minus(20, ChronoUnit.MINUTES));
        persistAssignment("ORD-DETAIL-ASSIGNED", AssignmentStatus.ASSIGNED, "DRV-50", null, null, now.minus(10, ChronoUnit.MINUTES));

        // Successful lookup
        UnassignedOrderDto dto = assignmentService.getUnassignedOrderDetail(adminToken, "ORD-DETAIL-1");
        assertThat(dto).isNotNull();
        assertThat(dto.getOrderId()).isEqualTo("ORD-DETAIL-1");
        assertThat(dto.getUnassignedReason()).isEqualTo("NO_ELIGIBLE_DRIVER");
        assertThat(dto.getDropoffAddress()).isEqualTo("Delivery Address ORD-DETAIL-1");
        assertThat(dto.getWaitingTimeSeconds()).isGreaterThanOrEqualTo(15 * 60L);

        // Assigned order is not considered unassigned -> throws AssignmentNotFoundException
        assertThatThrownBy(() -> assignmentService.getUnassignedOrderDetail(adminToken, "ORD-DETAIL-ASSIGNED"))
                .isInstanceOf(AssignmentNotFoundException.class)
                .hasMessageContaining("ORD-DETAIL-ASSIGNED");

        // Non-existent order -> throws AssignmentNotFoundException
        assertThatThrownBy(() -> assignmentService.getUnassignedOrderDetail(adminToken, "ORD-NON-EXISTENT"))
                .isInstanceOf(AssignmentNotFoundException.class);
    }

    @Test
    @DisplayName("Security checks: invalid token or non-operations role throws appropriate exception")
    void securityChecks() {
        // Missing auth
        assertThatThrownBy(() -> assignmentService.getUnassignedOrders(null, 0, 10, "UNASSIGNED", "oldest_first"))
                .isInstanceOf(BadCredentialsException.class);

        // Invalid format
        assertThatThrownBy(() -> assignmentService.getUnassignedOrders("NotBearer token", 0, 10, "UNASSIGNED", "oldest_first"))
                .isInstanceOf(BadCredentialsException.class);

        // Malformed token
        assertThatThrownBy(() -> assignmentService.getUnassignedOrders("Bearer invalid.jwt.token", 0, 10, "UNASSIGNED", "oldest_first"))
                .isInstanceOf(BadCredentialsException.class);

        // Customer role forbidden
        assertThatThrownBy(() -> assignmentService.getUnassignedOrders(customerToken, 0, 10, "UNASSIGNED", "oldest_first"))
                .isInstanceOf(AccessDeniedException.class);

        // Driver role forbidden
        assertThatThrownBy(() -> assignmentService.getUnassignedOrders(driverToken, 0, 10, "UNASSIGNED", "oldest_first"))
                .isInstanceOf(AccessDeniedException.class);
    }
}
