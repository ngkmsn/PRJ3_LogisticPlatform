package com.logistics.shipment.controller;

import com.logistics.common.security.JwtTokenProvider;
import com.logistics.common.security.Role;
import com.logistics.shipment.dto.StartTransitRequest;
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
class DriverTransitControllerTest {

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

        // Pre-seed orders in PICKED_UP status ready for transit
        createAssignment("ORD-TR-1", "NUM-TR-1", driver1Id, AssignmentStatus.PICKED_UP);
        createAssignment("ORD-TR-2", "NUM-TR-2", driver2Id, AssignmentStatus.PICKED_UP);
        createAssignment("ORD-TR-ASSIGNED", "NUM-TR-3", driver1Id, AssignmentStatus.ASSIGNED);
        createAssignment("ORD-TR-CANCELLED", "NUM-TR-4", driver1Id, AssignmentStatus.CANCELLED);
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
        if (status == AssignmentStatus.PICKED_UP) {
            a.setPickedUpAt(now);
            a.setPickupNotes("Pre-picked up");
        }
        a.setNotes("Call before delivery");
        a.setCreatedAt(now);
        a.setUpdatedAt(now);
        assignmentRepository.persist(a);
    }

    @Test
    @DisplayName("POST /assignments/{orderId}/transit returns 200 OK with IN_TRANSIT status")
    void startTransit_mainEndpoint_success() {
        StartTransitRequest req = new StartTransitRequest("Out for delivery, arriving in ~30 mins");

        given()
                .header("Authorization", driver1Token)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/assignments/ORD-TR-1/transit")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.orderId", equalTo("ORD-TR-1"))
                .body("data.status", equalTo("IN_TRANSIT"))
                .body("data.transitStartedAt", notNullValue())
                .body("data.transitNotes", equalTo("Out for delivery, arriving in ~30 mins"));
    }

    @Test
    @DisplayName("POST /assignments/{orderId}/start-transit alias works correctly")
    void startTransit_alias_success() {
        StartTransitRequest req = new StartTransitRequest("Alias start transit test");

        given()
                .header("Authorization", driver1Token)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/assignments/ORD-TR-1/start-transit")
                .then()
                .statusCode(200)
                .body("data.status", equalTo("IN_TRANSIT"));
    }

    @Test
    @DisplayName("POST /api/assignments/{orderId}/transit works with api prefix")
    void startTransit_apiAssignments_success() {
        StartTransitRequest req = new StartTransitRequest("API alias test");

        given()
                .header("Authorization", driver1Token)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/api/assignments/ORD-TR-1/transit")
                .then()
                .statusCode(200)
                .body("data.status", equalTo("IN_TRANSIT"));
    }

    @Test
    @DisplayName("POST /api/shipments/assignments/{orderId}/transit works with api/shipments prefix")
    void startTransit_apiShipmentsAssignments_success() {
        StartTransitRequest req = new StartTransitRequest("API shipments alias test");

        given()
                .header("Authorization", driver1Token)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/api/shipments/assignments/ORD-TR-1/transit")
                .then()
                .statusCode(200)
                .body("data.status", equalTo("IN_TRANSIT"));
    }

    @Test
    @DisplayName("POST /shipments/assignments/{orderId}/transit works with shipments prefix")
    void startTransit_shipmentsAssignments_success() {
        StartTransitRequest req = new StartTransitRequest("Shipments alias test");

        given()
                .header("Authorization", driver1Token)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/shipments/assignments/ORD-TR-1/transit")
                .then()
                .statusCode(200)
                .body("data.status", equalTo("IN_TRANSIT"));
    }

    @Test
    @DisplayName("POST /assignments/{orderId}/transit returns 403 Forbidden when called by unassigned driver")
    void startTransit_unassignedDriver_forbidden() {
        StartTransitRequest req = new StartTransitRequest("Hijack transit attempt");

        given()
                .header("Authorization", driver2Token)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/assignments/ORD-TR-1/transit")
                .then()
                .statusCode(403)
                .body("success", equalTo(false));
    }

    @Test
    @DisplayName("POST /assignments/{orderId}/transit returns 403 Forbidden for non-driver role")
    void startTransit_nonDriverRole_forbidden() {
        StartTransitRequest req = new StartTransitRequest("Customer attempt");

        given()
                .header("Authorization", customerToken)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/assignments/ORD-TR-1/transit")
                .then()
                .statusCode(403);

        given()
                .header("Authorization", dispatcherToken)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/assignments/ORD-TR-1/transit")
                .then()
                .statusCode(403);
    }

    @Test
    @DisplayName("POST /assignments/{orderId}/transit returns 401 Unauthorized without auth header")
    void startTransit_noAuth_unauthorized() {
        given()
                .contentType(ContentType.JSON)
                .body(new StartTransitRequest("No auth"))
                .when()
                .post("/assignments/ORD-TR-1/transit")
                .then()
                .statusCode(401);
    }

    @Test
    @DisplayName("POST /assignments/{orderId}/transit returns 400 Bad Request when not yet PICKED_UP")
    void startTransit_notPickedUp_badRequest() {
        given()
                .header("Authorization", driver1Token)
                .contentType(ContentType.JSON)
                .body(new StartTransitRequest("Start transit while only ASSIGNED"))
                .when()
                .post("/assignments/ORD-TR-ASSIGNED/transit")
                .then()
                .statusCode(400)
                .body("success", equalTo(false));
    }

    @Test
    @DisplayName("POST /assignments/{orderId}/transit returns 400 Bad Request on cancelled state")
    void startTransit_cancelledState_badRequest() {
        given()
                .header("Authorization", driver1Token)
                .contentType(ContentType.JSON)
                .body(new StartTransitRequest("Start transit on cancelled order"))
                .when()
                .post("/assignments/ORD-TR-CANCELLED/transit")
                .then()
                .statusCode(400)
                .body("success", equalTo(false));
    }

    @Test
    @DisplayName("POST /assignments/{orderId}/transit returns 404 Not Found for non-existent order")
    void startTransit_notFound() {
        given()
                .header("Authorization", driver1Token)
                .contentType(ContentType.JSON)
                .body(new StartTransitRequest("Non existent"))
                .when()
                .post("/assignments/ORD-DOES-NOT-EXIST/transit")
                .then()
                .statusCode(404)
                .body("success", equalTo(false));
    }
}
