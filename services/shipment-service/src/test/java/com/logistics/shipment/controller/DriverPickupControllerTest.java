package com.logistics.shipment.controller;

import com.logistics.common.security.JwtTokenProvider;
import com.logistics.common.security.Role;
import com.logistics.shipment.dto.ConfirmPickupRequest;
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
class DriverPickupControllerTest {

    @Inject
    JwtTokenProvider jwtTokenProvider;

    @Inject
    AssignmentRepository assignmentRepository;

    private String driver1Token;
    private String driver2Token;
    private String customerToken;
    private String dispatcherToken;

    private final String driver1Id = "drv-ctrl-1";
    private final String driver2Id = "drv-ctrl-2";

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

        createAssignment("ORD-PK-1", "NUM-PK-1", driver1Id, AssignmentStatus.ASSIGNED);
        createAssignment("ORD-PK-2", "NUM-PK-2", driver2Id, AssignmentStatus.ASSIGNED);
        createAssignment("ORD-PK-CANCELLED", "NUM-PK-3", driver1Id, AssignmentStatus.CANCELLED);
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
    @DisplayName("POST /assignments/{orderId}/pickup returns 200 OK with PICKED_UP status")
    void confirmPickup_mainEndpoint_success() {
        ConfirmPickupRequest req = new ConfirmPickupRequest("Package picked up from warehouse");

        given()
                .header("Authorization", driver1Token)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/assignments/ORD-PK-1/pickup")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.orderId", equalTo("ORD-PK-1"))
                .body("data.status", equalTo("PICKED_UP"))
                .body("data.pickedUpAt", notNullValue())
                .body("data.pickupNotes", equalTo("Package picked up from warehouse"));
    }

    @Test
    @DisplayName("POST /api/assignments/{orderId}/pickup works with api prefix")
    void confirmPickup_apiAssignments_success() {
        ConfirmPickupRequest req = new ConfirmPickupRequest("API alias test");

        given()
                .header("Authorization", driver1Token)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/api/assignments/ORD-PK-1/pickup")
                .then()
                .statusCode(200)
                .body("data.status", equalTo("PICKED_UP"));
    }

    @Test
    @DisplayName("POST /api/shipments/assignments/{orderId}/pickup works with api/shipments prefix")
    void confirmPickup_apiShipmentsAssignments_success() {
        ConfirmPickupRequest req = new ConfirmPickupRequest("API shipments alias test");

        given()
                .header("Authorization", driver1Token)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/api/shipments/assignments/ORD-PK-1/pickup")
                .then()
                .statusCode(200)
                .body("data.status", equalTo("PICKED_UP"));
    }

    @Test
    @DisplayName("POST /shipments/assignments/{orderId}/pickup works with shipments prefix")
    void confirmPickup_shipmentsAssignments_success() {
        ConfirmPickupRequest req = new ConfirmPickupRequest("Shipments alias test");

        given()
                .header("Authorization", driver1Token)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/shipments/assignments/ORD-PK-1/pickup")
                .then()
                .statusCode(200)
                .body("data.status", equalTo("PICKED_UP"));
    }

    @Test
    @DisplayName("POST /assignments/{orderId}/pickup returns 403 Forbidden when called by unassigned driver")
    void confirmPickup_unassignedDriver_forbidden() {
        ConfirmPickupRequest req = new ConfirmPickupRequest("Hijack attempt");

        given()
                .header("Authorization", driver2Token)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/assignments/ORD-PK-1/pickup")
                .then()
                .statusCode(403)
                .body("success", equalTo(false));
    }

    @Test
    @DisplayName("POST /assignments/{orderId}/pickup returns 403 Forbidden for non-driver role")
    void confirmPickup_nonDriverRole_forbidden() {
        ConfirmPickupRequest req = new ConfirmPickupRequest("Customer attempt");

        given()
                .header("Authorization", customerToken)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/assignments/ORD-PK-1/pickup")
                .then()
                .statusCode(403);

        given()
                .header("Authorization", dispatcherToken)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/assignments/ORD-PK-1/pickup")
                .then()
                .statusCode(403);
    }

    @Test
    @DisplayName("POST /assignments/{orderId}/pickup returns 401 Unauthorized without auth header")
    void confirmPickup_noAuth_unauthorized() {
        given()
                .contentType(ContentType.JSON)
                .body(new ConfirmPickupRequest("No auth"))
                .when()
                .post("/assignments/ORD-PK-1/pickup")
                .then()
                .statusCode(401);
    }

    @Test
    @DisplayName("POST /assignments/{orderId}/pickup returns 400 Bad Request on invalid state")
    void confirmPickup_invalidState_badRequest() {
        given()
                .header("Authorization", driver1Token)
                .contentType(ContentType.JSON)
                .body(new ConfirmPickupRequest("Pickup cancelled order"))
                .when()
                .post("/assignments/ORD-PK-CANCELLED/pickup")
                .then()
                .statusCode(400)
                .body("success", equalTo(false));
    }

    @Test
    @DisplayName("POST /assignments/{orderId}/pickup returns 404 Not Found for non-existent order")
    void confirmPickup_notFound() {
        given()
                .header("Authorization", driver1Token)
                .contentType(ContentType.JSON)
                .body(new ConfirmPickupRequest("Non existent"))
                .when()
                .post("/assignments/ORD-DOES-NOT-EXIST/pickup")
                .then()
                .statusCode(404)
                .body("success", equalTo(false));
    }
}
