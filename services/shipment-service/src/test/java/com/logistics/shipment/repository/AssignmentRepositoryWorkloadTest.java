package com.logistics.shipment.repository;

import com.logistics.shipment.entity.Assignment;
import com.logistics.shipment.model.AssignmentStatus;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class AssignmentRepositoryWorkloadTest {

    private static final String DRIVER = "drv-workload-1";
    private static final String OTHER_DRIVER = "drv-workload-2";

    @Inject
    AssignmentRepository assignmentRepository;

    @BeforeEach
    @Transactional
    void setUp() {
        assignmentRepository.deleteAll();
    }

    @Test
    @Transactional
    @DisplayName("Workload counts every order the driver still carries: assigned, picked up and in transit")
    void countsAssignedPickedUpAndInTransit() {
        create(DRIVER, AssignmentStatus.ASSIGNED);
        create(DRIVER, AssignmentStatus.PICKED_UP);
        create(DRIVER, AssignmentStatus.IN_TRANSIT);

        assertEquals(3, assignmentRepository.countActiveAssignmentsByDriverId(DRIVER));
    }

    @Test
    @Transactional
    @DisplayName("Finished orders and other drivers' orders do not count towards workload")
    void ignoresFinishedOrdersAndOtherDrivers() {
        create(DRIVER, AssignmentStatus.IN_TRANSIT);
        create(DRIVER, AssignmentStatus.DELIVERED);
        create(DRIVER, AssignmentStatus.FAILED);
        create(DRIVER, AssignmentStatus.CANCELLED);
        create(OTHER_DRIVER, AssignmentStatus.ASSIGNED);

        assertEquals(1, assignmentRepository.countActiveAssignmentsByDriverId(DRIVER));
    }

    @Test
    @DisplayName("A blank driver id has no workload")
    void blankDriverIdIsZero() {
        assertEquals(0, assignmentRepository.countActiveAssignmentsByDriverId(" "));
        assertEquals(0, assignmentRepository.countActiveAssignmentsByDriverId(null));
    }

    private void create(String driverId, AssignmentStatus status) {
        Instant now = Instant.now();
        String orderId = "ORD-WL-" + UUID.randomUUID();
        Assignment a = new Assignment();
        a.setId(UUID.randomUUID().toString());
        a.setOrderId(orderId);
        a.setOrderNumber("NUM-" + orderId);
        a.setCustomerId("cust-001");
        a.setRecipientName("Nguyen Van Recipient");
        a.setRecipientPhone("0987654321");
        a.setDeliveryAddress("789 Dien Bien Phu, Binh Thanh, TP.HCM");
        a.setTotalAmount(new BigDecimal("200000.00"));
        a.setStatus(status);
        a.setDriverId(driverId);
        a.setAssignedAt(now);
        a.setCreatedAt(now);
        a.setUpdatedAt(now);
        assignmentRepository.persist(a);
    }
}
