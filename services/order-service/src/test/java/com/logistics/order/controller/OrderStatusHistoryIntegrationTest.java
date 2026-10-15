package com.logistics.order.controller;

import com.logistics.common.model.OrderStatus;
import com.logistics.common.security.JwtTokenProvider;
import com.logistics.order.dto.CancelOrderRequest;
import com.logistics.order.dto.CreateOrderItemRequest;
import com.logistics.order.dto.CreateOrderRequest;
import com.logistics.order.dto.UpdateOrderStatusRequest;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class OrderStatusHistoryIntegrationTest {

    @Inject
    JwtTokenProvider jwtTokenProvider;

    private String customerToken;
    private String otherCustomerToken;
    private String adminToken;
    private String dispatcherToken;

    @BeforeEach
    void setUp() {
        customerToken = "Bearer " + jwtTokenProvider.generateToken(
                "cust-uuid-history-1", "customerHistory1", "cust1@logistics.com", "CUSTOMER"
        );
        otherCustomerToken = "Bearer " + jwtTokenProvider.generateToken(
                "cust-uuid-history-2", "customerHistory2", "cust2@logistics.com", "CUSTOMER"
        );
        adminToken = "Bearer " + jwtTokenProvider.generateToken(
                "admin-uuid-history", "adminHistory", "admin@logistics.com", "ADMIN"
        );
        dispatcherToken = "Bearer " + jwtTokenProvider.generateToken(
                "disp-uuid-history", "dispHistory", "disp@logistics.com", "DISPATCHER"
        );
    }

    private String createOrderAndGetId(String token, String recipientName) {
        CreateOrderRequest request = new CreateOrderRequest(
                recipientName,
                "0912345678",
                "100 Trần Hưng Đạo, Hoàn Kiếm, Hà Nội",
                "Giao giờ hành chính",
                List.of(new CreateOrderItemRequest("Kiện hàng mẫu", 1, new BigDecimal("120000.00")))
        );

        return given()
                .header("Authorization", token)
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/orders")
                .then()
                .statusCode(201)
                .extract()
                .path("data.id");
    }

    @Test
    @DisplayName("PB-021: Initial order creation automatically records PENDING history entry")
    void orderCreation_recordsPendingHistory() {
        String orderId = createOrderAndGetId(customerToken, "Người nhận PB021 Create");

        given()
                .header("Authorization", customerToken)
                .when()
                .get("/orders/" + orderId + "/history")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data", hasSize(1))
                .body("data[0].orderId", equalTo(orderId))
                .body("data[0].previousStatus", equalTo(null))
                .body("data[0].newStatus", equalTo(OrderStatus.PENDING.name()))
                .body("data[0].changedBy", equalTo("cust-uuid-history-1"))
                .body("data[0].changedByRole", equalTo("CUSTOMER"))
                .body("data[0].createdAt", notNullValue());
    }

    @Test
    @DisplayName("PB-021: Lifecycle transitions automatically append history entries")
    void lifecycleTransitions_appendHistoryEntries() {
        String orderId = createOrderAndGetId(customerToken, "Người nhận PB021 Lifecycle");

        // 1. Transition PENDING -> CONFIRMED by Admin
        UpdateOrderStatusRequest confirmReq = new UpdateOrderStatusRequest(OrderStatus.CONFIRMED, "Duyệt đơn hàng");
        given()
                .header("Authorization", adminToken)
                .contentType(ContentType.JSON)
                .body(confirmReq)
                .when()
                .patch("/orders/" + orderId + "/status")
                .then()
                .statusCode(200);

        // 2. Transition CONFIRMED -> ASSIGNED by Dispatcher
        UpdateOrderStatusRequest assignReq = new UpdateOrderStatusRequest(OrderStatus.ASSIGNED, "Phân phối cho tài xế");
        given()
                .header("Authorization", dispatcherToken)
                .contentType(ContentType.JSON)
                .body(assignReq)
                .when()
                .patch("/orders/" + orderId + "/status")
                .then()
                .statusCode(200);

        // Verify history via /api/orders/{id}/history endpoint as well
        given()
                .header("Authorization", customerToken)
                .when()
                .get("/api/orders/" + orderId + "/history")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data", hasSize(3))
                // Entry 0: initial creation
                .body("data[0].previousStatus", equalTo(null))
                .body("data[0].newStatus", equalTo("PENDING"))
                // Entry 1: confirmed by admin
                .body("data[1].previousStatus", equalTo("PENDING"))
                .body("data[1].newStatus", equalTo("CONFIRMED"))
                .body("data[1].changedByRole", equalTo("ADMIN"))
                .body("data[1].reason", equalTo("Duyệt đơn hàng"))
                // Entry 2: assigned by dispatcher
                .body("data[2].previousStatus", equalTo("CONFIRMED"))
                .body("data[2].newStatus", equalTo("ASSIGNED"))
                .body("data[2].changedByRole", equalTo("DISPATCHER"))
                .body("data[2].reason", equalTo("Phân phối cho tài xế"));
    }

    @Test
    @DisplayName("PB-021: Order cancellation automatically records CANCELLED history entry with reason")
    void orderCancellation_recordsCancelledHistory() {
        String orderId = createOrderAndGetId(customerToken, "Người nhận PB021 Cancel");

        CancelOrderRequest cancelReq = new CancelOrderRequest("Thay đổi nhu cầu mua sắm");
        given()
                .header("Authorization", customerToken)
                .contentType(ContentType.JSON)
                .body(cancelReq)
                .when()
                .post("/orders/" + orderId + "/cancel")
                .then()
                .statusCode(200);

        given()
                .header("Authorization", customerToken)
                .when()
                .get("/orders/" + orderId + "/history")
                .then()
                .statusCode(200)
                .body("data", hasSize(2))
                .body("data[0].newStatus", equalTo("PENDING"))
                .body("data[1].previousStatus", equalTo("PENDING"))
                .body("data[1].newStatus", equalTo("CANCELLED"))
                .body("data[1].changedBy", equalTo("cust-uuid-history-1"))
                .body("data[1].reason", equalTo("Thay đổi nhu cầu mua sắm"));
    }

    @Test
    @DisplayName("PB-021: Other customer cannot view order history (403 Forbidden)")
    void getOrderHistory_otherCustomer_forbidden() {
        String orderId = createOrderAndGetId(customerToken, "Người nhận PB021 Forbidden");

        given()
                .header("Authorization", otherCustomerToken)
                .when()
                .get("/orders/" + orderId + "/history")
                .then()
                .statusCode(403)
                .body("success", equalTo(false));
    }

    @Test
    @DisplayName("PB-021: Unauthenticated request to view history is rejected (401)")
    void getOrderHistory_unauthenticated_rejected() {
        String orderId = createOrderAndGetId(customerToken, "Người nhận PB021 Unauth");

        given()
                .when()
                .get("/orders/" + orderId + "/history")
                .then()
                .statusCode(401)
                .body("success", equalTo(false));
    }

    @Test
    @DisplayName("PB-021: Non-existent order returns 404 Not Found")
    void getOrderHistory_notFound() {
        given()
                .header("Authorization", adminToken)
                .when()
                .get("/orders/00000000-0000-0000-0000-000000000000/history")
                .then()
                .statusCode(404)
                .body("success", equalTo(false));
    }
}
