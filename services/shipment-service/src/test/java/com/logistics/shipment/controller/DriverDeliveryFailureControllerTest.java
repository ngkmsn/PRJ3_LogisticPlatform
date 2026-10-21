package com.logistics.shipment.controller;

import com.logistics.common.security.JwtTokenProvider;
import com.logistics.common.security.Role;
import com.logistics.shipment.dto.ReportDeliveryFailureRequest;
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
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class DriverDeliveryFailureControllerTest {

    @Inject
    JwtTokenProvider jwtTokenProvider;

    @Inject
    AssignmentRepository assignmentRepository;

    private String driver1Token;
    private String driver2Token;
    private String customerToken;
    private String dispatcherToken;

    private final String driver1Id = "drv-fail-ctrl-1";
    private final String driver2Id = "drv-fail-ctrl-2";

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
        customerToken = "Bearer " + jwtTokenProvider.generateToken(
                "cust-ctrl-1", "customer1", "cust@logistics.com", Role.CUSTOMER.name()
        );
        dispatcherToken = "Bearer " + jwtTokenProvider.generateToken(
                "disp-ctrl-1", "disp1", "disp@logistics.com", Role.DISPATCHER.name()
        );

        // Pre-seed orders in IN_TRANSIT, PICKED_UP, ASSIGNED, DELIVERED, CANCELLED
        createAssignment("ORD-FAIL-1", "NUM-FAIL-1", driver1Id, AssignmentStatus.IN_TRANSIT);
        createAssignment("ORD-FAIL-2", "NUM-FAIL-2", driver2Id, AssignmentStatus.IN_TRANSIT);
        createAssignment("ORD-FAIL-PICKED", "NUM-FAIL-3", driver1Id, AssignmentStatus.PICKED_UP);
        createAssignment("ORD-FAIL-ASSIGNED", "NUM-FAIL-4", driver1Id, AssignmentStatus.ASSIGNED);
        createAssignment("ORD-FAIL-DELIVERED", "NUM-FAIL-5", driver1Id, AssignmentStatus.DELIVERED);
        createAssignment("ORD-FAIL-CANCELLED", "NUM-FAIL-6", driver1Id, AssignmentStatus.CANCELLED);
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
        if (status == AssignmentStatus.PICKED_UP || status == AssignmentStatus.IN_TRANSIT || status == AssignmentStatus.DELIVERED) {
            a.setPickedUpAt(now);
            a.setPickupNotes("Pre-picked up");
        }
        if (status == AssignmentStatus.IN_TRANSIT || status == AssignmentStatus.DELIVERED) {
            a.setTransitStartedAt(now);
            a.setTransitNotes("Pre-transit");
        }
        if (status == AssignmentStatus.DELIVERED) {
            a.setDeliveredAt(now);
            a.setDeliveryNotes("Pre-delivered");
        }
        a.setNotes("Call before delivery");
        a.setCreatedAt(now);
        a.setUpdatedAt(now);
        assignmentRepository.persist(a);
    }

    @Test
    @DisplayName("POST /assignments/{orderId}/fail returns 200 OK with FAILED status")
    void reportFailure_mainEndpoint_success() {
        ReportDeliveryFailureRequest req = new ReportDeliveryFailureRequest(
                "RECIPIENT_UNREACHABLE",
                "Recipient phone disconnected, no one at home"
        );

        given()
                .header("Authorization", driver1Token)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/assignments/ORD-FAIL-1/fail")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.orderId", equalTo("ORD-FAIL-1"))
                .body("data.status", equalTo("FAILED"))
                .body("data.failedAt", notNullValue())
                .body("data.failureReason", equalTo("RECIPIENT_UNREACHABLE"))
                .body("data.failureNotes", equalTo("Recipient phone disconnected, no one at home"));
    }

    @Test
    @DisplayName("POST /assignments/{orderId}/failed alias works correctly")
    void reportFailure_failedAlias_success() {
        ReportDeliveryFailureRequest req = new ReportDeliveryFailureRequest("RECIPIENT_REFUSED");

        given()
                .header("Authorization", driver1Token)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/assignments/ORD-FAIL-1/failed")
                .then()
                .statusCode(200)
                .body("data.status", equalTo("FAILED"))
                .body("data.failureReason", equalTo("RECIPIENT_REFUSED"));
    }

    @Test
    @DisplayName("POST /assignments/{orderId}/fail-delivery alias works correctly")
    void reportFailure_failDeliveryAlias_success() {
        ReportDeliveryFailureRequest req = new ReportDeliveryFailureRequest("WRONG_ADDRESS");

        given()
                .header("Authorization", driver1Token)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/assignments/ORD-FAIL-1/fail-delivery")
                .then()
                .statusCode(200)
                .body("data.status", equalTo("FAILED"));
    }

    @Test
    @DisplayName("POST /api/assignments/{orderId}/fail works with api prefix")
    void reportFailure_apiAssignments_success() {
        ReportDeliveryFailureRequest req = new ReportDeliveryFailureRequest("DAMAGED_PACKAGE");

        given()
                .header("Authorization", driver1Token)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/api/assignments/ORD-FAIL-1/fail")
                .then()
                .statusCode(200)
                .body("data.status", equalTo("FAILED"));
    }

    @Test
    @DisplayName("POST /api/shipments/assignments/{orderId}/fail works with gateway prefix")
    void reportFailure_apiShipmentsAssignments_success() {
        ReportDeliveryFailureRequest req = new ReportDeliveryFailureRequest("RECIPIENT_UNREACHABLE");

        given()
                .header("Authorization", driver1Token)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/api/shipments/assignments/ORD-FAIL-1/fail")
                .then()
                .statusCode(200)
                .body("data.status", equalTo("FAILED"));
    }

    @Test
    @DisplayName("POST /shipments/assignments/{orderId}/fail works with shipments prefix")
    void reportFailure_shipmentsAssignments_success() {
        ReportDeliveryFailureRequest req = new ReportDeliveryFailureRequest("RECIPIENT_UNREACHABLE");

        given()
                .header("Authorization", driver1Token)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/shipments/assignments/ORD-FAIL-1/fail")
                .then()
                .statusCode(200)
                .body("data.status", equalTo("FAILED"));
    }

    @Test
    @DisplayName("Failure report with missing or blank failureReason returns 400 Bad Request")
    void reportFailure_missingReason_badRequest() {
        ReportDeliveryFailureRequest emptyReq = new ReportDeliveryFailureRequest("   ");

        given()
                .header("Authorization", driver1Token)
                .contentType(ContentType.JSON)
                .body(emptyReq)
                .when()
                .post("/assignments/ORD-FAIL-1/fail")
                .then()
                .statusCode(400)
                .body("success", equalTo(false));
    }

    @Test
    @DisplayName("Failure report by unassigned driver returns 403 Forbidden")
    void reportFailure_unassignedDriver_forbidden() {
        ReportDeliveryFailureRequest req = new ReportDeliveryFailureRequest("RECIPIENT_UNREACHABLE");

        given()
                .header("Authorization", driver2Token)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/assignments/ORD-FAIL-1/fail")
                .then()
                .statusCode(403)
                .body("success", equalTo(false));
    }

    @Test
    @DisplayName("Failure report by non-driver role returns 403 Forbidden")
    void reportFailure_nonDriver_forbidden() {
        ReportDeliveryFailureRequest req = new ReportDeliveryFailureRequest("RECIPIENT_UNREACHABLE");

        given()
                .header("Authorization", customerToken)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/assignments/ORD-FAIL-1/fail")
                .then()
                .statusCode(403)
                .body("success", equalTo(false));

        given()
                .header("Authorization", dispatcherToken)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/assignments/ORD-FAIL-1/fail")
                .then()
                .statusCode(403)
                .body("success", equalTo(false));
    }

    @Test
    @DisplayName("Failure report without token returns 401 Unauthorized")
    void reportFailure_missingToken_unauthorized() {
        ReportDeliveryFailureRequest req = new ReportDeliveryFailureRequest("RECIPIENT_UNREACHABLE");

        given()
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/assignments/ORD-FAIL-1/fail")
                .then()
                .statusCode(401)
                .body("success", equalTo(false));
    }

    @Test
    @DisplayName("Failure report on invalid status (ASSIGNED, DELIVERED, CANCELLED) returns 400 Bad Request")
    void reportFailure_invalidState_badRequest() {
        ReportDeliveryFailureRequest req = new ReportDeliveryFailureRequest("RECIPIENT_UNREACHABLE");

        // Order is still ASSIGNED (not yet picked up)
        given()
                .header("Authorization", driver1Token)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/assignments/ORD-FAIL-ASSIGNED/fail")
                .then()
                .statusCode(400)
                .body("success", equalTo(false));

        // Order is already DELIVERED
        given()
                .header("Authorization", driver1Token)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/assignments/ORD-FAIL-DELIVERED/fail")
                .then()
                .statusCode(400)
                .body("success", equalTo(false));

        // Order is CANCELLED
        given()
                .header("Authorization", driver1Token)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/assignments/ORD-FAIL-CANCELLED/fail")
                .then()
                .statusCode(400)
                .body("success", equalTo(false));
    }

    @Test
    @DisplayName("Failure report on non-existent order returns 404 Not Found")
    void reportFailure_nonExistentOrder_notFound() {
        ReportDeliveryFailureRequest req = new ReportDeliveryFailureRequest("RECIPIENT_UNREACHABLE");

        given()
                .header("Authorization", driver1Token)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/assignments/NON-EXISTENT-ORDER/fail")
                .then()
                .statusCode(404)
                .body("success", equalTo(false));
    }
}
