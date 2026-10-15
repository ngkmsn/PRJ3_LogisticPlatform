package com.logistics.order.controller;

import com.logistics.common.security.JwtTokenProvider;
import com.logistics.order.dto.CreateOrderItemRequest;
import com.logistics.order.dto.CreateOrderRequest;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class OrderDetailsIntegrationTest {

    @Inject
    JwtTokenProvider jwtTokenProvider;

    private String customerTokenA;
    private String customerIdA;

    private String customerTokenB;
    private String customerIdB;

    private String driverToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        customerIdA = "aaaa1111-1111-1111-1111-aaaaaaaaaaaa";
        customerTokenA = "Bearer " + jwtTokenProvider.generateToken(
                customerIdA, "customerA", "customerA@logistics.com", "CUSTOMER"
        );

        customerIdB = "bbbb2222-2222-2222-2222-bbbbbbbbbbbb";
        customerTokenB = "Bearer " + jwtTokenProvider.generateToken(
                customerIdB, "customerB", "customerB@logistics.com", "CUSTOMER"
        );

        driverToken = "Bearer " + jwtTokenProvider.generateToken(
                "cccc3333-3333-3333-3333-cccccccccccc", "driver", "driver@logistics.com", "DRIVER"
        );

        adminToken = "Bearer " + jwtTokenProvider.generateToken(
                "dddd4444-4444-4444-4444-dddddddddddd", "admin", "admin@logistics.com", "ADMIN"
        );
    }

    private String createOrderAndGetId(String token, String recipientName, BigDecimal price) {
        CreateOrderRequest request = new CreateOrderRequest(
                recipientName,
                "0912345678",
                "123 Phố Huế, Hai Bà Trưng, Hà Nội",
                "Giao giờ hành chính",
                List.of(
                        new CreateOrderItemRequest("Sản phẩm A", 2, price),
                        new CreateOrderItemRequest("Sản phẩm B", 1, new BigDecimal("50000.00"))
                )
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
    @DisplayName("Customer can view their own order details with status PENDING and complete items")
    void getOrderDetails_ownerSuccess() {
        String orderId = createOrderAndGetId(customerTokenA, "Lê Văn Hùng", new BigDecimal("100000.00"));

        given()
                .header("Authorization", customerTokenA)
                .when()
                .get("/orders/" + orderId)
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.id", equalTo(orderId))
                .body("data.customerId", equalTo(customerIdA))
                .body("data.status", equalTo("PENDING"))
                .body("data.recipientName", equalTo("Lê Văn Hùng"))
                .body("data.recipientPhone", equalTo("0912345678"))
                .body("data.deliveryAddress", equalTo("123 Phố Huế, Hai Bà Trưng, Hà Nội"))
                .body("data.notes", equalTo("Giao giờ hành chính"))
                .body("data.items", hasSize(2))
                .body("data.totalAmount", equalTo(250000.0f))
                .body("data.createdAt", notNullValue());
    }

    @Test
    @DisplayName("Customer can retrieve order details by orderNumber identifier")
    void getOrderDetails_byOrderNumber() {
        CreateOrderRequest request = new CreateOrderRequest(
                "Phạm Văn C",
                "0977889900",
                "456 Cầu Giấy, Hà Nội",
                null,
                List.of(new CreateOrderItemRequest("Món hàng C", 1, new BigDecimal("75000.00")))
        );

        String orderNumber = given()
                .header("Authorization", customerTokenA)
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/orders")
                .then()
                .statusCode(201)
                .extract()
                .path("data.orderNumber");

        given()
                .header("Authorization", customerTokenA)
                .when()
                .get("/orders/" + orderNumber)
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.orderNumber", equalTo(orderNumber))
                .body("data.customerId", equalTo(customerIdA))
                .body("data.status", equalTo("PENDING"));
    }

    @Test
    @DisplayName("Customer attempting to view another customer's order is rejected with 403 Forbidden")
    void getOrderDetails_notOwner_forbidden() {
        // Order belongs to Customer A
        String orderAId = createOrderAndGetId(customerTokenA, "Chủ đơn A", new BigDecimal("50000.00"));

        // Customer B tries to view Customer A's order
        given()
                .header("Authorization", customerTokenB)
                .when()
                .get("/orders/" + orderAId)
                .then()
                .statusCode(403)
                .body("success", equalTo(false))
                .body("message", containsString("You do not have permission to view this order"));
    }

    @Test
    @DisplayName("Non-existent order returns 404 Not Found")
    void getOrderDetails_notFound_404() {
        given()
                .header("Authorization", customerTokenA)
                .when()
                .get("/orders/00000000-0000-0000-0000-nonexistent000")
                .then()
                .statusCode(404)
                .body("success", equalTo(false))
                .body("message", containsString("Order not found with identifier"));
    }

    @Test
    @DisplayName("Non-customer roles (DRIVER, ADMIN) are rejected with 403 Forbidden")
    void getOrderDetails_nonCustomer_forbidden() {
        String orderId = createOrderAndGetId(customerTokenA, "Chủ đơn A", new BigDecimal("50000.00"));

        // Driver
        given()
                .header("Authorization", driverToken)
                .when()
                .get("/orders/" + orderId)
                .then()
                .statusCode(403)
                .body("success", equalTo(false))
                .body("message", containsString("Only customers are permitted to view order details"));

        // Admin
        given()
                .header("Authorization", adminToken)
                .when()
                .get("/orders/" + orderId)
                .then()
                .statusCode(403)
                .body("success", equalTo(false))
                .body("message", containsString("Only customers are permitted to view order details"));
    }

    @Test
    @DisplayName("Unauthenticated request is rejected with 401 Unauthorized")
    void getOrderDetails_unauthenticated_rejected() {
        String orderId = createOrderAndGetId(customerTokenA, "Chủ đơn A", new BigDecimal("50000.00"));

        given()
                .when()
                .get("/orders/" + orderId)
                .then()
                .statusCode(401)
                .body("success", equalTo(false));
    }

    @Test
    @DisplayName("GET /api/orders/{id} routes correctly through API prefix")
    void getOrderDetails_apiPrefix_success() {
        String orderId = createOrderAndGetId(customerTokenA, "Chủ đơn A", new BigDecimal("50000.00"));

        given()
                .header("Authorization", customerTokenA)
                .when()
                .get("/api/orders/" + orderId)
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.id", equalTo(orderId));
    }
}
