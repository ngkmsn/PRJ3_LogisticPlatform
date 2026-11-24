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
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

@QuarkusTest
class StaffOrderAccessIntegrationTest {

    @Inject
    JwtTokenProvider jwtTokenProvider;

    private String customerTokenA;
    private String customerTokenB;
    private String driverToken;
    private String adminToken;
    private String dispatcherToken;
    private String orderIdA;
    private String orderIdB;

    @BeforeEach
    void setUp() {
        customerTokenA = "Bearer " + jwtTokenProvider.generateToken(
                "51111111-aaaa-aaaa-aaaa-111111111111", "staffTestCustA", "staffA@logistics.com", "CUSTOMER");
        customerTokenB = "Bearer " + jwtTokenProvider.generateToken(
                "52222222-bbbb-bbbb-bbbb-222222222222", "staffTestCustB", "staffB@logistics.com", "CUSTOMER");
        driverToken = "Bearer " + jwtTokenProvider.generateToken(
                "53333333-cccc-cccc-cccc-333333333333", "driver", "driver@logistics.com", "DRIVER");
        adminToken = "Bearer " + jwtTokenProvider.generateToken(
                "54444444-dddd-dddd-dddd-444444444444", "admin", "admin@logistics.com", "ADMIN");
        dispatcherToken = "Bearer " + jwtTokenProvider.generateToken(
                "55555555-eeee-eeee-eeee-555555555555", "dispatcher", "dispatcher@logistics.com", "DISPATCHER");

        orderIdA = createOrder(customerTokenA);
        orderIdB = createOrder(customerTokenB);
    }

    private String createOrder(String token) {
        CreateOrderRequest request = new CreateOrderRequest(
                "Recipient", "0988776655", "1 Le Loi, Q1", "notes",
                List.of(new CreateOrderItemRequest("Item", 1, new BigDecimal("10000"))));
        return given()
                .header("Authorization", token)
                .contentType(ContentType.JSON)
                .body(request)
                .when().post("/api/orders")
                .then().statusCode(201)
                .extract().path("data.id");
    }

    @Test
    @DisplayName("Dispatcher lists orders of all customers")
    void staffList_dispatcherSeesAllCustomersOrders() {
        given().header("Authorization", dispatcherToken)
                .when().get("/api/orders?size=100")
                .then().statusCode(200)
                .body("data.content.id", hasItems(orderIdA, orderIdB));
    }

    @Test
    @DisplayName("Admin can filter all orders by status")
    void staffList_adminFiltersByStatus() {
        given().header("Authorization", adminToken)
                .when().get("/api/orders?status=PENDING&size=100")
                .then().statusCode(200)
                .body("data.content.id", hasItem(orderIdA))
                .body("data.content.status", everyItem(is("PENDING")));
    }

    @Test
    @DisplayName("Dispatcher views another customer's order details")
    void staffDetail_dispatcherViewsOtherCustomersOrder() {
        given().header("Authorization", dispatcherToken)
                .when().get("/api/orders/" + orderIdA)
                .then().statusCode(200)
                .body("data.id", is(orderIdA));
    }

    @Test
    @DisplayName("Customer still sees only own orders and cannot open another customer's order")
    void customerList_stillOwnOnly() {
        given().header("Authorization", customerTokenA)
                .when().get("/api/orders?size=100")
                .then().statusCode(200)
                .body("data.content.id", hasItem(orderIdA))
                .body("data.content.id", not(hasItem(orderIdB)));

        given().header("Authorization", customerTokenA)
                .when().get("/api/orders/" + orderIdB)
                .then().statusCode(403);
    }

    @Test
    @DisplayName("Driver is still forbidden from listing and viewing orders")
    void driver_stillForbidden() {
        given().header("Authorization", driverToken)
                .when().get("/api/orders")
                .then().statusCode(403);

        given().header("Authorization", driverToken)
                .when().get("/api/orders/" + orderIdA)
                .then().statusCode(403);
    }
}
