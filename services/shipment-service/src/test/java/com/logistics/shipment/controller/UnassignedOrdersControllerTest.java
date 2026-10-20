package com.logistics.shipment.controller;

import com.logistics.common.security.JwtTokenProvider;
import com.logistics.common.security.Role;
import com.logistics.shipment.entity.Assignment;
import com.logistics.shipment.model.AssignmentStatus;
import com.logistics.shipment.repository.AssignmentRepository;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

@QuarkusTest
class UnassignedOrdersControllerTest {

    @Inject
    JwtTokenProvider jwtTokenProvider;

    @Inject
    AssignmentRepository assignmentRepository;

    private String dispatcherToken;
    private String adminToken;
    private String customerToken;
    private String driverToken;

    @BeforeEach
    @Transactional
    void setUp() {
        dispatcherToken = "Bearer " + jwtTokenProvider.generateToken(
                "disp-user-1", "dispatcher1", "dispatcher1@logistics.com", Role.DISPATCHER.name()
        );
        adminToken = "Bearer " + jwtTokenProvider.generateToken(
                "admin-user-1", "admin1", "admin1@logistics.com", Role.ADMIN.name()
        );
        customerToken = "Bearer " + jwtTokenProvider.generateToken(
                "cust-user-1", "customer1", "customer1@logistics.com", Role.CUSTOMER.name()
        );
        driverToken = "Bearer " + jwtTokenProvider.generateToken(
                "driver-user-1", "driver1", "driver1@logistics.com", Role.DRIVER.name()
        );

        // Ensure clean test baseline
        assignmentRepository.deleteAll();

        Instant now = Instant.now();

        // Seed 3 unassigned orders
        persistAssignment("ORD-CTRL-UN1", AssignmentStatus.UNASSIGNED, null, "NO_ELIGIBLE_DRIVER", now.minus(20, ChronoUnit.MINUTES));
        persistAssignment("ORD-CTRL-UN2", AssignmentStatus.UNASSIGNED, null, "ALL_DRIVERS_BUSY", now.minus(15, ChronoUnit.MINUTES));
        persistAssignment("ORD-CTRL-UN3", AssignmentStatus.UNASSIGNED, null, "NO_ELIGIBLE_DRIVER", now.minus(5, ChronoUnit.MINUTES));

        // Seed 2 assigned orders (PB-031)
        persistAssignment("ORD-CTRL-AS1", AssignmentStatus.ASSIGNED, "DRV-1", null, now.minus(30, ChronoUnit.MINUTES));
        persistAssignment("ORD-CTRL-AS2", AssignmentStatus.ASSIGNED, "DRV-2", null, now.minus(25, ChronoUnit.MINUTES));
    }

    private void persistAssignment(String orderId, AssignmentStatus status, String driverId,
                                   String unassignedReason, Instant time) {
        Assignment a = new Assignment();
        a.setId(UUID.randomUUID().toString());
        a.setOrderId(orderId);
        a.setOrderNumber("NUM-" + orderId);
        a.setCustomerId("CUST-" + orderId);
        a.setRecipientName("Recipient " + orderId);
        a.setRecipientPhone("0987654321");
        a.setDeliveryAddress("123 Delivery Street, Hanoi");
        a.setTotalAmount(new BigDecimal("250000.00"));
        a.setStatus(status);
        a.setDriverId(driverId);
        a.setUnassignedReason(unassignedReason);
        a.setUnassignedAt(status == AssignmentStatus.UNASSIGNED ? time : null);
        a.setAssignedAt(status == AssignmentStatus.ASSIGNED ? time : null);
        a.setCreatedAt(time);
        a.setUpdatedAt(time);
        assignmentRepository.persist(a);
    }

    @Test
    @DisplayName("GET /api/assignments/unassigned without Authorization header returns 401 Unauthorized")
    void getUnassigned_unauthorized_whenMissingToken() {
        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/api/assignments/unassigned")
                .then()
                .statusCode(401)
                .body("success", equalTo(false))
                .body("message", containsString("Missing or invalid Authorization header"));
    }

