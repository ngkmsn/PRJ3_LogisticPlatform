package com.logistics.shipment.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logistics.common.event.DomainEvent;
import com.logistics.common.event.KafkaTopics;
import com.logistics.common.model.OrderStatus;
import com.logistics.shipment.entity.Assignment;
import com.logistics.shipment.model.AssignmentStatus;
import com.logistics.shipment.repository.AssignmentRepository;
import com.logistics.shipment.service.AssignmentService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class OrderToAssignmentFlowIntegrationTest {

    @Inject
    OrderCreatedConsumer orderCreatedConsumer;

    @Inject
    AssignmentService assignmentService;

    @Inject
    AssignmentRepository assignmentRepository;

    @Inject
    ObjectMapper objectMapper;

    @BeforeEach
    @Transactional
    void cleanUp() {
        assignmentRepository.deleteAll();
    }

    @Test
    @DisplayName("End-to-End Flow: OrderCreated event produced from Order Service contract is consumed and initiates Assignment")
    void orderToAssignmentFlow_success() throws Exception {
        String orderId = "ord-e2e-" + UUID.randomUUID();
        String orderNumber = "ORD-20261016-9999";
        String customerId = "cust-e2e-888";
        String recipientName = "Phạm Thị D";
        String recipientPhone = "0933221100";
        String deliveryAddress = "100 Hai Bà Trưng, Quận 3, TP.HCM";
        BigDecimal totalAmount = new BigDecimal("450000.00");
        String notes = "Gọi trước khi giao 15 phút";

        // 1. Build payload matching Order Service PB-022 / PB-023 exact serialization
        Map<String, Object> item1 = new HashMap<>();
        item1.put("productName", "Thùng sữa tươi 1L");
        item1.put("quantity", 2);
        item1.put("unitPrice", 35000.00);
        item1.put("weightKg", 2.0);

        Map<String, Object> orderPayloadMap = new HashMap<>();
        orderPayloadMap.put("orderId", orderId);
        orderPayloadMap.put("orderNumber", orderNumber);
        orderPayloadMap.put("customerId", customerId);
        orderPayloadMap.put("recipientName", recipientName);
        orderPayloadMap.put("recipientPhone", recipientPhone);
        orderPayloadMap.put("deliveryAddress", deliveryAddress);
        orderPayloadMap.put("status", OrderStatus.PENDING.name());
        orderPayloadMap.put("totalAmount", totalAmount);
        orderPayloadMap.put("notes", notes);
        orderPayloadMap.put("items", List.of(item1));
        orderPayloadMap.put("createdAt", Instant.now().toString());

        DomainEvent<Map<String, Object>> domainEvent = new DomainEvent<>(
                "ORDER_CREATED",
                orderId,
                orderPayloadMap
        );
        domainEvent.setEventId(UUID.randomUUID().toString());
        domainEvent.setTimestamp(Instant.now());

        String serializedKafkaMessage = objectMapper.writeValueAsString(domainEvent);

        // 2. Consume event at Assignment Service
        Assignment createdAssignment = orderCreatedConsumer.processOrderEvent(serializedKafkaMessage);

        // 3. Verify Assignment was created according to service contract
        assertThat(createdAssignment).isNotNull();
        assertThat(createdAssignment.getId()).isNotBlank();
        assertThat(createdAssignment.getOrderId()).isEqualTo(orderId);
        assertThat(createdAssignment.getOrderNumber()).isEqualTo(orderNumber);
        assertThat(createdAssignment.getCustomerId()).isEqualTo(customerId);
        assertThat(createdAssignment.getRecipientName()).isEqualTo(recipientName);
        assertThat(createdAssignment.getRecipientPhone()).isEqualTo(recipientPhone);
        assertThat(createdAssignment.getDeliveryAddress()).isEqualTo(deliveryAddress);
        assertThat(createdAssignment.getTotalAmount()).isEqualByComparingTo(totalAmount);
        assertThat(createdAssignment.getStatus()).isEqualTo(AssignmentStatus.PENDING);
        assertThat(createdAssignment.getNotes()).isEqualTo(notes);
        assertThat(createdAssignment.getDriverId()).isNull(); // No driver assigned yet at initiation stage

        // 4. Verify persisted state in shipment_db
        Assignment fromDb = assignmentService.findByOrderId(orderId).orElse(null);
        assertThat(fromDb).isNotNull();
        assertThat(fromDb.getId()).isEqualTo(createdAssignment.getId());
        assertThat(fromDb.getStatus()).isEqualTo(AssignmentStatus.PENDING);

        // 5. Test idempotency: Resend identical event
        Assignment duplicateResult = orderCreatedConsumer.processOrderEvent(serializedKafkaMessage);
        assertThat(duplicateResult).isNotNull();
        assertThat(duplicateResult.getId()).isEqualTo(createdAssignment.getId());
        assertThat(assignmentRepository.count()).isEqualTo(1L);
    }
}
