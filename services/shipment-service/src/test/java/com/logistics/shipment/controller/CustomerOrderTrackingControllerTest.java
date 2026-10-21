package com.logistics.shipment.controller;

import com.logistics.common.security.JwtTokenProvider;
import com.logistics.common.security.Role;
import com.logistics.shipment.entity.Assignment;
import com.logistics.shipment.entity.DriverLocation;
import com.logistics.shipment.model.AssignmentStatus;
import com.logistics.shipment.repository.AssignmentRepository;
import com.logistics.shipment.repository.DriverLocationRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class CustomerOrderTrackingControllerTest {

    @Inject
    AssignmentRepository assignmentRepository;

    @Inject
    DriverLocationRepository driverLocationRepository;

    @Inject
    JwtTokenProvider jwtTokenProvider;

    private String customerAToken;
    private String customerBToken;
    private final String customerAId = "cust-ctrl-uuid-01";
    private final String customerBId = "cust-ctrl-uuid-02";
    private final String driverId = "drv-ctrl-uuid-01";

    @BeforeEach
    void setUp() {
        QuarkusTransaction.requiringNew().run(() -> {
            driverLocationRepository.deleteAll();
            assignmentRepository.deleteAll();
        });

        customerAToken = "Bearer " + jwtTokenProvider.generateToken(
                customerAId, "customerA", "custA@test.com", Role.CUSTOMER.name()
        );
        customerBToken = "Bearer " + jwtTokenProvider.generateToken(
                customerBId, "customerB", "custB@test.com", Role.CUSTOMER.name()
        );
    }

    private void createTrackableOrder(String orderId) {
        QuarkusTransaction.requiringNew().run(() -> {
            Assignment assignment = new Assignment(
                    UUID.randomUUID().toString(),
                    orderId,
                    "ORD-" + orderId,
                    customerAId,
                    "Test Recipient",
                    "0912345678",
                    "123 Hoan Kiem, Hanoi",
                    new BigDecimal("200000"),
                    AssignmentStatus.IN_TRANSIT,
                    driverId,
                    Instant.now().minus(30, ChronoUnit.MINUTES),
                    "Note",
                    Instant.now(),
                    Instant.now()
            );
            assignmentRepository.persist(assignment);

            DriverLocation location = new DriverLocation(
                    UUID.randomUUID().toString(),
                    driverId,
                    "driver-user-01",
                    21.028511,
                    105.854444,
                    5.0,
                    30.0,
                    180.0,
                    orderId,
                    Instant.now().minus(2, ChronoUnit.MINUTES),
                    Instant.now(),
                    Instant.now()
            );
            driverLocationRepository.persist(location);
        });
    }

    @Test
    @DisplayName("GET /tracking/orders/{orderId} returns 200 OK with driver GPS location for order owner")
    void getOrderTracking_success() {
        String orderId = "ord-ctrl-2001";
        createTrackableOrder(orderId);

        given()
                .header("Authorization", customerAToken)
                .contentType(ContentType.JSON)
                .when()
                .get("/tracking/orders/" + orderId)
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.orderId", equalTo(orderId))
                .body("data.customerId", equalTo(customerAId))
                .body("data.driverId", equalTo(driverId))
                .body("data.latitude", equalTo(21.028511F))
                .body("data.longitude", equalTo(105.854444F))
                .body("data.liveTrackingActive", equalTo(true))
                .body("data.stale", equalTo(false));
    }

    @Test
    @DisplayName("GET /shipments/tracking/orders/{orderId} alias returns 200 OK")
    void getOrderTracking_gatewayAlias_success() {
        String orderId = "ord-ctrl-2002";
        createTrackableOrder(orderId);

        given()
                .header("Authorization", customerAToken)
                .contentType(ContentType.JSON)
                .when()
                .get("/shipments/tracking/orders/" + orderId)
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.orderId", equalTo(orderId));
    }

    @Test
    @DisplayName("GET /api/tracking/orders/{orderId} returns 403 Forbidden when accessed by another customer")
    void getOrderTracking_wrongCustomer_returnsForbidden() {
        String orderId = "ord-ctrl-2003";
        createTrackableOrder(orderId);

        given()
                .header("Authorization", customerBToken)
                .contentType(ContentType.JSON)
                .when()
                .get("/api/tracking/orders/" + orderId)
                .then()
                .statusCode(403)
                .body("success", equalTo(false))
                .body("message", containsString("You do not own this order"));
    }

    @Test
    @DisplayName("GET /tracking/orders/{orderId} returns 401 Unauthorized when missing token")
    void getOrderTracking_missingAuth_returnsUnauthorized() {
        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/tracking/orders/ord-test")
                .then()
                .statusCode(401)
                .body("success", equalTo(false));
    }

    @Test
    @DisplayName("GET /tracking/orders/{orderId} returns 400 Bad Request when order is not in trackable state")
    void getOrderTracking_untrackableOrder_returnsBadRequest() {
        String orderId = "ord-ctrl-pending";
        QuarkusTransaction.requiringNew().run(() -> {
            Assignment assignment = new Assignment(
                    UUID.randomUUID().toString(),
                    orderId,
                    "ORD-" + orderId,
                    customerAId,
                    "Recipient",
                    "0912345678",
                    "Address",
                    new BigDecimal("100000"),
                    AssignmentStatus.PENDING,
                    null,
                    null,
                    "Pending",
                    Instant.now(),
                    Instant.now()
            );
            assignmentRepository.persist(assignment);
        });

        given()
                .header("Authorization", customerAToken)
                .contentType(ContentType.JSON)
                .when()
                .get("/tracking/orders/" + orderId)
                .then()
                .statusCode(400)
                .body("success", equalTo(false))
                .body("message", containsString("Order is not in a trackable state"));
    }
}
