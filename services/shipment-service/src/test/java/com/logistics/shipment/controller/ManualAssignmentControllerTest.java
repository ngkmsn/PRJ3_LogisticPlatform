package com.logistics.shipment.controller;

import com.logistics.common.security.JwtTokenProvider;
import com.logistics.common.security.Role;
import com.logistics.shipment.dto.ManualAssignDriverRequest;
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
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
class ManualAssignmentControllerTest {

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
        // Seed unassigned order
        persistAssignment("ORD-CTRL-MAN1", AssignmentStatus.UNASSIGNED, null, "NO_ELIGIBLE_DRIVER", now.minus(20, ChronoUnit.MINUTES));
        // Seed assigned order
        persistAssignment("ORD-CTRL-ALREADY", AssignmentStatus.ASSIGNED, "DRV-EXISTING", null, now.minus(30, ChronoUnit.MINUTES));
    }

    private void persistAssignment(String orderId, AssignmentStatus status, String driverId,
                                   String unassignedReason, Instant time) {
        Assignment a = new Assignment();
        a.setId(UUID.randomUUID().toString());
        a.setOrderId(orderId);
        a.setOrderNumber("ORD-" + orderId);
        a.setCustomerId("cust-" + orderId);
        a.setRecipientName("Recipient " + orderId);
        a.setRecipientPhone("0901234567");
        a.setDeliveryAddress("Delivery Address " + orderId);
        a.setTotalAmount(new BigDecimal("210000.00"));
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
    @DisplayName("POST /assignments/{orderId}/manual-assign returns 200 OK and marks assignment source as MANUAL")
    void manualAssign_success_asDispatcher() {
        ManualAssignDriverRequest req = new ManualAssignDriverRequest("DRV-SUCCESS-1", "Manual dispatch call");

        given()
                .header("Authorization", dispatcherToken)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/assignments/ORD-CTRL-MAN1/manual-assign")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.orderId", equalTo("ORD-CTRL-MAN1"))
                .body("data.status", equalTo("ASSIGNED"))
                .body("data.driverId", equalTo("DRV-SUCCESS-1"))
                .body("data.assignmentSource", equalTo("MANUAL"))
                .body("data.assignedBy", equalTo("dispatcher1"))
                .body("data.assignedAt", notNullValue())
                .body("data.notes", containsString("[Manual Assign] Manual dispatch call"));
    }

    @Test
    @DisplayName("POST /api/assignments/{orderId}/manual-assign returns 409 Conflict when order is already assigned")
    void manualAssign_conflict_whenAlreadyAssigned() {
        ManualAssignDriverRequest req = new ManualAssignDriverRequest("DRV-NEW");

        given()
                .header("Authorization", dispatcherToken)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/api/assignments/ORD-CTRL-ALREADY/manual-assign")
                .then()
                .statusCode(409)
                .body("success", equalTo(false))
                .body("message", containsString("is already assigned to driver"));
    }

    @Test
    @DisplayName("POST /assignments/{orderId}/manual-assign returns 400 Bad Request when driver is invalid/offline")
    void manualAssign_badRequest_whenDriverInvalid() {
        ManualAssignDriverRequest req = new ManualAssignDriverRequest("INVALID");

        given()
                .header("Authorization", adminToken)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/assignments/ORD-CTRL-MAN1/manual-assign")
                .then()
                .statusCode(400)
                .body("success", equalTo(false))
                .body("message", containsString("does not exist or is not available"));
    }

    @Test
    @DisplayName("POST /assignments/{orderId}/manual-assign returns 404 Not Found when orderId does not exist")
    void manualAssign_notFound_whenOrderMissing() {
        ManualAssignDriverRequest req = new ManualAssignDriverRequest("DRV-1");

        given()
                .header("Authorization", dispatcherToken)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/assignments/NON-EXISTENT-ORDER/manual-assign")
                .then()
                .statusCode(404)
                .body("success", equalTo(false))
                .body("message", containsString("No assignment record found"));
    }

    @Test
    @DisplayName("POST /assignments/{orderId}/manual-assign returns 401 Unauthorized when missing token")
    void manualAssign_unauthorized_whenMissingToken() {
        ManualAssignDriverRequest req = new ManualAssignDriverRequest("DRV-1");

        given()
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/assignments/ORD-CTRL-MAN1/manual-assign")
                .then()
                .statusCode(401)
                .body("success", equalTo(false))
                .body("message", containsString("Missing or invalid Authorization header"));
    }

    @Test
    @DisplayName("POST /assignments/{orderId}/manual-assign returns 403 Forbidden when caller is CUSTOMER or DRIVER")
    void manualAssign_forbidden_whenUnauthorizedRole() {
        ManualAssignDriverRequest req = new ManualAssignDriverRequest("DRV-1");

        given()
                .header("Authorization", customerToken)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/assignments/ORD-CTRL-MAN1/manual-assign")
                .then()
                .statusCode(403)
                .body("success", equalTo(false))
                .body("message", containsString("Access denied"));

        given()
                .header("Authorization", driverToken)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/assignments/ORD-CTRL-MAN1/manual-assign")
                .then()
                .statusCode(403)
                .body("success", equalTo(false))
                .body("message", containsString("Access denied"));
    }
}
