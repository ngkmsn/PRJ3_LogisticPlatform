package com.logistics.shipment.service;

import com.logistics.common.security.JwtTokenProvider;
import com.logistics.common.security.Role;
import com.logistics.shipment.dto.AssignmentHistoryDto;
import com.logistics.shipment.dto.ManualAssignDriverRequest;
import com.logistics.shipment.dto.OrderCreatedPayload;
import com.logistics.shipment.dto.ReassignDriverRequest;
import com.logistics.shipment.eligibility.model.AssignmentCriteria;
import com.logistics.shipment.eligibility.model.EligibleDriver;
import com.logistics.shipment.eligibility.service.DriverSelectionService;
import com.logistics.shipment.entity.Assignment;
import com.logistics.shipment.entity.AssignmentHistory;
import com.logistics.shipment.exception.AccessDeniedException;
import com.logistics.shipment.exception.AssignmentNotFoundException;
import com.logistics.shipment.exception.BadCredentialsException;
import com.logistics.shipment.model.AssignmentActionType;
import com.logistics.shipment.model.AssignmentStatus;
import com.logistics.shipment.repository.AssignmentHistoryRepository;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@QuarkusTest
class AssignmentHistoryServiceTest {

    @Inject
    AssignmentRepository assignmentRepository;

    @Inject
    AssignmentHistoryRepository assignmentHistoryRepository;

    @Inject
    AssignmentService assignmentService;

    @Inject
    JwtTokenProvider jwtTokenProvider;

    @InjectSpy
    DriverSelectionService driverSelectionService;

    private String dispatcherToken;
    private String adminToken;
    private String customerToken;

    @BeforeEach
    @Transactional
    void setUp() {
        assignmentHistoryRepository.deleteAll();
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

    private Assignment persistAssignment(String orderId, AssignmentStatus status, String driverId) {
        Instant now = Instant.now();
        Assignment a = new Assignment();
        a.setId(UUID.randomUUID().toString());
        a.setOrderId(orderId);
        a.setOrderNumber("ORD-" + orderId);
        a.setCustomerId("cust-" + orderId);
        a.setRecipientName("Recipient " + orderId);
        a.setRecipientPhone("0901234567");
        a.setDeliveryAddress("Delivery Address " + orderId);
        a.setTotalAmount(new BigDecimal("200000.00"));
        a.setStatus(status);
        a.setDriverId(driverId);
        a.setAssignedAt(status == AssignmentStatus.ASSIGNED ? now : null);
        a.setCreatedAt(now);
        a.setUpdatedAt(now);
        assignmentRepository.persist(a);
        return a;
    }

    private OrderCreatedPayload createPayload(String orderId) {
        return new OrderCreatedPayload(
                orderId,
                "ORD-" + orderId,
                "cust-1",
                "Recipient " + orderId,
                "0901234567",
                "123 Main St",
                com.logistics.common.model.OrderStatus.PENDING,
                new BigDecimal("150000.00"),
                "Notes",
                Instant.now()
        );
    }

    private EligibleDriver createCandidate(String driverId, String name, double score) {
        EligibleDriver driver = new EligibleDriver(
                driverId, "user-" + driverId, name, "0911223344", "MOTORBIKE", "59B-56789",
                com.logistics.common.model.DriverAvailability.AVAILABLE, "ACTIVE", 10.7770, 106.7010, Instant.now()
        );
        driver.setScore(score);
        return driver;
    }

    @Test
    @TestTransaction
    @DisplayName("Auto-assignment persists an AUTO_ASSIGN history entry with performedBy = SYSTEM")
    void autoAssign_recordsHistory() {
        String orderId = "ORD-AUTO-HIST-1";
        OrderCreatedPayload payload = createPayload(orderId);
        EligibleDriver candidate = createCandidate("DRV-AUTO-1", "Driver Auto", 95.5);

        Mockito.doReturn(List.of(candidate))
                .when(driverSelectionService).rankEligibleDrivers(Mockito.any(AssignmentCriteria.class));

        Assignment assignment = assignmentService.autoAssignOrder(payload);
        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.ASSIGNED);
        assertThat(assignment.getDriverId()).isEqualTo("DRV-AUTO-1");

        List<AssignmentHistory> histories = assignmentHistoryRepository.findByOrderIdOrderByCreatedAtAsc(orderId);
        assertThat(histories).hasSize(1);

        AssignmentHistory h = histories.get(0);
        assertThat(h.getOrderId()).isEqualTo(orderId);
        assertThat(h.getAssignmentId()).isEqualTo(assignment.getId());
        assertThat(h.getPreviousDriverId()).isNull();
        assertThat(h.getNewDriverId()).isEqualTo("DRV-AUTO-1");
        assertThat(h.getActionType()).isEqualTo(AssignmentActionType.AUTO_ASSIGN);
        assertThat(h.getPerformedBy()).isEqualTo("SYSTEM");
        assertThat(h.getReason()).contains("95.5");
        assertThat(h.getCreatedAt()).isNotNull();
    }

