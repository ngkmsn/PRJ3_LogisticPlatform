package com.logistics.order.controller;

import com.logistics.common.model.OrderStatus;
import com.logistics.common.security.JwtTokenProvider;
import com.logistics.order.dto.CreateOrderItemRequest;
import com.logistics.order.dto.CreateOrderRequest;
import com.logistics.order.dto.UpdateOrderStatusRequest;
import com.logistics.order.entity.Order;
import com.logistics.order.repository.OrderRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class OrderLifecycleIntegrationTest {

    @Inject
    JwtTokenProvider jwtTokenProvider;

    @Inject
    OrderRepository orderRepository;

    private String customerToken;
    private String driverToken;
    private String dispatcherToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        customerToken = "Bearer " + jwtTokenProvider.generateToken(
                "cust-uuid-lifecycle", "customerLifecycle", "customer@logistics.com", "CUSTOMER"
        );
        driverToken = "Bearer " + jwtTokenProvider.generateToken(
                "driver-uuid-lifecycle", "driverLifecycle", "driver@logistics.com", "DRIVER"
        );
        dispatcherToken = "Bearer " + jwtTokenProvider.generateToken(
                "disp-uuid-lifecycle", "dispatcherLifecycle", "dispatcher@logistics.com", "DISPATCHER"
        );
        adminToken = "Bearer " + jwtTokenProvider.generateToken(
                "admin-uuid-lifecycle", "adminLifecycle", "admin@logistics.com", "ADMIN"
        );
    }

    private String createOrderAndGetId(String recipientName, BigDecimal price) {
        CreateOrderRequest request = new CreateOrderRequest(
                recipientName,
                "0912345678",
                "100 Trần Hưng Đạo, Hoàn Kiếm, Hà Nội",
                "Giao hàng tiêu chuẩn",
                List.of(new CreateOrderItemRequest("Kiện hàng mẫu", 1, price))
        );

        return given()
                .header("Authorization", customerToken)
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/orders")
                .then()
                .statusCode(201)
                .extract()
                .path("data.id");
    }

    @Transactional
    void setOrderStatus(String orderId, OrderStatus status) {
        Order order = orderRepository.findById(orderId);
        if (order != null) {
            order.setStatus(status);
            orderRepository.persist(order);
        }
    }

    @Test
    @DisplayName("Complete valid lifecycle flow: PENDING -> CONFIRMED -> ASSIGNED -> IN_TRANSIT -> DELIVERED")
    void completeValidLifecycleFlow() {
        String orderId = createOrderAndGetId("Nguyễn Văn Lifecycle", new BigDecimal("100000.00"));

        // Step 1: PENDING -> CONFIRMED (Admin)
        given()
                .header("Authorization", adminToken)
                .contentType(ContentType.JSON)
                .body(new UpdateOrderStatusRequest(OrderStatus.CONFIRMED, "Admin xác nhận đơn"))
                .when()
                .patch("/orders/" + orderId + "/status")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.status", equalTo("CONFIRMED"))
                .body("data.notes", containsString("[Status: PENDING -> CONFIRMED: Admin xác nhận đơn]"));

        // Step 2: CONFIRMED -> ASSIGNED (Dispatcher)
        given()
                .header("Authorization", dispatcherToken)
                .contentType(ContentType.JSON)
                .body(new UpdateOrderStatusRequest(OrderStatus.ASSIGNED, "Gán tài xế D. Rahman"))
                .when()
                .patch("/orders/" + orderId + "/status")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.status", equalTo("ASSIGNED"));

        // Step 3: ASSIGNED -> IN_TRANSIT (Dispatcher)
        given()
                .header("Authorization", dispatcherToken)
                .contentType(ContentType.JSON)
                .body(new UpdateOrderStatusRequest(OrderStatus.IN_TRANSIT, "Tài xế đã nhận hàng và đang giao"))
                .when()
                .patch("/orders/" + orderId + "/status")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.status", equalTo("IN_TRANSIT"));

        // Step 4: IN_TRANSIT -> DELIVERED (Admin)
        given()
                .header("Authorization", adminToken)
                .contentType(ContentType.JSON)
                .body(new UpdateOrderStatusRequest(OrderStatus.DELIVERED, "Đã giao thành công"))
                .when()
                .patch("/orders/" + orderId + "/status")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.status", equalTo("DELIVERED"));
    }

    @Test
    @DisplayName("Re-dispatch flow: ASSIGNED can transition back to CONFIRMED")
    void transition_assignedBackToConfirmed_reDispatch() {
        String orderId = createOrderAndGetId("Trần Văn ReDispatch", new BigDecimal("200000.00"));
        setOrderStatus(orderId, OrderStatus.ASSIGNED);

        given()
                .header("Authorization", dispatcherToken)
                .contentType(ContentType.JSON)
                .body(new UpdateOrderStatusRequest(OrderStatus.CONFIRMED, "Tài xế từ chối nhận, đưa về chờ gán lại"))
                .when()
                .patch("/orders/" + orderId + "/status")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.status", equalTo("CONFIRMED"));
    }

    @Test
    @DisplayName("Status update works via /api/orders prefix and PUT method")
    void transition_viaApiPrefixAndPut_success() {
        String orderId = createOrderAndGetId("Lê Văn API", new BigDecimal("150000.00"));

        given()
                .header("Authorization", dispatcherToken)
                .contentType(ContentType.JSON)
                .body(new UpdateOrderStatusRequest(OrderStatus.CONFIRMED))
                .when()
                .put("/api/orders/" + orderId + "/status")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.status", equalTo("CONFIRMED"));
    }

    @Test
    @DisplayName("Invalid transition from terminal state DELIVERED is rejected with 400 Bad Request")
    void transition_fromDelivered_rejected_400() {
        String orderId = createOrderAndGetId("Khách Hàng Terminal", new BigDecimal("100000.00"));
        setOrderStatus(orderId, OrderStatus.DELIVERED);

        given()
                .header("Authorization", adminToken)
                .contentType(ContentType.JSON)
                .body(new UpdateOrderStatusRequest(OrderStatus.CANCELLED))
                .when()
                .patch("/orders/" + orderId + "/status")
                .then()
                .statusCode(400)
                .body("success", equalTo(false))
                .body("message", containsString("terminal status 'DELIVERED' cannot transition to any new status"));
    }

    @Test
    @DisplayName("Invalid skip-step transition (PENDING -> DELIVERED) is rejected with 400 Bad Request")
    void transition_pendingToDelivered_rejected_400() {
        String orderId = createOrderAndGetId("Đơn Hàng Skip Step", new BigDecimal("100000.00"));

        given()
                .header("Authorization", adminToken)
                .contentType(ContentType.JSON)
                .body(new UpdateOrderStatusRequest(OrderStatus.DELIVERED))
                .when()
                .patch("/orders/" + orderId + "/status")
                .then()
                .statusCode(400)
                .body("success", equalTo(false))
                .body("message", containsString("Transition not permitted. Allowed next statuses from 'PENDING'"));
    }

    @Test
    @DisplayName("Transition to same status is rejected with 400 Bad Request")
    void transition_sameStatus_rejected_400() {
        String orderId = createOrderAndGetId("Đơn Hàng Trùng", new BigDecimal("100000.00"));

        given()
                .header("Authorization", dispatcherToken)
                .contentType(ContentType.JSON)
                .body(new UpdateOrderStatusRequest(OrderStatus.PENDING))
                .when()
                .patch("/orders/" + orderId + "/status")
                .then()
                .statusCode(400)
                .body("success", equalTo(false))
                .body("message", containsString("Order is already in status PENDING"));
    }

    @Test
    @DisplayName("Customer role is rejected with 403 Forbidden when attempting to update order status")
    void transition_customerRole_forbidden_403() {
        String orderId = createOrderAndGetId("Đơn Khách Hàng", new BigDecimal("100000.00"));

        given()
                .header("Authorization", customerToken)
                .contentType(ContentType.JSON)
                .body(new UpdateOrderStatusRequest(OrderStatus.CONFIRMED))
                .when()
                .patch("/orders/" + orderId + "/status")
                .then()
                .statusCode(403)
                .body("success", equalTo(false))
                .body("message", containsString("Only administrators and dispatchers are permitted to update order status"));
    }

    @Test
    @DisplayName("Driver role is rejected with 403 Forbidden when attempting to update order status")
    void transition_driverRole_forbidden_403() {
        String orderId = createOrderAndGetId("Đơn Tài Xế", new BigDecimal("100000.00"));

        given()
                .header("Authorization", driverToken)
                .contentType(ContentType.JSON)
                .body(new UpdateOrderStatusRequest(OrderStatus.CONFIRMED))
                .when()
                .patch("/orders/" + orderId + "/status")
                .then()
                .statusCode(403)
                .body("success", equalTo(false))
                .body("message", containsString("Only administrators and dispatchers are permitted to update order status"));
    }

    @Test
    @DisplayName("Unauthenticated request is rejected with 401 Unauthorized")
    void transition_unauthenticated_rejected_401() {
        given()
                .contentType(ContentType.JSON)
                .body(new UpdateOrderStatusRequest(OrderStatus.CONFIRMED))
                .when()
                .patch("/orders/any-id/status")
                .then()
                .statusCode(401)
                .body("success", equalTo(false))
                .body("message", containsString("Missing or invalid Authorization header"));
    }

    @Test
    @DisplayName("Non-existent order returns 404 Not Found")
    void transition_notFound_404() {
        given()
                .header("Authorization", adminToken)
                .contentType(ContentType.JSON)
                .body(new UpdateOrderStatusRequest(OrderStatus.CONFIRMED))
                .when()
                .patch("/orders/non-existent-order-id/status")
                .then()
                .statusCode(404)
                .body("success", equalTo(false))
                .body("message", containsString("Order not found with identifier"));
    }
}
