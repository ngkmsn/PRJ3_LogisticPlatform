package com.logistics.shipment.controller;

import com.logistics.common.security.JwtTokenProvider;
import com.logistics.common.security.Role;
import com.logistics.shipment.dto.ConfirmDeliveryRequest;
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
class DriverDeliveryControllerTest {

    @Inject
    JwtTokenProvider jwtTokenProvider;

    @Inject
    AssignmentRepository assignmentRepository;

    private String driver1Token;
    private String driver2Token;
    private String customerToken;
    private String dispatcherToken;

    private final String driver1Id = "drv-del-ctrl-1";
    private final String driver2Id = "drv-del-ctrl-2";

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

        // Pre-seed orders in IN_TRANSIT status ready for delivery
        createAssignment("ORD-DEL-1", "NUM-DEL-1", driver1Id, AssignmentStatus.IN_TRANSIT);
        createAssignment("ORD-DEL-2", "NUM-DEL-2", driver2Id, AssignmentStatus.IN_TRANSIT);
        createAssignment("ORD-DEL-PICKED", "NUM-DEL-3", driver1Id, AssignmentStatus.PICKED_UP);
        createAssignment("ORD-DEL-ASSIGNED", "NUM-DEL-4", driver1Id, AssignmentStatus.ASSIGNED);
        createAssignment("ORD-DEL-CANCELLED", "NUM-DEL-5", driver1Id, AssignmentStatus.CANCELLED);
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
        if (status == AssignmentStatus.PICKED_UP || status == AssignmentStatus.IN_TRANSIT) {
            a.setPickedUpAt(now);
            a.setPickupNotes("Pre-picked up");
        }
        if (status == AssignmentStatus.IN_TRANSIT) {
            a.setTransitStartedAt(now);
            a.setTransitNotes("Pre-transit");
        }
        a.setNotes("Call before delivery");
        a.setCreatedAt(now);
        a.setUpdatedAt(now);
        assignmentRepository.persist(a);
    }

    @Test
    @DisplayName("POST /assignments/{orderId}/deliver returns 200 OK with DELIVERED status")
    void confirmDelivery_mainEndpoint_success() {
        ConfirmDeliveryRequest req = new ConfirmDeliveryRequest(
                "Delivered to recipient successfully",
                "https://cdn.example.com/pod.png"
        );

        given()
                .header("Authorization", driver1Token)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/assignments/ORD-DEL-1/deliver")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.orderId", equalTo("ORD-DEL-1"))
                .body("data.status", equalTo("DELIVERED"))
                .body("data.deliveredAt", notNullValue())
                .body("data.deliveryNotes", equalTo("Delivered to recipient successfully"))
                .body("data.proofOfDelivery", equalTo("https://cdn.example.com/pod.png"));
    }

    @Test
    @DisplayName("POST /assignments/{orderId}/complete-delivery alias works correctly")
    void confirmDelivery_completeDeliveryAlias_success() {
        ConfirmDeliveryRequest req = new ConfirmDeliveryRequest("Complete delivery alias test");

        given()
                .header("Authorization", driver1Token)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/assignments/ORD-DEL-1/complete-delivery")
                .then()
                .statusCode(200)
                .body("data.status", equalTo("DELIVERED"));
    }

    @Test
    @DisplayName("POST /assignments/{orderId}/delivered alias works correctly")
    void confirmDelivery_deliveredAlias_success() {
        ConfirmDeliveryRequest req = new ConfirmDeliveryRequest("Delivered alias test");

        given()
                .header("Authorization", driver1Token)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/assignments/ORD-DEL-1/delivered")
                .then()
                .statusCode(200)
                .body("data.status", equalTo("DELIVERED"));
    }

    @Test
    @DisplayName("POST /api/assignments/{orderId}/deliver works with api prefix")
    void confirmDelivery_apiAssignments_success() {
        ConfirmDeliveryRequest req = new ConfirmDeliveryRequest("API alias test");

        given()
                .header("Authorization", driver1Token)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/api/assignments/ORD-DEL-1/deliver")
                .then()
                .statusCode(200)
                .body("data.status", equalTo("DELIVERED"));
    }

    @Test
    @DisplayName("POST /api/shipments/assignments/{orderId}/deliver works with gateway prefix")
    void confirmDelivery_apiShipmentsAssignments_success() {
        ConfirmDeliveryRequest req = new ConfirmDeliveryRequest("Gateway route test");

        given()
                .header("Authorization", driver1Token)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/api/shipments/assignments/ORD-DEL-1/deliver")
                .then()
                .statusCode(200)
                .body("data.status", equalTo("DELIVERED"));
    }

    @Test
    @DisplayName("POST /shipments/assignments/{orderId}/deliver works with shipments prefix")
    void confirmDelivery_shipmentsAssignments_success() {
        ConfirmDeliveryRequest req = new ConfirmDeliveryRequest("Shipments service route test");

        given()
                .header("Authorization", driver1Token)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/shipments/assignments/ORD-DEL-1/deliver")
                .then()
                .statusCode(200)
                .body("data.status", equalTo("DELIVERED"));
    }

    @Test
    @DisplayName("Delivery attempt by unassigned driver returns 403 Forbidden")
    void confirmDelivery_unassignedDriver_forbidden() {
        ConfirmDeliveryRequest req = new ConfirmDeliveryRequest("Unauthorized delivery attempt");

        given()
                .header("Authorization", driver2Token)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/assignments/ORD-DEL-1/deliver")
                .then()
                .statusCode(403)
                .body("success", equalTo(false));
    }

    @Test
    @DisplayName("Delivery attempt by non-driver role returns 403 Forbidden")
    void confirmDelivery_nonDriver_forbidden() {
        ConfirmDeliveryRequest req = new ConfirmDeliveryRequest("Non-driver delivery attempt");

        given()
                .header("Authorization", customerToken)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/assignments/ORD-DEL-1/deliver")
                .then()
                .statusCode(403)
                .body("success", equalTo(false));

        given()
                .header("Authorization", dispatcherToken)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/assignments/ORD-DEL-1/deliver")
                .then()
                .statusCode(403)
                .body("success", equalTo(false));
    }

    @Test
    @DisplayName("Delivery attempt without token returns 401 Unauthorized")
    void confirmDelivery_missingToken_unauthorized() {
        ConfirmDeliveryRequest req = new ConfirmDeliveryRequest("Missing token");

        given()
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/assignments/ORD-DEL-1/deliver")
                .then()
                .statusCode(401)
                .body("success", equalTo(false));
    }

    @Test
    @DisplayName("Delivery attempt on non-IN_TRANSIT order returns 400 Bad Request")
    void confirmDelivery_invalidState_badRequest() {
        ConfirmDeliveryRequest req = new ConfirmDeliveryRequest("Premature delivery attempt");

        // Order is still PICKED_UP (not yet in transit)
        given()
                .header("Authorization", driver1Token)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/assignments/ORD-DEL-PICKED/deliver")
                .then()
                .statusCode(400)
                .body("success", equalTo(false));

        // Order is ASSIGNED (not even picked up)
        given()
                .header("Authorization", driver1Token)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/assignments/ORD-DEL-ASSIGNED/deliver")
                .then()
                .statusCode(400)
                .body("success", equalTo(false));

        // Order is CANCELLED
        given()
                .header("Authorization", driver1Token)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/assignments/ORD-DEL-CANCELLED/deliver")
                .then()
                .statusCode(400)
                .body("success", equalTo(false));
    }

    @Test
    @DisplayName("Delivery attempt on non-existent order returns 404 Not Found")
    void confirmDelivery_nonExistentOrder_notFound() {
        ConfirmDeliveryRequest req = new ConfirmDeliveryRequest("Non-existent order delivery");

        given()
                .header("Authorization", driver1Token)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/assignments/NON-EXISTENT-ORDER/deliver")
                .then()
                .statusCode(404)
                .body("success", equalTo(false));
    }
}
