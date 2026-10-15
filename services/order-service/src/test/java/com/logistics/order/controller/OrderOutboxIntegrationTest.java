package com.logistics.order.controller;

import com.logistics.common.model.OrderStatus;
import com.logistics.common.security.JwtTokenProvider;
import com.logistics.order.dto.CreateOrderItemRequest;
import com.logistics.order.dto.CreateOrderRequest;
import com.logistics.order.entity.OutboxEvent;
import com.logistics.order.entity.OutboxStatus;
import com.logistics.order.repository.OrderRepository;
import com.logistics.order.repository.OutboxEventRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

@QuarkusTest
class OrderOutboxIntegrationTest {

    @Inject
    JwtTokenProvider jwtTokenProvider;

    @Inject
    OrderRepository orderRepository;

    @InjectSpy
    OutboxEventRepository outboxEventRepository;

    private String customerToken;
    private String customerId;

    @BeforeEach
    void setUp() {
        reset(outboxEventRepository);
        customerId = "cust-outbox-uuid-1";
        customerToken = "Bearer " + jwtTokenProvider.generateToken(
                customerId, "customerOutbox", "customerOutbox@logistics.com", "CUSTOMER"
        );
    }

    @Test
    @DisplayName("PB-022: Order creation successfully persists ORDER_CREATED outbox event in the same transaction")
    void createOrder_success_persistsOutboxEvent() {
        CreateOrderRequest request = new CreateOrderRequest(
                "Phạm Minh Hoàng",
                "0944556677",
                "Số 88 Cầu Giấy, Hà Nội",
                "Giao sau 17h",
                List.of(
                        new CreateOrderItemRequest("Màn hình máy tính", 1, new BigDecimal("4500000.00")),
                        new CreateOrderItemRequest("Bàn phím cơ", 1, new BigDecimal("1200000.00"))
                )
        );

        String orderId = given()
                .header("Authorization", customerToken)
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/orders")
                .then()
                .statusCode(201)
                .body("success", equalTo(true))
                .extract()
                .path("data.id");

        assertThat(orderId).isNotBlank();

        // 1. Verify Order in database
        var orderOpt = orderRepository.findByIdOrOrderNumber(orderId);
        assertThat(orderOpt).isPresent();
        var order = orderOpt.get();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getTotalAmount()).isEqualByComparingTo("5700000.00");

        // 2. Verify OutboxEvent in database
        List<OutboxEvent> events = outboxEventRepository.findByAggregateId(orderId);
        assertThat(events).hasSize(1);

        OutboxEvent event = events.get(0);
        assertThat(event.getId()).isNotBlank();
        assertThat(event.getAggregateType()).isEqualTo("ORDER");
        assertThat(event.getAggregateId()).isEqualTo(orderId);
        assertThat(event.getEventType()).isEqualTo("ORDER_CREATED");
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getRetryCount()).isZero();
        assertThat(event.getOccurredAt()).isNotNull();
        assertThat(event.getCreatedAt()).isNotNull();
        assertThat(event.getPublishedAt()).isNull();

        // Payload checks
        assertThat(event.getPayload()).contains(orderId);
        assertThat(event.getPayload()).contains(order.getOrderNumber());
        assertThat(event.getPayload()).contains(customerId);
        assertThat(event.getPayload()).contains("Phạm Minh Hoàng");
        assertThat(event.getPayload()).contains("Màn hình máy tính");
        assertThat(event.getPayload()).contains("4500000.00");
    }

    @Test
    @DisplayName("PB-022: If saving Outbox event fails, the database transaction rolls back and Order is NOT saved")
    void createOrder_outboxFails_entireTransactionRollsBack() {
        long initialOrderCount = orderRepository.count();
        long initialOutboxCount = outboxEventRepository.count();

        // Simulate database exception during outbox persistence
        doThrow(new RuntimeException("Simulated Outbox DB Failure"))
                .when(outboxEventRepository).persist(any(OutboxEvent.class));

        CreateOrderRequest request = new CreateOrderRequest(
                "Người nhận thất bại",
                "0999888777",
                "999 Đường Tránh, Đà Nẵng",
                null,
                List.of(new CreateOrderItemRequest("Kiện hàng lỗi outbox", 1, new BigDecimal("100000.00")))
        );

        given()
                .header("Authorization", customerToken)
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/orders")
                .then()
                .statusCode(500);

        // Verify transaction was rolled back: neither order nor outbox event was committed
        long finalOrderCount = orderRepository.count();
        long finalOutboxCount = outboxEventRepository.count();

        assertThat(finalOrderCount)
                .as("Order count must not increase when outbox persistence fails")
                .isEqualTo(initialOrderCount);

        assertThat(finalOutboxCount)
                .as("Outbox count must not increase when outbox persistence fails")
                .isEqualTo(initialOutboxCount);
    }
}
