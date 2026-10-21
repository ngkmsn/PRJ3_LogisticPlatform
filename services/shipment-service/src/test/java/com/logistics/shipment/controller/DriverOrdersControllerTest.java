package com.logistics.shipment.controller;

import com.logistics.common.security.JwtTokenProvider;
import com.logistics.common.security.Role;
import com.logistics.shipment.entity.Assignment;
import com.logistics.shipment.model.AssignmentStatus;
import com.logistics.shipment.repository.AssignmentRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

@QuarkusTest
class DriverOrdersControllerTest {

    @Inject
    JwtTokenProvider jwtTokenProvider;

    @Inject
    AssignmentRepository assignmentRepository;

    private String driver1Token;
    private String driver2Token;
    private String driver3Token;
    private String customerToken;
    private String dispatcherToken;
    private String adminToken;

    private final String driver1Id = "drv-ctrl-1";
    private final String driver2Id = "drv-ctrl-2";
    private final String driver3Id = "drv-ctrl-3";

    @BeforeEach
    @Transactional
    void setUp() {
        assignmentRepository.deleteAll();

        driver1Token = "Bearer " + jwtTokenProvider.generateToken(
                driver1Id, "driver1", "driver1@logistics.com", Role.DRIVER.name()
        );
        driver2Token = "Bearer " + jwtTokenProvider.generateToken(
                driver2Id, "driver2", "driver2@logistics.com", Role.DRIVER.name()
        );
        driver3Token = "Bearer " + jwtTokenProvider.generateToken(
                driver3Id, "driver3", "driver3@logistics.com", Role.DRIVER.name()
        );
        customerToken = "Bearer " + jwtTokenProvider.generateToken(
                "cust-ctrl-1", "customer1", "cust@logistics.com", Role.CUSTOMER.name()
        );
        dispatcherToken = "Bearer " + jwtTokenProvider.generateToken(
                "disp-ctrl-1", "disp1", "disp@logistics.com", Role.DISPATCHER.name()
        );
        adminToken = "Bearer " + jwtTokenProvider.generateToken(
                "admin-ctrl-1", "admin1", "admin@logistics.com", Role.ADMIN.name()
        );

        // Seed assignments inside @Transactional setUp so they are committed before tests run
        createAssignment("ORD-C1", "NUM-C1", driver1Id, AssignmentStatus.ASSIGNED);
        createAssignment("ORD-C2", "NUM-C2", driver2Id, AssignmentStatus.ASSIGNED);
    }

    private void createAssignment(String orderId, String orderNumber, String driverId, AssignmentStatus status) {
        Instant now = Instant.now();
        Assignment a = new Assignment();
        a.setId(UUID.randomUUID().toString());
        a.setOrderId(orderId);
        a.setOrderNumber(orderNumber);
        a.setCustomerId("cust-001");
        a.setRecipientName("Nguyen Van Recipient");
        a.setRecipientPhone("0987654321");
        a.setDeliveryAddress("789 Dien Bien Phu, Binh Thanh, TP.HCM");
        a.setTotalAmount(new BigDecimal("200000.00"));
        a.setStatus(status);
        a.setDriverId(driverId);
        a.setAssignedAt(now);
        a.setNotes("Call before delivery");
        a.setCreatedAt(now);
        a.setUpdatedAt(now);
        assignmentRepository.persist(a);
    }

    @Test
    @DisplayName("GET /assignments/driver/orders returns 200 OK with Driver's assigned orders")
    void getDriverOrders_success() {
        given()
                .header("Authorization", driver1Token)
                .contentType(ContentType.JSON)
                .when()
                .get("/assignments/driver/orders")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.totalElements", equalTo(1))
                .body("data.content", hasSize(1))
                .body("data.content[0].orderId", equalTo("ORD-C1"))
                .body("data.content[0].orderNumber", equalTo("NUM-C1"))
                .body("data.content[0].recipientName", equalTo("Nguyen Van Recipient"))
                .body("data.content[0].deliveryAddress", equalTo("789 Dien Bien Phu, Binh Thanh, TP.HCM"))
                .body("data.content[0].status", equalTo("ASSIGNED"));
    }

    @Test
    @DisplayName("GET /assignments/my-orders returns 200 OK with Driver's assigned orders")
    void getMyOrders_aliasRoute_success() {
        given()
                .header("Authorization", driver1Token)
                .contentType(ContentType.JSON)
                .when()
                .get("/assignments/my-orders")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.totalElements", equalTo(1))
                .body("data.content[0].orderId", equalTo("ORD-C1"));
    }

    @Test
    @DisplayName("GET /api/assignments/driver/orders returns 200 OK")
    void getDriverOrders_apiPrefix_success() {
        given()
                .header("Authorization", driver1Token)
                .contentType(ContentType.JSON)
                .when()
                .get("/api/assignments/driver/orders")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.totalElements", equalTo(1))
                .body("data.content[0].orderId", equalTo("ORD-C1"));
    }

    @Test
    @DisplayName("GET /api/shipments/assignments/driver/orders and /shipments/assignments/driver/orders returns 200 OK")
    void getDriverOrders_shipmentsPrefix_success() {
        given()
                .header("Authorization", driver1Token)
                .contentType(ContentType.JSON)
                .when()
                .get("/api/shipments/assignments/driver/orders")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.totalElements", equalTo(1))
                .body("data.content[0].orderId", equalTo("ORD-C1"));

        given()
                .header("Authorization", driver1Token)
                .contentType(ContentType.JSON)
                .when()
                .get("/shipments/assignments/driver/orders")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.totalElements", equalTo(1))
                .body("data.content[0].orderId", equalTo("ORD-C1"));
    }

    @Test
    @DisplayName("GET /assignments/driver/orders without token returns 401 Unauthorized")
    void getDriverOrders_missingToken_401() {
        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/assignments/driver/orders")
                .then()
                .statusCode(401);
    }

    @Test
    @DisplayName("GET /assignments/driver/orders with invalid token returns 401 Unauthorized")
    void getDriverOrders_invalidToken_401() {
        given()
                .header("Authorization", "Bearer invalid-jwt")
                .contentType(ContentType.JSON)
                .when()
                .get("/assignments/driver/orders")
                .then()
                .statusCode(401);
    }

    @Test
    @DisplayName("GET /assignments/driver/orders by non-driver roles returns 403 Forbidden")
    void getDriverOrders_nonDriverRoles_403() {
        // Customer
        given()
                .header("Authorization", customerToken)
                .contentType(ContentType.JSON)
                .when()
                .get("/assignments/driver/orders")
                .then()
                .statusCode(403);

        // Dispatcher
        given()
                .header("Authorization", dispatcherToken)
                .contentType(ContentType.JSON)
                .when()
                .get("/assignments/driver/orders")
                .then()
                .statusCode(403);

        // Admin
        given()
                .header("Authorization", adminToken)
                .contentType(ContentType.JSON)
                .when()
                .get("/assignments/driver/orders")
                .then()
                .statusCode(403);
    }

    @Test
    @DisplayName("GET /assignments/driver/orders when driver has no orders returns 200 with empty list")
    void getDriverOrders_noOrders_returnsEmptyList() {
        given()
                .header("Authorization", driver3Token)
                .contentType(ContentType.JSON)
                .when()
                .get("/assignments/driver/orders")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.totalElements", equalTo(0))
                .body("data.content", hasSize(0));
    }
}
