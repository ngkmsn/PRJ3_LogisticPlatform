package com.logistics.shipment.controller;

import com.logistics.common.security.JwtTokenProvider;
import com.logistics.common.security.Role;
import com.logistics.shipment.dto.ReassignDriverRequest;
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
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class ReassignmentControllerTest {

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

        assignmentRepository.deleteAll();

        Instant now = Instant.now();
        // Seed assigned order
        persistAssignment("ORD-CTRL-REASSIGN", AssignmentStatus.ASSIGNED, "DRV-INITIAL-1", now.minus(30, ChronoUnit.MINUTES));
        // Seed unassigned order
        persistAssignment("ORD-CTRL-UNASSIGNED", AssignmentStatus.UNASSIGNED, null, now.minus(15, ChronoUnit.MINUTES));
    }

    private void persistAssignment(String orderId, AssignmentStatus status, String driverId, Instant time) {
        Assignment a = new Assignment();
        a.setId(UUID.randomUUID().toString());
        a.setOrderId(orderId);
        a.setOrderNumber("ORD-" + orderId);
        a.setCustomerId("cust-" + orderId);
        a.setRecipientName("Recipient " + orderId);
        a.setRecipientPhone("0901234567");
        a.setDeliveryAddress("Delivery Address " + orderId);
        a.setTotalAmount(new BigDecimal("300000.00"));
        a.setStatus(status);
        a.setDriverId(driverId);
        a.setAssignedAt(status == AssignmentStatus.ASSIGNED ? time : null);
        a.setCreatedAt(time);
        a.setUpdatedAt(time);
        assignmentRepository.persist(a);
    }

    @Test
    @DisplayName("POST /assignments/{orderId}/reassign returns 200 OK with reassigned metadata")
    void reassign_success_asDispatcher() {
        ReassignDriverRequest req = new ReassignDriverRequest("DRV-REPLACE-2", "Driver delayed due to traffic", "Urgent replacement");

        given()
                .header("Authorization", dispatcherToken)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/assignments/ORD-CTRL-REASSIGN/reassign")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.orderId", equalTo("ORD-CTRL-REASSIGN"))
                .body("data.status", equalTo("ASSIGNED"))
                .body("data.previousDriverId", equalTo("DRV-INITIAL-1"))
                .body("data.driverId", equalTo("DRV-REPLACE-2"))
                .body("data.reassignReason", equalTo("Driver delayed due to traffic"))
                .body("data.reassignedBy", equalTo("dispatcher1"))
                .body("data.reassignedAt", notNullValue())
                .body("data.assignmentSource", equalTo("MANUAL"))
                .body("data.notes", containsString("[Reassigned] Urgent replacement"));
    }

    @Test
    @DisplayName("POST /api/assignments/{orderId}/reassign returns 200 OK when called via /api prefix")
    void reassign_success_apiPrefix() {
        ReassignDriverRequest req = new ReassignDriverRequest("DRV-REPLACE-2", "Driver delayed");

        given()
                .header("Authorization", adminToken)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/api/assignments/ORD-CTRL-REASSIGN/reassign")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.previousDriverId", equalTo("DRV-INITIAL-1"))
                .body("data.driverId", equalTo("DRV-REPLACE-2"));
    }

    @Test
    @DisplayName("POST /assignments/{orderId}/reassign returns 400 Bad Request when reassigning to same driver")
    void reassign_badRequest_sameDriver() {
        ReassignDriverRequest req = new ReassignDriverRequest("DRV-INITIAL-1", "Accidental same driver");

        given()
                .header("Authorization", dispatcherToken)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/assignments/ORD-CTRL-REASSIGN/reassign")
                .then()
                .statusCode(400)
                .body("success", equalTo(false))
                .body("message", containsString("New driver cannot be the same as the currently assigned driver"));
    }

    @Test
    @DisplayName("POST /assignments/{orderId}/reassign returns 400 Bad Request when order is not yet assigned")
    void reassign_badRequest_orderNotAssigned() {
        ReassignDriverRequest req = new ReassignDriverRequest("DRV-REPLACE-2", "Reason");

        given()
                .header("Authorization", dispatcherToken)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/assignments/ORD-CTRL-UNASSIGNED/reassign")
                .then()
                .statusCode(400)
                .body("success", equalTo(false))
                .body("message", containsString("does not have an active assignment"));
    }

    @Test
    @DisplayName("POST /assignments/{orderId}/reassign returns 400 Bad Request when new driver is invalid/offline")
    void reassign_badRequest_invalidDriver() {
        ReassignDriverRequest req = new ReassignDriverRequest("INVALID", "Reason");

        given()
                .header("Authorization", adminToken)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/assignments/ORD-CTRL-REASSIGN/reassign")
                .then()
                .statusCode(400)
                .body("success", equalTo(false))
                .body("message", containsString("does not exist or is not available"));
    }

    @Test
    @DisplayName("POST /assignments/{orderId}/reassign returns 404 Not Found when orderId does not exist")
    void reassign_notFound_whenOrderMissing() {
        ReassignDriverRequest req = new ReassignDriverRequest("DRV-REPLACE-2", "Reason");

        given()
                .header("Authorization", dispatcherToken)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/assignments/NON-EXISTENT-ORDER/reassign")
                .then()
                .statusCode(404)
                .body("success", equalTo(false))
                .body("message", containsString("No assignment record found"));
    }

    @Test
    @DisplayName("POST /assignments/{orderId}/reassign returns 401 Unauthorized when missing token")
    void reassign_unauthorized_whenMissingToken() {
        ReassignDriverRequest req = new ReassignDriverRequest("DRV-REPLACE-2", "Reason");

        given()
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/assignments/ORD-CTRL-REASSIGN/reassign")
                .then()
                .statusCode(401)
                .body("success", equalTo(false))
                .body("message", containsString("Missing or invalid Authorization header"));
    }

    @Test
    @DisplayName("POST /assignments/{orderId}/reassign returns 403 Forbidden when caller is CUSTOMER or DRIVER")
    void reassign_forbidden_whenUnauthorizedRole() {
        ReassignDriverRequest req = new ReassignDriverRequest("DRV-REPLACE-2", "Reason");

        given()
                .header("Authorization", customerToken)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/assignments/ORD-CTRL-REASSIGN/reassign")
                .then()
                .statusCode(403)
                .body("success", equalTo(false))
                .body("message", containsString("Access denied"));

        given()
                .header("Authorization", driverToken)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/assignments/ORD-CTRL-REASSIGN/reassign")
                .then()
                .statusCode(403)
                .body("success", equalTo(false))
                .body("message", containsString("Access denied"));
    }
}
