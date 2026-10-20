package com.logistics.shipment.service;

import com.logistics.common.security.JwtTokenProvider;
import com.logistics.common.security.Role;
import com.logistics.shipment.dto.ManualAssignDriverRequest;
import com.logistics.shipment.entity.Assignment;
import com.logistics.shipment.exception.AccessDeniedException;
import com.logistics.shipment.exception.AssignmentNotFoundException;
import com.logistics.shipment.exception.DuplicateAssignmentException;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@QuarkusTest
class ManualAssignmentServiceTest {

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

    private Assignment persistAssignment(String orderId, AssignmentStatus status, String driverId,
                                         String unassignedReason, Instant time) {
        Assignment a = new Assignment();
        a.setId(UUID.randomUUID().toString());
        a.setOrderId(orderId);
        a.setOrderNumber("ORD-" + orderId);
        a.setCustomerId("cust-" + orderId);
        a.setRecipientName("Recipient " + orderId);
        a.setRecipientPhone("0901234567");
        a.setDeliveryAddress("Delivery Address " + orderId);
        a.setTotalAmount(new BigDecimal("180000.00"));
        a.setStatus(status);
        a.setDriverId(driverId);
        a.setUnassignedReason(unassignedReason);
        a.setUnassignedAt(status == AssignmentStatus.UNASSIGNED ? time : null);
        a.setAssignedAt(status == AssignmentStatus.ASSIGNED ? time : null);
        a.setCreatedAt(time);
        a.setUpdatedAt(time);
        assignmentRepository.persist(a);
        return a;
    }

    @Test
    @TestTransaction
    @DisplayName("manualAssignDriver successfully transitions UNASSIGNED order to ASSIGNED, records operator and publishes event")
    void manualAssignDriver_success() {
        Instant now = Instant.now();
        persistAssignment("ORD-MANUAL-1", AssignmentStatus.UNASSIGNED, null, "NO_ELIGIBLE_DRIVER", now.minus(30, ChronoUnit.MINUTES));

        ManualAssignDriverRequest request = new ManualAssignDriverRequest("DRV-MANUAL-100", "Assigned via hotline");
        Assignment assigned = assignmentService.manualAssignDriver(dispatcherToken, "ORD-MANUAL-1", request);

        assertThat(assigned).isNotNull();
        assertThat(assigned.getOrderId()).isEqualTo("ORD-MANUAL-1");
        assertThat(assigned.getStatus()).isEqualTo(AssignmentStatus.ASSIGNED);
        assertThat(assigned.getDriverId()).isEqualTo("DRV-MANUAL-100");
        assertThat(assigned.getAssignmentSource()).isEqualTo("MANUAL");
        assertThat(assigned.getAssignedBy()).isEqualTo("dispatcher");
        assertThat(assigned.getAssignedAt()).isNotNull();
        assertThat(assigned.getUnassignedReason()).isNull();
        assertThat(assigned.getUnassignedAt()).isNull();
        assertThat(assigned.getNotes()).contains("[Manual Assign] Assigned via hotline");

        // Verify database persistence
        Assignment fromDb = assignmentRepository.findByOrderId("ORD-MANUAL-1").orElseThrow();
        assertThat(fromDb.getStatus()).isEqualTo(AssignmentStatus.ASSIGNED);
        assertThat(fromDb.getDriverId()).isEqualTo("DRV-MANUAL-100");
        assertThat(fromDb.getAssignmentSource()).isEqualTo("MANUAL");

        // Verify event publishing to Kafka
        verify(eventPublisher, times(1)).publishDriverAssigned(any(Assignment.class));
    }

    @Test
    @TestTransaction
    @DisplayName("manualAssignDriver throws DuplicateAssignmentException (409) when order already has an assigned driver")
    void manualAssignDriver_conflict_whenAlreadyAssigned() {
        Instant now = Instant.now();
        persistAssignment("ORD-ALREADY-ASSIGNED", AssignmentStatus.ASSIGNED, "DRV-EXISTING", null, now.minus(15, ChronoUnit.MINUTES));

        ManualAssignDriverRequest request = new ManualAssignDriverRequest("DRV-NEW-999");
        assertThatThrownBy(() -> assignmentService.manualAssignDriver(dispatcherToken, "ORD-ALREADY-ASSIGNED", request))
                .isInstanceOf(DuplicateAssignmentException.class)
                .hasMessageContaining("ORD-ALREADY-ASSIGNED");

        // Event should NOT be published
        verify(eventPublisher, Mockito.never()).publishDriverAssigned(any(Assignment.class));
    }

    @Test
    @TestTransaction
    @DisplayName("manualAssignDriver throws IllegalStateException (400) when order is in final status")
    void manualAssignDriver_finalStatus_throwsException() {
        Instant now = Instant.now();
        persistAssignment("ORD-CANCELLED", AssignmentStatus.CANCELLED, null, null, now.minus(10, ChronoUnit.MINUTES));

        ManualAssignDriverRequest request = new ManualAssignDriverRequest("DRV-100");
        assertThatThrownBy(() -> assignmentService.manualAssignDriver(dispatcherToken, "ORD-CANCELLED", request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CANCELLED");
    }

    @Test
    @TestTransaction
    @DisplayName("manualAssignDriver throws IllegalArgumentException (400) when driver is non-existent or invalid")
    void manualAssignDriver_invalidDriver_throwsException() {
        Instant now = Instant.now();
        persistAssignment("ORD-VALID", AssignmentStatus.UNASSIGNED, null, "ALL_DRIVERS_BUSY", now.minus(10, ChronoUnit.MINUTES));

        ManualAssignDriverRequest request = new ManualAssignDriverRequest("INVALID");
        assertThatThrownBy(() -> assignmentService.manualAssignDriver(dispatcherToken, "ORD-VALID", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not exist or is not available");
    }

    @Test
    @TestTransaction
    @DisplayName("manualAssignDriver throws AssignmentNotFoundException (404) when order does not exist")
    void manualAssignDriver_orderNotFound_throwsException() {
        ManualAssignDriverRequest request = new ManualAssignDriverRequest("DRV-100");
        assertThatThrownBy(() -> assignmentService.manualAssignDriver(adminToken, "ORD-NOT-FOUND", request))
                .isInstanceOf(AssignmentNotFoundException.class)
                .hasMessageContaining("ORD-NOT-FOUND");
    }

    @Test
    @DisplayName("manualAssignDriver throws AccessDeniedException (403) when caller has CUSTOMER role")
    void manualAssignDriver_customerRole_throwsAccessDenied() {
        ManualAssignDriverRequest request = new ManualAssignDriverRequest("DRV-100");
        assertThatThrownBy(() -> assignmentService.manualAssignDriver(customerToken, "ORD-1", request))
                .isInstanceOf(AccessDeniedException.class);
    }
}
