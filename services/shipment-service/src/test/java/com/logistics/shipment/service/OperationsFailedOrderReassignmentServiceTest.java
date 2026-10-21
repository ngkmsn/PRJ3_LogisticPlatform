package com.logistics.shipment.service;

import com.logistics.common.security.JwtTokenProvider;
import com.logistics.common.security.Role;
import com.logistics.shipment.dto.ConfirmPickupRequest;
import com.logistics.shipment.dto.ConfirmDeliveryRequest;
import com.logistics.shipment.dto.FailedOrderDto;
import com.logistics.shipment.dto.PagedResponse;
import com.logistics.shipment.dto.ReassignDriverRequest;
import com.logistics.shipment.dto.ReportDeliveryFailureRequest;
import com.logistics.shipment.dto.StartTransitRequest;
import com.logistics.shipment.entity.Assignment;
import com.logistics.shipment.entity.AssignmentHistory;
import com.logistics.shipment.exception.AccessDeniedException;
import com.logistics.shipment.exception.AssignmentNotFoundException;
import com.logistics.shipment.exception.BadCredentialsException;
import com.logistics.shipment.model.AssignmentActionType;
import com.logistics.shipment.model.AssignmentStatus;
import com.logistics.shipment.publisher.DriverAssignedEventPublisher;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@QuarkusTest
class OperationsFailedOrderReassignmentServiceTest {

    @Inject
    AssignmentRepository assignmentRepository;

    @Inject
    AssignmentHistoryRepository assignmentHistoryRepository;

    @Inject
    AssignmentService assignmentService;

    @Inject
    JwtTokenProvider jwtTokenProvider;

    @InjectSpy
    DriverAssignedEventPublisher eventPublisher;

    private String dispatcherToken;
    private String adminToken;
    private String customerToken;
    private String oldDriverToken;
    private String newDriverToken;

    private final String oldDriverId = "DRV-FAILED-OLD";
    private final String newDriverId = "DRV-NEW-2";

    @BeforeEach
    @Transactional
    void setUp() {
        assignmentHistoryRepository.deleteAll();
        assignmentRepository.deleteAll();

        dispatcherToken = "Bearer " + jwtTokenProvider.generateToken(
                "disp-user-43", "dispatcher_pb43", "dispatcher43@test.com", Role.DISPATCHER.name()
        );
        adminToken = "Bearer " + jwtTokenProvider.generateToken(
                "admin-user-43", "admin_pb43", "admin43@test.com", Role.ADMIN.name()
        );
        customerToken = "Bearer " + jwtTokenProvider.generateToken(
                "cust-user-43", "customer43", "customer43@test.com", Role.CUSTOMER.name()
        );
        oldDriverToken = "Bearer " + jwtTokenProvider.generateToken(
                oldDriverId, "driver_old", "driver_old@test.com", Role.DRIVER.name()
        );
        newDriverToken = "Bearer " + jwtTokenProvider.generateToken(
                newDriverId, "driver_new", "driver_new@test.com", Role.DRIVER.name()
        );
    }

    private Assignment seedFailedAssignment(String orderId, String driverId, String failureReason) {
        Instant now = Instant.now();
        Assignment a = new Assignment();
        a.setId(UUID.randomUUID().toString());
        a.setOrderId(orderId);
        a.setOrderNumber("ORD-" + orderId);
        a.setCustomerId("cust-43");
        a.setRecipientName("Recipient 43");
        a.setRecipientPhone("0908889999");
        a.setDeliveryAddress("456 Cau Giay, Hanoi");
        a.setTotalAmount(new BigDecimal("185000.00"));
        a.setStatus(AssignmentStatus.FAILED);
        a.setDriverId(driverId);
        a.setAssignedAt(now.minus(45, ChronoUnit.MINUTES));
        a.setPickedUpAt(now.minus(30, ChronoUnit.MINUTES));
        a.setTransitStartedAt(now.minus(20, ChronoUnit.MINUTES));
        a.setFailedAt(now.minus(10, ChronoUnit.MINUTES));
        a.setFailureReason(failureReason);
        a.setFailureNotes("Recipient was not present");
        a.setNotes("Urgent fragile item");
        a.setCreatedAt(now.minus(50, ChronoUnit.MINUTES));
        a.setUpdatedAt(now.minus(10, ChronoUnit.MINUTES));
        assignmentRepository.persist(a);
        return a;
    }

