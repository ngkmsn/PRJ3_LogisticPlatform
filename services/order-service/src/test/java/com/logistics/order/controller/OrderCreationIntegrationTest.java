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
import java.util.Collections;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;

@QuarkusTest
class OrderCreationIntegrationTest {

    @Inject
    JwtTokenProvider jwtTokenProvider;

    private String customerToken;
    private String driverToken;
    private String customerId;

    @BeforeEach
    void setUp() {
        customerId = "00000000-0000-0000-0000-000000000004";
        customerToken = "Bearer " + jwtTokenProvider.generateToken(
                customerId, "customer", "customer@logistics.com", "CUSTOMER"
        );
        driverToken = "Bearer " + jwtTokenProvider.generateToken(
                "00000000-0000-0000-0000-000000000003", "driver", "driver@logistics.com", "DRIVER"
        );
    }

    @Test
    @DisplayName("POST /orders by Customer creates order with initial status PENDING in database")
    void createOrder_customerSuccess_statusIsPending() {
        CreateOrderRequest request = new CreateOrderRequest(
                "Trần Thị Mai",
                "0912345678",
                "Số 456 Lê Duẩn, Hoàn Kiếm, Hà Nội",
                "Giao vào buổi sáng",
                List.of(
                        new CreateOrderItemRequest("Sản phẩm 1", 2, new BigDecimal("100000.00")),
                        new CreateOrderItemRequest("Sản phẩm 2", 3, new BigDecimal("50000.00"))
                )
        );

        given()
                .header("Authorization", customerToken)
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/orders")
                .then()
                .statusCode(201)
                .body("success", equalTo(true))
                .body("data.id", notNullValue())
                .body("data.orderNumber", startsWith("ORD-"))
                .body("data.customerId", equalTo(customerId))
                .body("data.status", equalTo("PENDING"))
                .body("data.recipientName", equalTo("Trần Thị Mai"))
                .body("data.recipientPhone", equalTo("0912345678"))
                .body("data.deliveryAddress", equalTo("Số 456 Lê Duẩn, Hoàn Kiếm, Hà Nội"))
                .body("data.notes", equalTo("Giao vào buổi sáng"))
                .body("data.items.size()", equalTo(2))
                .body("data.totalAmount", equalTo(350000.0f));
    }

    @Test
    @DisplayName("POST /orders by non-customer (DRIVER) is rejected with 403 Forbidden")
    void createOrder_driverForbidden() {
        CreateOrderRequest request = new CreateOrderRequest(
                "Người Nhận", "0900000000", "Địa chỉ", null,
                List.of(new CreateOrderItemRequest("Hàng hóa", 1, new BigDecimal("50000.00")))
        );

        given()
                .header("Authorization", driverToken)
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/orders")
                .then()
                .statusCode(403)
                .body("success", equalTo(false))
                .body("message", containsString("Only customers are permitted to create orders"));
    }

    @Test
    @DisplayName("POST /orders unauthenticated is rejected with 401 Unauthorized")
    void createOrder_unauthenticated_rejected() {
        CreateOrderRequest request = new CreateOrderRequest(
                "Người Nhận", "0900000000", "Địa chỉ", null,
                List.of(new CreateOrderItemRequest("Hàng hóa", 1, new BigDecimal("50000.00")))
        );

        given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/orders")
                .then()
                .statusCode(401)
                .body("success", equalTo(false));
    }

    @Test
    @DisplayName("POST /orders with missing recipient name is rejected with 400 Bad Request")
    void createOrder_missingRecipientName_badRequest() {
        CreateOrderRequest request = new CreateOrderRequest(
                "", // Blank recipient name
                "0912345678",
                "Địa chỉ nhận hàng",
                null,
                List.of(new CreateOrderItemRequest("Hàng hóa", 1, new BigDecimal("50000.00")))
        );

        given()
                .header("Authorization", customerToken)
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/orders")
                .then()
                .statusCode(400)
                .body("success", equalTo(false));
    }

    @Test
    @DisplayName("POST /orders with empty items list is rejected with 400 Bad Request")
    void createOrder_emptyItems_badRequest() {
        CreateOrderRequest request = new CreateOrderRequest(
                "Người Nhận",
                "0912345678",
                "Địa chỉ nhận hàng",
                null,
                Collections.emptyList()
        );

        given()
                .header("Authorization", customerToken)
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/orders")
                .then()
                .statusCode(400)
                .body("success", equalTo(false));
    }

    @Test
    @DisplayName("POST /orders with item quantity zero or negative is rejected with 400 Bad Request")
    void createOrder_invalidItemQuantity_badRequest() {
        CreateOrderRequest request = new CreateOrderRequest(
                "Người Nhận",
                "0912345678",
                "Địa chỉ nhận hàng",
                null,
                List.of(new CreateOrderItemRequest("Hàng hóa", 0, new BigDecimal("50000.00")))
        );

        given()
                .header("Authorization", customerToken)
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/orders")
                .then()
                .statusCode(400)
                .body("success", equalTo(false));
    }

    @Test
    @DisplayName("POST /orders with item price zero is rejected with 400 Bad Request")
    void createOrder_invalidItemPrice_badRequest() {
        CreateOrderRequest request = new CreateOrderRequest(
                "Người Nhận",
                "0912345678",
                "Địa chỉ nhận hàng",
                null,
                List.of(new CreateOrderItemRequest("Hàng hóa", 1, BigDecimal.ZERO))
        );

        given()
                .header("Authorization", customerToken)
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/orders")
                .then()
                .statusCode(400)
                .body("success", equalTo(false));
    }

    @Test
    @DisplayName("POST /api/orders routes correctly through API prefix")
    void createOrder_apiPrefix_success() {
        CreateOrderRequest request = new CreateOrderRequest(
                "Lê Văn Nam",
                "0933221100",
                "789 Điện Biên Phủ, TP.HCM",
                null,
                List.of(new CreateOrderItemRequest("Gói hàng mẫu", 1, new BigDecimal("120000.00")))
        );

        given()
                .header("Authorization", customerToken)
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/api/orders")
                .then()
                .statusCode(201)
                .body("success", equalTo(true))
                .body("data.status", equalTo("PENDING"));
    }
}
