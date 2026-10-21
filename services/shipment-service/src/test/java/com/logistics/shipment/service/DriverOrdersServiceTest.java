package com.logistics.shipment.service;

import com.logistics.common.security.JwtTokenProvider;
import com.logistics.common.security.Role;
import com.logistics.shipment.dto.DriverOrderDto;
import com.logistics.shipment.dto.PagedResponse;
import com.logistics.shipment.entity.Assignment;
import com.logistics.shipment.exception.AccessDeniedException;
import com.logistics.shipment.exception.BadCredentialsException;
import com.logistics.shipment.model.AssignmentStatus;
import com.logistics.shipment.repository.AssignmentRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@QuarkusTest
class DriverOrdersServiceTest {

    @Inject
    AssignmentRepository assignmentRepository;

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
    @DisplayName("Driver can view their own assigned orders with safe fields")
    void driverCanViewOwnAssignedOrders() {
        persistAssignment("ORD-001", "NUM-001", driver1UserId, AssignmentStatus.ASSIGNED);
        persistAssignment("ORD-002", "NUM-002", driver1UserId, AssignmentStatus.ASSIGNED);

        PagedResponse<DriverOrderDto> response = assignmentService.getDriverAssignedOrders(driver1Token, 0, 10, null);

        assertThat(response).isNotNull();
        assertThat(response.getTotalElements()).isEqualTo(2);
        assertThat(response.getContent()).hasSize(2);

        DriverOrderDto dto = response.getContent().get(0);
        assertThat(dto.getOrderId()).isIn("ORD-001", "ORD-002");
        assertThat(dto.getRecipientName()).isEqualTo("Tran Van B");
        assertThat(dto.getRecipientPhone()).isEqualTo("0912345678");
        assertThat(dto.getDeliveryAddress()).isEqualTo("456 Nguyen Trai, Q5, TP.HCM");
        assertThat(dto.getPickupAddress()).isNotNull();
        assertThat(dto.getTotalAmount()).isEqualByComparingTo("150000.00");
        assertThat(dto.getStatus()).isEqualTo(AssignmentStatus.ASSIGNED);
        assertThat(dto.getNotes()).isEqualTo("Handle with care");
    }

    @Test
    @Transactional
    @DisplayName("Driver cannot view orders assigned to other drivers or unassigned orders")
    void driverCannotViewOrdersAssignedToOtherDrivers() {
        // Given orders for driver 1, driver 2, and unassigned
        persistAssignment("ORD-DRV1", "NUM-DRV1", driver1UserId, AssignmentStatus.ASSIGNED);
        persistAssignment("ORD-DRV2", "NUM-DRV2", driver2UserId, AssignmentStatus.ASSIGNED);
        persistAssignment("ORD-UNASSIGNED", "NUM-UNASSIGNED", null, AssignmentStatus.UNASSIGNED);

        // When Driver 1 requests orders
        PagedResponse<DriverOrderDto> response = assignmentService.getDriverAssignedOrders(driver1Token, 0, 10, null);

        // Then only Driver 1's orders are returned
        assertThat(response.getTotalElements()).isEqualTo(1);
        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getOrderId()).isEqualTo("ORD-DRV1");

        // When Driver 2 requests orders
        PagedResponse<DriverOrderDto> response2 = assignmentService.getDriverAssignedOrders(driver2Token, 0, 10, null);

