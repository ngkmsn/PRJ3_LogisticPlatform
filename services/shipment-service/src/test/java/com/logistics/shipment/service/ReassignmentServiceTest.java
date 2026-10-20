package com.logistics.shipment.service;

import com.logistics.common.security.JwtTokenProvider;
import com.logistics.common.security.Role;
import com.logistics.shipment.dto.ReassignDriverRequest;
import com.logistics.shipment.entity.Assignment;
import com.logistics.shipment.exception.AccessDeniedException;
import com.logistics.shipment.exception.AssignmentNotFoundException;
import com.logistics.shipment.model.AssignmentStatus;
import com.logistics.shipment.publisher.DriverAssignedEventPublisher;
import com.logistics.shipment.repository.AssignmentRepository;
import io.quarkus.test.TestTransaction;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@QuarkusTest
class ReassignmentServiceTest {

    @Inject
    AssignmentRepository assignmentRepository;

    @Inject
    AssignmentService assignmentService;

    @Inject
    JwtTokenProvider jwtTokenProvider;

    @InjectSpy
    DriverAssignedEventPublisher eventPublisher;

    private String dispatcherToken;
    private String adminToken;
    private String customerToken;

    @BeforeEach
    @Transactional
    void setUp() {
        assignmentRepository.deleteAll();

        dispatcherToken = "Bearer " + jwtTokenProvider.generateToken(
                "disp-1", "dispatcher", "disp@test.com", Role.DISPATCHER.name()
        );
        adminToken = "Bearer " + jwtTokenProvider.generateToken(
                "admin-1", "admin", "admin@test.com", Role.ADMIN.name()
        );
        customerToken = "Bearer " + jwtTokenProvider.generateToken(
                "cust-1", "customer", "cust@test.com", Role.CUSTOMER.name()
        );
    }

    private Assignment persistAssignment(String orderId, AssignmentStatus status, String driverId, Instant time) {
        Assignment a = new Assignment();
        a.setId(UUID.randomUUID().toString());
        a.setOrderId(orderId);
        a.setOrderNumber("ORD-" + orderId);
        a.setCustomerId("cust-" + orderId);
        a.setRecipientName("Recipient " + orderId);
        a.setRecipientPhone("0901234567");
        a.setDeliveryAddress("Delivery Address " + orderId);
        a.setTotalAmount(new BigDecimal("250000.00"));
        a.setStatus(status);
        a.setDriverId(driverId);
        a.setAssignedAt(status == AssignmentStatus.ASSIGNED ? time : null);
        a.setCreatedAt(time);
        a.setUpdatedAt(time);
        assignmentRepository.persist(a);
        return a;
    }

    @Test
    @TestTransaction
    @DisplayName("reassignDriver successfully transfers order from old driver to new driver and adjusts workload")
    void reassignDriver_success() {
        Instant now = Instant.now();
        persistAssignment("ORD-REASSIGN-1", AssignmentStatus.ASSIGNED, "DRV-OLD-1", now.minus(30, ChronoUnit.MINUTES));

        // Prior workload check
        assertThat(assignmentRepository.countActiveAssignmentsByDriverId("DRV-OLD-1")).isEqualTo(1);
        assertThat(assignmentRepository.countActiveAssignmentsByDriverId("DRV-NEW-2")).isEqualTo(0);

        ReassignDriverRequest request = new ReassignDriverRequest("DRV-NEW-2", "Driver vehicle breakdown", "Urgent dispatch");
        Assignment reassigned = assignmentService.reassignDriver(dispatcherToken, "ORD-REASSIGN-1", request);

        assertThat(reassigned).isNotNull();
        assertThat(reassigned.getOrderId()).isEqualTo("ORD-REASSIGN-1");
        assertThat(reassigned.getStatus()).isEqualTo(AssignmentStatus.ASSIGNED);
        assertThat(reassigned.getPreviousDriverId()).isEqualTo("DRV-OLD-1");
        assertThat(reassigned.getDriverId()).isEqualTo("DRV-NEW-2");
        assertThat(reassigned.getReassignReason()).isEqualTo("Driver vehicle breakdown");
        assertThat(reassigned.getReassignedBy()).isEqualTo("dispatcher");
        assertThat(reassigned.getAssignedBy()).isEqualTo("dispatcher");
        assertThat(reassigned.getAssignmentSource()).isEqualTo("MANUAL");
        assertThat(reassigned.getReassignedAt()).isNotNull();
        assertThat(reassigned.getNotes()).contains("[Reassigned] Urgent dispatch");

        // Database verification
        Assignment fromDb = assignmentRepository.findByOrderId("ORD-REASSIGN-1").orElseThrow();
        assertThat(fromDb.getDriverId()).isEqualTo("DRV-NEW-2");
        assertThat(fromDb.getPreviousDriverId()).isEqualTo("DRV-OLD-1");

        // Workload shift verification: DRV-OLD-1 decrements from 1 to 0; DRV-NEW-2 increments from 0 to 1
        assertThat(assignmentRepository.countActiveAssignmentsByDriverId("DRV-OLD-1")).isEqualTo(0);
        assertThat(assignmentRepository.countActiveAssignmentsByDriverId("DRV-NEW-2")).isEqualTo(1);

        // Verify event publishing
        verify(eventPublisher, times(1)).publishDriverReassigned(any(Assignment.class), eq("DRV-OLD-1"), eq("Driver vehicle breakdown"));
    }

