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
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class OperationsFailedOrderReassignmentControllerTest {

    @Inject
    JwtTokenProvider jwtTokenProvider;

    @Inject
    AssignmentRepository assignmentRepository;

    private String dispatcherToken;
    private String adminToken;
    private String customerToken;
    private String driverToken;

    private final String initialDriverId = "DRV-CTRL-FAIL-OLD";

    @BeforeEach
    @Transactional
    void setUp() {
        dispatcherToken = "Bearer " + jwtTokenProvider.generateToken(
                "disp-ctrl-43", "dispatcher_ctrl", "dispctrl@logistics.com", Role.DISPATCHER.name()
        );
        adminToken = "Bearer " + jwtTokenProvider.generateToken(
                "admin-ctrl-43", "admin_ctrl", "adminctrl@logistics.com", Role.ADMIN.name()
        );
        customerToken = "Bearer " + jwtTokenProvider.generateToken(
                "cust-ctrl-43", "customer_ctrl", "customerctrl@logistics.com", Role.CUSTOMER.name()
        );
        driverToken = "Bearer " + jwtTokenProvider.generateToken(
                "driver-ctrl-43", "driver_ctrl", "driverctrl@logistics.com", Role.DRIVER.name()
        );

        assignmentRepository.deleteAll();

        Instant now = Instant.now();
        // Seed failed order
        persistAssignment("ORD-CTRL-FAILED-43", AssignmentStatus.FAILED, initialDriverId,
                now.minus(40, ChronoUnit.MINUTES), "Recipient unreachable at address");

        // Seed another failed order for listing
        persistAssignment("ORD-CTRL-FAILED-44", AssignmentStatus.FAILED, initialDriverId,
                now.minus(30, ChronoUnit.MINUTES), "Package damaged in transit");

        // Seed delivered order
        persistAssignment("ORD-CTRL-DELIVERED-43", AssignmentStatus.DELIVERED, initialDriverId,
                now.minus(20, ChronoUnit.MINUTES), null);
    }

    private void persistAssignment(String orderId, AssignmentStatus status, String driverId, Instant time, String failureReason) {
        Assignment a = new Assignment();
        a.setId(UUID.randomUUID().toString());
        a.setOrderId(orderId);
        a.setOrderNumber("ORD-" + orderId);
        a.setCustomerId("cust-" + orderId);
        a.setRecipientName("Recipient " + orderId);
        a.setRecipientPhone("0987654321");
        a.setDeliveryAddress("123 Kim Ma, Ba Dinh, Hanoi");
        a.setTotalAmount(new BigDecimal("350000.00"));
        a.setStatus(status);
        a.setDriverId(driverId);
        if (status == AssignmentStatus.FAILED) {
            a.setFailedAt(time);
            a.setFailureReason(failureReason);
            a.setFailureNotes("Recipient was absent");
        } else if (status == AssignmentStatus.DELIVERED) {
            a.setDeliveredAt(time);
        }
        a.setCreatedAt(time);
        a.setUpdatedAt(time);
        assignmentRepository.persist(a);
    }

    @Test
    @DisplayName("GET /assignments/failed returns 200 OK with list of failed orders for Operations")
    void getFailedOrders_success() {
        given()
                .header("Authorization", dispatcherToken)
                .when()
                .get("/assignments/failed")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.totalElements", greaterThanOrEqualTo(2))
                .body("data.content[0].orderId", notNullValue())
                .body("data.content[0].failureReason", notNullValue());
    }

    @Test
    @DisplayName("GET /api/assignments/failed returns 200 OK via gateway alias")
    void getFailedOrders_gatewayAlias_success() {
        given()
                .header("Authorization", adminToken)
                .when()
                .get("/api/assignments/failed")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.totalElements", greaterThanOrEqualTo(2));
    }

    @Test
    @DisplayName("GET /assignments/failed/{orderId} returns 200 OK with failed order details")
    void getFailedOrderDetail_success() {
        given()
                .header("Authorization", dispatcherToken)
                .when()
                .get("/assignments/failed/ORD-CTRL-FAILED-43")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.orderId", equalTo("ORD-CTRL-FAILED-43"))
                .body("data.status", equalTo("FAILED"))
                .body("data.driverId", equalTo(initialDriverId))
                .body("data.failureReason", equalTo("Recipient unreachable at address"))
                .body("data.failedAt", notNullValue());
    }

    @Test
    @DisplayName("GET /assignments/unassigned?status=FAILED returns failed orders with failure metadata")
    void getUnassignedOrders_statusFailed_success() {
        given()
                .header("Authorization", adminToken)
                .queryParam("status", "FAILED")
                .when()
                .get("/assignments/unassigned")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.totalElements", greaterThanOrEqualTo(2))
                .body("data.content[0].failureReason", notNullValue());
    }

    @Test
    @DisplayName("POST /assignments/{orderId}/reassign successfully reassigns FAILED order to new driver")
    void reassign_failedOrder_success() {
        ReassignDriverRequest req = new ReassignDriverRequest(
                "DRV-NEW-2", "Recipient confirmed new schedule", "Dispatched immediately"
        );

        given()
                .header("Authorization", dispatcherToken)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/assignments/ORD-CTRL-FAILED-43/reassign")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.orderId", equalTo("ORD-CTRL-FAILED-43"))
                .body("data.status", equalTo("ASSIGNED"))
                .body("data.previousDriverId", equalTo(initialDriverId))
                .body("data.driverId", equalTo("DRV-NEW-2"))
                .body("data.reassignReason", equalTo("Recipient confirmed new schedule"))
                .body("data.reassignedBy", equalTo("dispatcher_ctrl"))
                .body("data.reassignedAt", notNullValue())
                .body("data.notes", containsString("[Previously Failed: Recipient unreachable at address"))
                .body("data.notes", containsString("[Reassigned] Dispatched immediately"));
    }

    @Test
    @DisplayName("POST /api/assignments/{orderId}/reassign works via /api prefix for FAILED order")
    void reassign_failedOrder_apiPrefix_success() {
        ReassignDriverRequest req = new ReassignDriverRequest("DRV-0881", "Operations reassign");

        given()
                .header("Authorization", adminToken)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/api/assignments/ORD-CTRL-FAILED-44/reassign")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.status", equalTo("ASSIGNED"))
                .body("data.previousDriverId", equalTo(initialDriverId))
                .body("data.driverId", equalTo("DRV-0881"));
    }

    @Test
    @DisplayName("POST /assignments/{orderId}/reassign returns 400 Bad Request when reassigning DELIVERED order")
    void reassign_badRequest_deliveredOrder() {
        ReassignDriverRequest req = new ReassignDriverRequest("DRV-NEW-2", "Reason");

        given()
                .header("Authorization", dispatcherToken)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/assignments/ORD-CTRL-DELIVERED-43/reassign")
                .then()
                .statusCode(400)
                .body("success", equalTo(false))
                .body("message", containsString("terminal status: DELIVERED"));
    }

    @Test
    @DisplayName("POST /assignments/{orderId}/reassign returns 400 Bad Request when reassigning to same driver")
    void reassign_badRequest_sameDriver() {
        ReassignDriverRequest req = new ReassignDriverRequest(initialDriverId, "Same driver");

        given()
                .header("Authorization", dispatcherToken)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/assignments/ORD-CTRL-FAILED-43/reassign")
                .then()
                .statusCode(400)
                .body("success", equalTo(false))
                .body("message", containsString("New driver cannot be the same as the currently assigned driver"));
    }

    @Test
    @DisplayName("POST /assignments/{orderId}/reassign returns 400 Bad Request when new driver is invalid")
    void reassign_badRequest_invalidDriver() {
        ReassignDriverRequest req = new ReassignDriverRequest("INVALID", "Reason");

        given()
                .header("Authorization", adminToken)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/assignments/ORD-CTRL-FAILED-43/reassign")
                .then()
                .statusCode(400)
                .body("success", equalTo(false))
                .body("message", containsString("does not exist or is not available"));
    }

    @Test
    @DisplayName("POST /assignments/{orderId}/reassign returns 404 Not Found when order does not exist")
    void reassign_notFound_whenOrderMissing() {
        ReassignDriverRequest req = new ReassignDriverRequest("DRV-NEW-2", "Reason");

        given()
                .header("Authorization", dispatcherToken)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/assignments/NON-EXISTENT-FAILED/reassign")
                .then()
                .statusCode(404)
                .body("success", equalTo(false))
                .body("message", containsString("No assignment record found"));
    }

    @Test
    @DisplayName("POST /assignments/{orderId}/reassign returns 401 Unauthorized when missing token")
    void reassign_unauthorized_whenMissingToken() {
        ReassignDriverRequest req = new ReassignDriverRequest("DRV-NEW-2", "Reason");

        given()
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/assignments/ORD-CTRL-FAILED-43/reassign")
                .then()
                .statusCode(401)
                .body("success", equalTo(false))
                .body("message", containsString("Missing or invalid Authorization header"));
    }

    @Test
    @DisplayName("POST /assignments/{orderId}/reassign returns 403 Forbidden when caller is DRIVER or CUSTOMER")
    void reassign_forbidden_whenUnauthorizedRole() {
        ReassignDriverRequest req = new ReassignDriverRequest("DRV-NEW-2", "Reason");

        given()
                .header("Authorization", driverToken)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/assignments/ORD-CTRL-FAILED-43/reassign")
                .then()
                .statusCode(403)
                .body("success", equalTo(false))
                .body("message", containsString("Access denied"));

        given()
                .header("Authorization", customerToken)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/assignments/ORD-CTRL-FAILED-43/reassign")
                .then()
                .statusCode(403)
                .body("success", equalTo(false))
                .body("message", containsString("Access denied"));
    }
}