        // Then only Driver 2's orders are returned
        assertThat(response2.getTotalElements()).isEqualTo(1);
        assertThat(response2.getContent().get(0).getOrderId()).isEqualTo("ORD-DRV2");
    }

    @Test
    @Transactional
    @DisplayName("Driver with no assigned orders returns empty list")
    void driverWithNoAssignedOrdersReturnsEmptyList() {
        // Driver 2 has orders, but Driver 1 has none
        persistAssignment("ORD-DRV2", "NUM-DRV2", driver2UserId, AssignmentStatus.ASSIGNED);

        PagedResponse<DriverOrderDto> response = assignmentService.getDriverAssignedOrders(driver1Token, 0, 10, null);

        assertThat(response).isNotNull();
        assertThat(response.getTotalElements()).isEqualTo(0);
        assertThat(response.getContent()).isEmpty();
    }

    @Test
    @DisplayName("Non-driver roles (CUSTOMER, DISPATCHER, ADMIN) are rejected with 403 Forbidden")
    void nonDriverRolesRejectedWith403() {
        assertThatThrownBy(() -> assignmentService.getDriverAssignedOrders(customerToken, 0, 10, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only drivers are permitted");

        assertThatThrownBy(() -> assignmentService.getDriverAssignedOrders(dispatcherToken, 0, 10, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only drivers are permitted");

        assertThatThrownBy(() -> assignmentService.getDriverAssignedOrders(adminToken, 0, 10, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only drivers are permitted");
    }

    @Test
    @DisplayName("Unauthenticated or invalid token is rejected with 401 BadCredentialsException")
    void unauthenticatedOrInvalidTokenRejectedWith401() {
        assertThatThrownBy(() -> assignmentService.getDriverAssignedOrders(null, 0, 10, null))
                .isInstanceOf(BadCredentialsException.class);

        assertThatThrownBy(() -> assignmentService.getDriverAssignedOrders("InvalidHeader", 0, 10, null))
                .isInstanceOf(BadCredentialsException.class);

        assertThatThrownBy(() -> assignmentService.getDriverAssignedOrders("Bearer invalid.jwt.token", 0, 10, null))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @Transactional
    @DisplayName("Filter by status works correctly and rejects invalid status")
    void filterByStatusWorksAndRejectsInvalidStatus() {
        persistAssignment("ORD-A1", "NUM-A1", driver1UserId, AssignmentStatus.ASSIGNED);
        persistAssignment("ORD-A2", "NUM-A2", driver1UserId, AssignmentStatus.CANCELLED);

        // Filter by ASSIGNED
        PagedResponse<DriverOrderDto> assignedOnly = assignmentService.getDriverAssignedOrders(driver1Token, 0, 10, "ASSIGNED");
        assertThat(assignedOnly.getContent()).hasSize(1);
        assertThat(assignedOnly.getContent().get(0).getOrderId()).isEqualTo("ORD-A1");

        // Invalid status filter throws IllegalArgumentException
        assertThatThrownBy(() -> assignmentService.getDriverAssignedOrders(driver1Token, 0, 10, "UNKNOWN_STATUS"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid assignment status");
    }

    @Test
    @Transactional
    @DisplayName("Pagination operates correctly")
    void paginationWorksCorrectly() {
        for (int i = 1; i <= 5; i++) {
            persistAssignment("ORD-" + i, "NUM-" + i, driver1UserId, AssignmentStatus.ASSIGNED);
        }

        // Page 0, size 2
        PagedResponse<DriverOrderDto> page0 = assignmentService.getDriverAssignedOrders(driver1Token, 0, 2, null);
        assertThat(page0.getContent()).hasSize(2);
        assertThat(page0.getTotalElements()).isEqualTo(5);
        assertThat(page0.getTotalPages()).isEqualTo(3);
        assertThat(page0.getPage()).isEqualTo(0);

        // Page 1, size 2
        PagedResponse<DriverOrderDto> page1 = assignmentService.getDriverAssignedOrders(driver1Token, 1, 2, null);
        assertThat(page1.getContent()).hasSize(2);
        assertThat(page1.getPage()).isEqualTo(1);

        // Page 2, size 2
        PagedResponse<DriverOrderDto> page2 = assignmentService.getDriverAssignedOrders(driver1Token, 2, 2, null);
        assertThat(page2.getContent()).hasSize(1);
    }

    @Test
    @Transactional
    @DisplayName("Viewing assigned orders does not mutate Order or Assignment state")
    void viewingOrdersDoesNotMutateState() {
        Assignment a = persistAssignment("ORD-READONLY", "NUM-RO", driver1UserId, AssignmentStatus.ASSIGNED);
        Instant updatedAtBefore = a.getUpdatedAt();

        assignmentService.getDriverAssignedOrders(driver1Token, 0, 10, null);

        Assignment after = assignmentRepository.findByOrderId("ORD-READONLY").orElseThrow();
        assertThat(after.getStatus()).isEqualTo(AssignmentStatus.ASSIGNED);
        assertThat(after.getDriverId()).isEqualTo(driver1UserId);
        assertThat(after.getUpdatedAt()).isEqualTo(updatedAtBefore);
    }
}