    @Test
    @DisplayName("GET /api/assignments/unassigned with CUSTOMER role returns 403 Forbidden")
    void getUnassigned_forbidden_whenCustomerRole() {
        given()
                .header("Authorization", customerToken)
                .contentType(ContentType.JSON)
                .when()
                .get("/api/assignments/unassigned")
                .then()
                .statusCode(403)
                .body("success", equalTo(false))
                .body("message", containsString("Access denied"));
    }

    @Test
    @DisplayName("GET /api/assignments/unassigned with DRIVER role returns 403 Forbidden")
    void getUnassigned_forbidden_whenDriverRole() {
        given()
                .header("Authorization", driverToken)
                .contentType(ContentType.JSON)
                .when()
                .get("/api/assignments/unassigned")
                .then()
                .statusCode(403)
                .body("success", equalTo(false))
                .body("message", containsString("Access denied"));
    }

    @Test
    @DisplayName("GET /api/assignments/unassigned with DISPATCHER role returns 200 OK and excludes ASSIGNED orders")
    void getUnassigned_success_asDispatcher() {
        given()
                .header("Authorization", dispatcherToken)
                .queryParam("page", 0)
                .queryParam("size", 10)
                .queryParam("sort", "oldest_first")
                .contentType(ContentType.JSON)
                .when()
                .get("/api/assignments/unassigned")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.totalElements", equalTo(3))
                .body("data.totalPages", equalTo(1))
                .body("data.page", equalTo(0))
                .body("data.content.size()", equalTo(3))
                .body("data.content.status", everyItem(equalTo("UNASSIGNED")))
                .body("data.content.orderId", hasItem("ORD-CTRL-UN1"))
                .body("data.content.orderId", hasItem("ORD-CTRL-UN2"))
                .body("data.content.orderId", hasItem("ORD-CTRL-UN3"))
                // ASSIGNED orders are strictly excluded
                .body("data.content.orderId", not(hasItem("ORD-CTRL-AS1")))
                .body("data.content.orderId", not(hasItem("ORD-CTRL-AS2")));
    }

    @Test
    @DisplayName("GET /assignments/unassigned works with ADMIN role and supports pagination")
    void getUnassigned_pagination_asAdmin() {
        given()
                .header("Authorization", adminToken)
                .queryParam("page", 0)
                .queryParam("size", 2)
                .queryParam("sort", "oldest_first")
                .contentType(ContentType.JSON)
                .when()
                .get("/assignments/unassigned")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.totalElements", equalTo(3))
                .body("data.totalPages", equalTo(2))
                .body("data.page", equalTo(0))
                .body("data.content.size()", equalTo(2))
                .body("data.content[0].orderId", equalTo("ORD-CTRL-UN1"))
                .body("data.content[1].orderId", equalTo("ORD-CTRL-UN2"));
    }

    @Test
    @DisplayName("GET /api/assignments/unassigned/{orderId} returns 200 OK for unassigned order")
    void getUnassignedDetail_success() {
        given()
                .header("Authorization", dispatcherToken)
                .contentType(ContentType.JSON)
                .when()
                .get("/api/assignments/unassigned/ORD-CTRL-UN1")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.orderId", equalTo("ORD-CTRL-UN1"))
                .body("data.status", equalTo("UNASSIGNED"))
                .body("data.unassignedReason", equalTo("NO_ELIGIBLE_DRIVER"))
                .body("data.dropoffAddress", equalTo("123 Delivery Street, Hanoi"))
                .body("data.waitingTimeSeconds", greaterThanOrEqualTo(1000));
    }

    @Test
    @DisplayName("GET /api/assignments/unassigned/{orderId} returns 404 Not Found for non-existent or assigned order")
    void getUnassignedDetail_notFound() {
        // Non-existent
        given()
                .header("Authorization", dispatcherToken)
                .contentType(ContentType.JSON)
                .when()
                .get("/api/assignments/unassigned/ORD-UNKNOWN")
                .then()
                .statusCode(404)
                .body("success", equalTo(false))
                .body("message", containsString("No unassigned order found"));

        // Assigned order is not in unassigned queue -> 404
        given()
                .header("Authorization", dispatcherToken)
                .contentType(ContentType.JSON)
                .when()
                .get("/api/assignments/unassigned/ORD-CTRL-AS1")
                .then()
                .statusCode(404)
                .body("success", equalTo(false))
                .body("message", containsString("No unassigned order found"));
    }
}