    @Test
    @TestTransaction
    @DisplayName("reassignDriver throws IllegalArgumentException when new driver is same as current driver")
    void reassignDriver_sameDriver_throwsIllegalArgument() {
        Instant now = Instant.now();
        persistAssignment("ORD-SAME", AssignmentStatus.ASSIGNED, "DRV-SAME", now.minus(10, ChronoUnit.MINUTES));

        ReassignDriverRequest request = new ReassignDriverRequest("DRV-SAME", "Mistake");
        assertThatThrownBy(() -> assignmentService.reassignDriver(dispatcherToken, "ORD-SAME", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("New driver cannot be the same as the currently assigned driver");

        verify(eventPublisher, Mockito.never()).publishDriverReassigned(any(), any(), any());
    }

    @Test
    @TestTransaction
    @DisplayName("reassignDriver throws IllegalStateException when order is not in ASSIGNED status")
    void reassignDriver_notAssigned_throwsIllegalState() {
        Instant now = Instant.now();
        persistAssignment("ORD-UNASSIGNED", AssignmentStatus.UNASSIGNED, null, now.minus(10, ChronoUnit.MINUTES));

        ReassignDriverRequest request = new ReassignDriverRequest("DRV-NEW-2", "Reason");
        assertThatThrownBy(() -> assignmentService.reassignDriver(dispatcherToken, "ORD-UNASSIGNED", request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not have an active assignment");
    }

    @Test
    @TestTransaction
    @DisplayName("reassignDriver throws IllegalStateException when order is in CANCELLED status")
    void reassignDriver_cancelledOrder_throwsIllegalState() {
        Instant now = Instant.now();
        persistAssignment("ORD-CANCELLED", AssignmentStatus.CANCELLED, "DRV-1", now.minus(10, ChronoUnit.MINUTES));

        ReassignDriverRequest request = new ReassignDriverRequest("DRV-NEW-2", "Reason");
        assertThatThrownBy(() -> assignmentService.reassignDriver(dispatcherToken, "ORD-CANCELLED", request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CANCELLED");
    }

    @Test
    @TestTransaction
    @DisplayName("reassignDriver throws IllegalArgumentException when new driver is invalid or offline")
    void reassignDriver_invalidDriver_throwsIllegalArgument() {
        Instant now = Instant.now();
        persistAssignment("ORD-VALID", AssignmentStatus.ASSIGNED, "DRV-OLD-1", now.minus(10, ChronoUnit.MINUTES));

        ReassignDriverRequest request = new ReassignDriverRequest("INVALID", "Reason");
        assertThatThrownBy(() -> assignmentService.reassignDriver(dispatcherToken, "ORD-VALID", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not exist or is not available");
    }

    @Test
    @TestTransaction
    @DisplayName("reassignDriver throws AssignmentNotFoundException when order does not exist")
    void reassignDriver_orderNotFound_throwsNotFound() {
        ReassignDriverRequest request = new ReassignDriverRequest("DRV-NEW-2", "Reason");
        assertThatThrownBy(() -> assignmentService.reassignDriver(adminToken, "NON-EXISTENT", request))
                .isInstanceOf(AssignmentNotFoundException.class)
                .hasMessageContaining("NON-EXISTENT");
    }

    @Test
    @DisplayName("reassignDriver throws AccessDeniedException when caller has CUSTOMER role")
    void reassignDriver_customerRole_throwsAccessDenied() {
        ReassignDriverRequest request = new ReassignDriverRequest("DRV-NEW-2", "Reason");
        assertThatThrownBy(() -> assignmentService.reassignDriver(customerToken, "ORD-1", request))
                .isInstanceOf(AccessDeniedException.class);
    }
}