    @Test
    @TestTransaction
    @DisplayName("Manual-assignment persists a MANUAL_ASSIGN history entry with performedBy = operator username")
    void manualAssign_recordsHistory() {
        String orderId = "ORD-MANUAL-HIST-1";
        Assignment assignment = persistAssignment(orderId, AssignmentStatus.UNASSIGNED, null);

        ManualAssignDriverRequest req = new ManualAssignDriverRequest("DRV-MANUAL-1", "Assigned by hotline operator");
        assignmentService.manualAssignDriver(dispatcherToken, orderId, req);

        List<AssignmentHistory> histories = assignmentHistoryRepository.findByOrderIdOrderByCreatedAtAsc(orderId);
        assertThat(histories).hasSize(1);

        AssignmentHistory h = histories.get(0);
        assertThat(h.getOrderId()).isEqualTo(orderId);
        assertThat(h.getAssignmentId()).isEqualTo(assignment.getId());
        assertThat(h.getPreviousDriverId()).isNull();
        assertThat(h.getNewDriverId()).isEqualTo("DRV-MANUAL-1");
        assertThat(h.getActionType()).isEqualTo(AssignmentActionType.MANUAL_ASSIGN);
        assertThat(h.getPerformedBy()).isEqualTo("dispatcher");
        assertThat(h.getReason()).isEqualTo("Assigned by hotline operator");
    }

    @Test
    @TestTransaction
    @DisplayName("Auto Assign followed by Reassign records chronological history entries (AUTO_ASSIGN -> REASSIGN)")
    void autoAssign_then_reassign_recordsChronologicalHistory() {
        String orderId = "ORD-AUTO-REASSIGN-HIST";
        OrderCreatedPayload payload = createPayload(orderId);
        EligibleDriver candidate = createCandidate("DRV-FIRST", "Driver First", 88.0);

        Mockito.doReturn(List.of(candidate))
                .when(driverSelectionService).rankEligibleDrivers(Mockito.any(AssignmentCriteria.class));

        assignmentService.autoAssignOrder(payload);

        // Now reassign to DRV-SECOND
        ReassignDriverRequest reassignReq = new ReassignDriverRequest("DRV-SECOND", "Vehicle flat tire", "Urgent transfer");
        assignmentService.reassignDriver(dispatcherToken, orderId, reassignReq);

        List<AssignmentHistoryDto> historyDtos = assignmentService.getAssignmentHistory(dispatcherToken, orderId);
        assertThat(historyDtos).hasSize(2);

        AssignmentHistoryDto first = historyDtos.get(0);
        assertThat(first.getActionType()).isEqualTo(AssignmentActionType.AUTO_ASSIGN);
        assertThat(first.getPreviousDriverId()).isNull();
        assertThat(first.getNewDriverId()).isEqualTo("DRV-FIRST");
        assertThat(first.getPerformedBy()).isEqualTo("SYSTEM");

        AssignmentHistoryDto second = historyDtos.get(1);
        assertThat(second.getActionType()).isEqualTo(AssignmentActionType.REASSIGN);
        assertThat(second.getPreviousDriverId()).isEqualTo("DRV-FIRST");
        assertThat(second.getNewDriverId()).isEqualTo("DRV-SECOND");
        assertThat(second.getPerformedBy()).isEqualTo("dispatcher");
        assertThat(second.getReason()).isEqualTo("Vehicle flat tire");

        // Verify chronological ordering
        assertThat(first.getCreatedAt()).isBeforeOrEqualTo(second.getCreatedAt());
    }