    @Test
    @TestTransaction
    @DisplayName("Operations successfully reassigns FAILED order to new eligible driver and transitions to ASSIGNED")
    void reassign_failedOrder_success() {
        seedFailedAssignment("ORD-FAIL-01", oldDriverId, "Recipient phone switched off");

        ReassignDriverRequest request = new ReassignDriverRequest(
                newDriverId,
                "Recipient contacted and rescheduled; assigning fresh driver",
                "Priority afternoon dispatch"
        );

        Assignment reassigned = assignmentService.reassignDriver(dispatcherToken, "ORD-FAIL-01", request);

        // Verification of assignment state
        assertThat(reassigned).isNotNull();
        assertThat(reassigned.getOrderId()).isEqualTo("ORD-FAIL-01");
        assertThat(reassigned.getStatus()).isEqualTo(AssignmentStatus.ASSIGNED);
        assertThat(reassigned.getPreviousDriverId()).isEqualTo(oldDriverId);
        assertThat(reassigned.getDriverId()).isEqualTo(newDriverId);
        assertThat(reassigned.getReassignReason()).isEqualTo("Recipient contacted and rescheduled; assigning fresh driver");
        assertThat(reassigned.getReassignedBy()).isEqualTo("dispatcher_pb43");
        assertThat(reassigned.getAssignedBy()).isEqualTo("dispatcher_pb43");
        assertThat(reassigned.getAssignmentSource()).isEqualTo("MANUAL");
        assertThat(reassigned.getReassignedAt()).isNotNull();
        assertThat(reassigned.getAssignedAt()).isNotNull();

        // Transit state must be reset for new driver
        assertThat(reassigned.getPickedUpAt()).isNull();
        assertThat(reassigned.getTransitStartedAt()).isNull();

        // Failure flags cleared on active assignment, but context preserved in notes
        assertThat(reassigned.getFailedAt()).isNull();
        assertThat(reassigned.getFailureReason()).isNull();
        assertThat(reassigned.getNotes()).contains("[Previously Failed: Recipient phone switched off");
        assertThat(reassigned.getNotes()).contains("[Reassigned] Priority afternoon dispatch");

        // Workload verification: Old driver decremented/unassigned; New driver assigned
        assertThat(assignmentRepository.countActiveAssignmentsByDriverId(oldDriverId)).isEqualTo(0);
        assertThat(assignmentRepository.countActiveAssignmentsByDriverId(newDriverId)).isEqualTo(1);

        // Audit history verification
        List<AssignmentHistory> histories = assignmentHistoryRepository.findByOrderId("ORD-FAIL-01");
        assertThat(histories).isNotEmpty();
        AssignmentHistory reassignHistory = histories.stream()
                .filter(h -> h.getActionType() == AssignmentActionType.REASSIGN)
                .findFirst()
                .orElse(null);
        assertThat(reassignHistory).isNotNull();
        assertThat(reassignHistory.getPreviousDriverId()).isEqualTo(oldDriverId);
        assertThat(reassignHistory.getNewDriverId()).isEqualTo(newDriverId);
        assertThat(reassignHistory.getPerformedBy()).isEqualTo("dispatcher_pb43");
        assertThat(reassignHistory.getReason()).contains("Recipient contacted and rescheduled");

        // Event broadcasting verification
        verify(eventPublisher, times(1)).publishDriverReassigned(
                any(Assignment.class), eq(oldDriverId), eq("Recipient contacted and rescheduled; assigning fresh driver")
        );
    }

