package com.logistics.order.controller;

import com.logistics.common.dto.OrderTrackingDto;
import com.logistics.common.model.OrderStatus;
import com.logistics.common.security.JwtTokenProvider;
import com.logistics.common.security.Role;
import com.logistics.order.client.TrackingServiceClient;
import com.logistics.order.entity.Order;
import com.logistics.order.exception.TrackingServiceUnavailableException;
import com.logistics.order.repository.OrderRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@QuarkusTest
class CustomerOrderTrackingControllerTest {

    @Inject
    OrderRepository orderRepository;

    @Inject
    JwtTokenProvider jwtTokenProvider;

    @InjectMock
    TrackingServiceClient trackingServiceClient;

    private String customerAToken;
    private String customerBToken;
    private final String customerAId = "cust-ctrl-ord-01";
    private final String customerBId = "cust-ctrl-ord-02";

    @BeforeEach
    void setUp() {
        QuarkusTransaction.requiringNew().run(() -> {
            orderRepository.deleteAll();
        });

        customerAToken = "Bearer " + jwtTokenProvider.generateToken(
                customerAId, "customerA", "custA@test.com", Role.CUSTOMER.name()
        );
        customerBToken = "Bearer " + jwtTokenProvider.generateToken(
                customerBId, "customerB", "custB@test.com", Role.CUSTOMER.name()
        );
    }

    private void createOrder(String orderId, String customerId, OrderStatus status) {
        QuarkusTransaction.requiringNew().run(() -> {
            Order order = new Order(
                    orderId,
                    "ORD-" + orderId,
                    customerId,
                    status,
                    "Customer Recipient",
                    "0901234567",
                    "456 Le Loi, District 1, HCMC",
                    new BigDecimal("250000"),
                    "Handle with care"
            );
            orderRepository.persist(order);
        });
    }

    @Test
    @DisplayName("GET /orders/{id}/tracking returns 200 OK when customer tracks their active order")
    void getOrderTracking_success() {
        String orderId = "ord-ctrl-trk-01";
        createOrder(orderId, customerAId, OrderStatus.IN_TRANSIT);

        OrderTrackingDto mockDto = new OrderTrackingDto(
                orderId, "ORD-" + orderId, customerAId, "IN_TRANSIT", "IN_TRANSIT", "drv-555",
                10.776889, 106.700806, Instant.now(), true, false
        );

        when(trackingServiceClient.getOrderTracking(anyString(), anyString(), anyBoolean()))
                .thenReturn(mockDto);

        given()
                .header("Authorization", customerAToken)
                .contentType(ContentType.JSON)
                .when()
                .get("/orders/" + orderId + "/tracking")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.orderId", equalTo(orderId))
                .body("data.driverId", equalTo("drv-555"))
                .body("data.latitude", equalTo(10.776889F))
                .body("data.longitude", equalTo(106.700806F))
                .body("data.liveTrackingActive", equalTo(true));
    }

    @Test
    @DisplayName("GET /api/orders/{id}/tracking gateway alias returns 200 OK")
    void getOrderTracking_gatewayAlias_success() {
        String orderId = "ord-ctrl-trk-02";
        createOrder(orderId, customerAId, OrderStatus.ASSIGNED);

        OrderTrackingDto mockDto = new OrderTrackingDto(
                orderId, "ORD-" + orderId, customerAId, "ASSIGNED", "ASSIGNED", "drv-555",
                21.02, 105.85, Instant.now(), true, false
        );

        when(trackingServiceClient.getOrderTracking(anyString(), anyString(), anyBoolean()))
                .thenReturn(mockDto);

        given()
                .header("Authorization", customerAToken)
                .contentType(ContentType.JSON)
                .when()
                .get("/api/orders/" + orderId + "/tracking")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.orderId", equalTo(orderId));
    }

    @Test
    @DisplayName("GET /orders/{id}/tracking returns 403 Forbidden when accessed by another customer")
    void getOrderTracking_notOwner_returnsForbidden() {
        String orderId = "ord-ctrl-trk-03";
        createOrder(orderId, customerAId, OrderStatus.IN_TRANSIT);

        given()
                .header("Authorization", customerBToken)
                .contentType(ContentType.JSON)
                .when()
                .get("/orders/" + orderId + "/tracking")
                .then()
                .statusCode(403)
                .body("success", equalTo(false))
                .body("message", containsString("You do not have permission to view tracking"));
    }

    @Test
    @DisplayName("GET /orders/{id}/tracking returns 400 Bad Request when order is in PENDING state")
    void getOrderTracking_pendingState_returnsBadRequest() {
        String orderId = "ord-ctrl-trk-pending";
        createOrder(orderId, customerAId, OrderStatus.PENDING);

        given()
                .header("Authorization", customerAToken)
                .contentType(ContentType.JSON)
                .when()
                .get("/orders/" + orderId + "/tracking")
                .then()
                .statusCode(400)
                .body("success", equalTo(false))
                .body("message", containsString("Driver has not been assigned yet"));
    }

    @Test
    @DisplayName("GET /orders/{id}/tracking returns 503 Service Unavailable when Tracking Service is down")
    void getOrderTracking_trackingServiceDown_returnsServiceUnavailable() {
        String orderId = "ord-ctrl-trk-down";
        createOrder(orderId, customerAId, OrderStatus.IN_TRANSIT);

        when(trackingServiceClient.getOrderTracking(anyString(), anyString(), anyBoolean()))
                .thenThrow(new TrackingServiceUnavailableException("Tracking Service is currently unavailable"));

        given()
                .header("Authorization", customerAToken)
                .contentType(ContentType.JSON)
                .when()
                .get("/orders/" + orderId + "/tracking")
                .then()
                .statusCode(503)
                .body("success", equalTo(false))
                .body("message", containsString("Tracking Service is currently unavailable"));
    }

    @Test
    @DisplayName("GET /orders/{id}/tracking returns 401 Unauthorized when Authorization header is missing")
    void getOrderTracking_missingAuth_returnsUnauthorized() {
        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/orders/ord-999/tracking")
                .then()
                .statusCode(401)
                .body("success", equalTo(false));
    }
}