    @Test
    @TestTransaction
    @DisplayName("Multiple reassignments (Manual -> Reassign -> Reassign) records complete chronological audit trail")
    void multiple_reassigns_recordsAllTransitions() {
        String orderId = "ORD-MULTI-REASSIGN";
        persistAssignment(orderId, AssignmentStatus.UNASSIGNED, null);

        // Step 1: Manual assign to DRV-A
        assignmentService.manualAssignDriver(dispatcherToken, orderId, new ManualAssignDriverRequest("DRV-A", "Initial manual dispatch"));

        // Step 2: Reassign from DRV-A to DRV-B
        assignmentService.reassignDriver(dispatcherToken, orderId, new ReassignDriverRequest("DRV-B", "Driver A breakdown"));

        // Step 3: Reassign from DRV-B to DRV-C
        assignmentService.reassignDriver(adminToken, orderId, new ReassignDriverRequest("DRV-C", "Driver B stuck in traffic"));

        List<AssignmentHistoryDto> history = assignmentService.getAssignmentHistory(adminToken, orderId);
        assertThat(history).hasSize(3);

        // Entry 1: MANUAL_ASSIGN
        assertThat(history.get(0).getActionType()).isEqualTo(AssignmentActionType.MANUAL_ASSIGN);
        assertThat(history.get(0).getPreviousDriverId()).isNull();
        assertThat(history.get(0).getNewDriverId()).isEqualTo("DRV-A");
        assertThat(history.get(0).getPerformedBy()).isEqualTo("dispatcher");

        // Entry 2: REASSIGN (DRV-A -> DRV-B)
        assertThat(history.get(1).getActionType()).isEqualTo(AssignmentActionType.REASSIGN);
        assertThat(history.get(1).getPreviousDriverId()).isEqualTo("DRV-A");
        assertThat(history.get(1).getNewDriverId()).isEqualTo("DRV-B");
        assertThat(history.get(1).getPerformedBy()).isEqualTo("dispatcher");

        // Entry 3: REASSIGN (DRV-B -> DRV-C)
        assertThat(history.get(2).getActionType()).isEqualTo(AssignmentActionType.REASSIGN);
        assertThat(history.get(2).getPreviousDriverId()).isEqualTo("DRV-B");
        assertThat(history.get(2).getNewDriverId()).isEqualTo("DRV-C");
        assertThat(history.get(2).getPerformedBy()).isEqualTo("admin");

        // Assert chronological timestamp order
        assertThat(history.get(0).getCreatedAt()).isBeforeOrEqualTo(history.get(1).getCreatedAt());
        assertThat(history.get(1).getCreatedAt()).isBeforeOrEqualTo(history.get(2).getCreatedAt());
    }

    @Test
    @DisplayName("getAssignmentHistory enforces authentication and RBAC")
    void getAssignmentHistory_accessControl() {
        // Missing token -> 401 BadCredentialsException
        assertThatThrownBy(() -> assignmentService.getAssignmentHistory(null, "ORD-1"))
                .isInstanceOf(BadCredentialsException.class);

        // Customer token -> 403 AccessDeniedException
        assertThatThrownBy(() -> assignmentService.getAssignmentHistory(customerToken, "ORD-1"))
                .isInstanceOf(AccessDeniedException.class);

        // Non-existent order -> 404 AssignmentNotFoundException
        assertThatThrownBy(() -> assignmentService.getAssignmentHistory(dispatcherToken, "NON-EXISTENT"))
                .isInstanceOf(AssignmentNotFoundException.class);
    }
}
