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
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;

@QuarkusTest
class OrderListingIntegrationTest {

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
        customerIdA = "11111111-aaaa-aaaa-aaaa-111111111111";
        customerTokenA = "Bearer " + jwtTokenProvider.generateToken(
                customerIdA, "customerA", "customerA@logistics.com", "CUSTOMER"
        );

        customerIdB = "22222222-bbbb-bbbb-bbbb-222222222222";
        customerTokenB = "Bearer " + jwtTokenProvider.generateToken(
                customerIdB, "customerB", "customerB@logistics.com", "CUSTOMER"
        );

        driverToken = "Bearer " + jwtTokenProvider.generateToken(
                "33333333-cccc-cccc-cccc-333333333333", "driver", "driver@logistics.com", "DRIVER"
        );

        adminToken = "Bearer " + jwtTokenProvider.generateToken(
                "44444444-dddd-dddd-dddd-444444444444", "admin", "admin@logistics.com", "ADMIN"
        );
    }

    private void createOrderForCustomer(String token, String recipientName, String address, BigDecimal price) {
        CreateOrderRequest request = new CreateOrderRequest(
                recipientName,
                "0988776655",
                address,
                "Giao hàng nhanh",
                List.of(new CreateOrderItemRequest("Sản phẩm test", 1, price))
        );

        given()
                .header("Authorization", token)
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/orders")
                .then()
                .statusCode(201);
    }

    @Test
    @DisplayName("Customer can list their own orders and data isolation prevents seeing others' orders")
    void listOrders_customerDataIsolationEnforced() {
        // Customer A creates 2 orders
        createOrderForCustomer(customerTokenA, "Người nhận A1", "Địa chỉ A1", new BigDecimal("100000.00"));
        createOrderForCustomer(customerTokenA, "Người nhận A2", "Địa chỉ A2", new BigDecimal("200000.00"));

        // Customer B creates 1 order
        createOrderForCustomer(customerTokenB, "Người nhận B1", "Địa chỉ B1", new BigDecimal("300000.00"));

        // When Customer A fetches orders: must ONLY see orders belonging to customerIdA
        given()
                .header("Authorization", customerTokenA)
                .when()
                .get("/orders")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.content", hasSize(greaterThanOrEqualTo(2)))
                .body("data.content.customerId", everyItem(equalTo(customerIdA)))
                .body("data.totalElements", greaterThanOrEqualTo(2));

        // When Customer B fetches orders: must ONLY see orders belonging to customerIdB
        given()
                .header("Authorization", customerTokenB)
                .when()
                .get("/orders")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.content", hasSize(1))
                .body("data.content.customerId", everyItem(equalTo(customerIdB)))
                .body("data.content[0].recipientName", equalTo("Người nhận B1"))
                .body("data.totalElements", equalTo(1));
    }

    @Test
    @DisplayName("Pagination parameters (page, size) work correctly for Customer order list")
    void listOrders_pagination() {
        String uniqueCustomer = "55555555-eeee-eeee-eeee-555555555555";
        String uniqueToken = "Bearer " + jwtTokenProvider.generateToken(
                uniqueCustomer, "custUnique", "custUnique@logistics.com", "CUSTOMER"
        );

        createOrderForCustomer(uniqueToken, "Đơn 1", "Địa chỉ 1", new BigDecimal("10000.00"));
        createOrderForCustomer(uniqueToken, "Đơn 2", "Địa chỉ 2", new BigDecimal("20000.00"));
        createOrderForCustomer(uniqueToken, "Đơn 3", "Địa chỉ 3", new BigDecimal("30000.00"));

        // Page 0, size 2 -> should return 2 orders, totalElements=3, totalPages=2
        given()
                .header("Authorization", uniqueToken)
                .queryParam("page", 0)
                .queryParam("size", 2)
                .when()
                .get("/orders")
                .then()
                .statusCode(200)
                .body("data.content", hasSize(2))
                .body("data.page", equalTo(0))
                .body("data.size", equalTo(2))
                .body("data.totalElements", equalTo(3))
                .body("data.totalPages", equalTo(2));

        // Page 1, size 2 -> should return remaining 1 order
        given()
                .header("Authorization", uniqueToken)
                .queryParam("page", 1)
                .queryParam("size", 2)
                .when()
                .get("/orders")
                .then()
                .statusCode(200)
                .body("data.content", hasSize(1))
                .body("data.page", equalTo(1));
    }

    @Test
    @DisplayName("Filtering by status returns only matching orders")
    void listOrders_filterByStatus() {
        String filterCustomer = "66666666-ffff-ffff-ffff-666666666666";
        String filterToken = "Bearer " + jwtTokenProvider.generateToken(
                filterCustomer, "custFilter", "custFilter@logistics.com", "CUSTOMER"
        );

        createOrderForCustomer(filterToken, "Đơn PENDING", "Địa chỉ", new BigDecimal("50000.00"));

        // Filter by PENDING: matches
        given()
                .header("Authorization", filterToken)
                .queryParam("status", "PENDING")
                .when()
                .get("/orders")
                .then()
                .statusCode(200)
                .body("data.content", hasSize(1))
                .body("data.content[0].status", equalTo("PENDING"));

        // Filter by DELIVERED: no matches
        given()
                .header("Authorization", filterToken)
                .queryParam("status", "DELIVERED")
                .when()
                .get("/orders")
                .then()
                .statusCode(200)
                .body("data.content", hasSize(0))
                .body("data.totalElements", equalTo(0));
    }

    @Test
    @DisplayName("Filtering by invalid status returns 400 Bad Request")
    void listOrders_invalidStatusFilter_badRequest() {
        given()
                .header("Authorization", customerTokenA)
                .queryParam("status", "NOT_A_VALID_STATUS")
                .when()
                .get("/orders")
                .then()
                .statusCode(400)
                .body("success", equalTo(false))
                .body("message", containsString("Invalid order status"));
    }

    @Test
    @DisplayName("Non-customer roles (DRIVER, ADMIN) are rejected with 403 Forbidden")
    void listOrders_nonCustomer_forbidden() {
        // Driver
        given()
                .header("Authorization", driverToken)
                .when()
                .get("/orders")
                .then()
                .statusCode(403)
                .body("success", equalTo(false))
                .body("message", containsString("Only customers are permitted to view orders"));

        // Admin
        given()
                .header("Authorization", adminToken)
                .when()
                .get("/orders")
                .then()
                .statusCode(403)
                .body("success", equalTo(false))
                .body("message", containsString("Only customers are permitted to view orders"));
    }

    @Test
    @DisplayName("Unauthenticated request is rejected with 401 Unauthorized")
    void listOrders_unauthenticated_rejected() {
        given()
                .when()
                .get("/orders")
                .then()
                .statusCode(401)
                .body("success", equalTo(false));
    }

    @Test
    @DisplayName("GET /api/orders routes correctly through API prefix")
    void listOrders_apiPrefix_success() {
        given()
                .header("Authorization", customerTokenA)
                .when()
                .get("/api/orders")
                .then()
                .statusCode(200)
                .body("success", equalTo(true));
    }
}