    @Test
    @TestTransaction
    @DisplayName("Old driver is strictly revoked and cannot perform pickup, transit, delivery or report failure after reassign")
    void oldDriver_cannotPerformActions_afterReassign() {
        seedFailedAssignment("ORD-FAIL-REVOKED", oldDriverId, "Customer not home");

        ReassignDriverRequest request = new ReassignDriverRequest(
                newDriverId, "Reassigned by Operations"
        );
        assignmentService.reassignDriver(adminToken, "ORD-FAIL-REVOKED", request);

        // 1. Old driver attempts confirmPickup -> 403 Forbidden
        ConfirmPickupRequest pickupReq = new ConfirmPickupRequest();
        pickupReq.setNotes("Old driver trying to pickup");
        assertThatThrownBy(() -> assignmentService.confirmPickup(oldDriverToken, "ORD-FAIL-REVOKED", pickupReq))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You are not the assigned driver");

        // 2. Old driver attempts startTransit -> 403 Forbidden
        StartTransitRequest transitReq = new StartTransitRequest();
        transitReq.setNotes("Old driver trying to transit");
        assertThatThrownBy(() -> assignmentService.startTransit(oldDriverToken, "ORD-FAIL-REVOKED", transitReq))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You are not the assigned driver");

        // 3. Old driver attempts confirmDelivery -> 403 Forbidden
        ConfirmDeliveryRequest deliverReq = new ConfirmDeliveryRequest();
        deliverReq.setNotes("Old driver trying to deliver");
        assertThatThrownBy(() -> assignmentService.confirmDelivery(oldDriverToken, "ORD-FAIL-REVOKED", deliverReq))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You are not the assigned driver");

        // 4. Old driver attempts reportDeliveryFailure -> 403 Forbidden
        ReportDeliveryFailureRequest failReq = new ReportDeliveryFailureRequest();
        failReq.setReason("FAILED_OTHER");
        failReq.setNotes("Old driver reporting failure again");
        assertThatThrownBy(() -> assignmentService.reportDeliveryFailure(oldDriverToken, "ORD-FAIL-REVOKED", failReq))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You are not the assigned driver");
    }

    @Test
    @TestTransaction
    @DisplayName("New driver can successfully confirm pickup after order is reassigned from FAILED status")
    void newDriver_canPerformPickup_afterReassign() {
        seedFailedAssignment("ORD-FAIL-PICKUP", oldDriverId, "Initial delivery failed");

        ReassignDriverRequest request = new ReassignDriverRequest(newDriverId, "Reassign to fresh driver");
        assignmentService.reassignDriver(dispatcherToken, "ORD-FAIL-PICKUP", request);

        // New driver confirms pickup
        ConfirmPickupRequest pickupReq = new ConfirmPickupRequest();
        pickupReq.setNotes("Picked up from hub successfully");
        Assignment pickedUp = assignmentService.confirmPickup(newDriverToken, "ORD-FAIL-PICKUP", pickupReq);

        assertThat(pickedUp).isNotNull();
        assertThat(pickedUp.getStatus()).isEqualTo(AssignmentStatus.PICKED_UP);
        assertThat(pickedUp.getDriverId()).isEqualTo(newDriverId);
        assertThat(pickedUp.getPickedUpAt()).isNotNull();
    }

    @Test
    @TestTransaction
    @DisplayName("Reassign FAILED order throws IllegalArgumentException when new driver is invalid or offline")
    void reassign_failedOrder_invalidDriver_throwsIllegalArgument() {
        seedFailedAssignment("ORD-FAIL-INV", oldDriverId, "Damaged package");

        ReassignDriverRequest request = new ReassignDriverRequest("INVALID", "Try invalid driver");
        assertThatThrownBy(() -> assignmentService.reassignDriver(dispatcherToken, "ORD-FAIL-INV", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not exist or is not available");

        verify(eventPublisher, Mockito.never()).publishDriverReassigned(any(), any(), any());
    }

    @Test
    @TestTransaction
    @DisplayName("Reassign FAILED order throws IllegalArgumentException when new driver is same as current driver")
    void reassign_failedOrder_sameDriver_throwsIllegalArgument() {
        seedFailedAssignment("ORD-FAIL-SAME", oldDriverId, "Customer asked for delay");

        ReassignDriverRequest request = new ReassignDriverRequest(oldDriverId, "Same driver retry");
        assertThatThrownBy(() -> assignmentService.reassignDriver(dispatcherToken, "ORD-FAIL-SAME", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("New driver cannot be the same as the currently assigned driver");

        verify(eventPublisher, Mockito.never()).publishDriverReassigned(any(), any(), any());
    }

    @Test
    @DisplayName("Reassign throws AccessDeniedException when caller is CUSTOMER or DRIVER")
    void reassign_unauthorizedRole_throwsAccessDenied() {
        ReassignDriverRequest request = new ReassignDriverRequest(newDriverId, "Reason");

        assertThatThrownBy(() -> assignmentService.reassignDriver(customerToken, "ORD-1", request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only administrators and dispatchers");

        assertThatThrownBy(() -> assignmentService.reassignDriver(oldDriverToken, "ORD-1", request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only administrators and dispatchers");
    }

    @Test
    @DisplayName("Reassign throws BadCredentialsException when token is missing or invalid")
    void reassign_missingOrInvalidAuth_throwsBadCredentials() {
        ReassignDriverRequest request = new ReassignDriverRequest(newDriverId, "Reason");

        assertThatThrownBy(() -> assignmentService.reassignDriver(null, "ORD-1", request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Missing or invalid Authorization header");

        assertThatThrownBy(() -> assignmentService.reassignDriver("Bearer invalid.token.payload", "ORD-1", request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Token is invalid or expired");
    }

    @Test
    @TestTransaction
    @DisplayName("Reassign throws IllegalStateException when order is in DELIVERED terminal status")
    void reassign_terminalStatus_delivered_throwsIllegalState() {
        Instant now = Instant.now();
        Assignment a = new Assignment();
        a.setId(UUID.randomUUID().toString());
        a.setOrderId("ORD-DELIVERED");
        a.setOrderNumber("ORD-DELIVERED");
        a.setCustomerId("cust-43");
        a.setDeliveryAddress("Delivery Address");
        a.setTotalAmount(new BigDecimal("100000.00"));
        a.setStatus(AssignmentStatus.DELIVERED);
        a.setDriverId(oldDriverId);
        a.setDeliveredAt(now);
        a.setCreatedAt(now);
        assignmentRepository.persist(a);

        ReassignDriverRequest request = new ReassignDriverRequest(newDriverId, "Trying to reassign delivered order");
        assertThatThrownBy(() -> assignmentService.reassignDriver(dispatcherToken, "ORD-DELIVERED", request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("terminal status: DELIVERED");
    }

    @Test
    @TestTransaction
    @DisplayName("Reassign throws IllegalStateException when order is in CANCELLED terminal status")
    void reassign_terminalStatus_cancelled_throwsIllegalState() {
        Instant now = Instant.now();
        Assignment a = new Assignment();
        a.setId(UUID.randomUUID().toString());
        a.setOrderId("ORD-CANCELLED-43");
        a.setOrderNumber("ORD-CANCELLED-43");
        a.setCustomerId("cust-43");
        a.setDeliveryAddress("Delivery Address");
        a.setTotalAmount(new BigDecimal("100000.00"));
        a.setStatus(AssignmentStatus.CANCELLED);
        a.setDriverId(oldDriverId);
        a.setCreatedAt(now);
        assignmentRepository.persist(a);

        ReassignDriverRequest request = new ReassignDriverRequest(newDriverId, "Trying to reassign cancelled order");
        assertThatThrownBy(() -> assignmentService.reassignDriver(dispatcherToken, "ORD-CANCELLED-43", request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("terminal status: CANCELLED");
    }

    @Test
    @TestTransaction
    @DisplayName("getFailedOrders and getFailedOrderDetail return accurate failed orders with failure metadata")
    void getFailedOrders_and_detail_success() {
        seedFailedAssignment("ORD-FAIL-LIST-1", oldDriverId, "CUSTOMER_UNAVAILABLE");
        seedFailedAssignment("ORD-FAIL-LIST-2", oldDriverId, "WRONG_ADDRESS");

        // 1. List failed orders
        PagedResponse<FailedOrderDto> paged = assignmentService.getFailedOrders(dispatcherToken, 0, 10, "newest_first");
        assertThat(paged).isNotNull();
        assertThat(paged.getContent()).hasSize(2);
        assertThat(paged.getContent().stream().map(FailedOrderDto::getOrderId))
                .contains("ORD-FAIL-LIST-1", "ORD-FAIL-LIST-2");

        // 2. Query detail of specific failed order
        FailedOrderDto detail = assignmentService.getFailedOrderDetail(adminToken, "ORD-FAIL-LIST-1");
        assertThat(detail).isNotNull();
        assertThat(detail.getOrderId()).isEqualTo("ORD-FAIL-LIST-1");
        assertThat(detail.getStatus()).isEqualTo(AssignmentStatus.FAILED);
        assertThat(detail.getDriverId()).isEqualTo(oldDriverId);
        assertThat(detail.getFailureReason()).isEqualTo("CUSTOMER_UNAVAILABLE");
        assertThat(detail.getFailureNotes()).isEqualTo("Recipient was not present");
        assertThat(detail.getFailedAt()).isNotNull();

        // 3. Detail for non-existent order throws AssignmentNotFoundException
        assertThatThrownBy(() -> assignmentService.getFailedOrderDetail(dispatcherToken, "NON-EXISTENT"))
                .isInstanceOf(AssignmentNotFoundException.class)
                .hasMessageContaining("No failed order found");
    }

    @Test
    @DisplayName("Concurrent reassign requests for the same order are processed safely without corrupting assignment")
    void concurrent_reassignments_handled_safely() throws InterruptedException {
        // Prepare persistent order outside test transaction
        prepareFailedOrder("ORD-CONCURRENT-FAIL", oldDriverId, "Concurrent failure");

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        // Thread 1: reassign to DRV-NEW-2
        executor.submit(() -> {
            try {
                startLatch.await();
                assignmentService.reassignDriver(
                        dispatcherToken, "ORD-CONCURRENT-FAIL", new ReassignDriverRequest(newDriverId, "Reason A")
                );
                successCount.incrementAndGet();
            } catch (Exception e) {
                conflictCount.incrementAndGet();
                errors.add(e);
            } finally {
                doneLatch.countDown();
            }
        });

        // Thread 2: reassign to DRV-0881
        executor.submit(() -> {
            try {
                startLatch.await();
                assignmentService.reassignDriver(
                        adminToken, "ORD-CONCURRENT-FAIL", new ReassignDriverRequest("DRV-0881", "Reason B")
                );
                successCount.incrementAndGet();
            } catch (Exception e) {
                conflictCount.incrementAndGet();
                errors.add(e);
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        boolean completed = doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        // At least one succeeded
        assertThat(successCount.get()).isGreaterThanOrEqualTo(1);

        // Verify final database state is consistent
        Assignment finalState = assignmentRepository.findByOrderId("ORD-CONCURRENT-FAIL").orElseThrow();
        assertThat(finalState.getStatus()).isEqualTo(AssignmentStatus.ASSIGNED);
        assertThat(finalState.getDriverId()).isIn(newDriverId, "DRV-0881");
    }

    @Transactional
    void prepareFailedOrder(String orderId, String driverId, String reason) {
        seedFailedAssignment(orderId, driverId, reason);
    }
}
