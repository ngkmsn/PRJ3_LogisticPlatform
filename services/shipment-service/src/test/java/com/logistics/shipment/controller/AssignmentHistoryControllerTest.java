package com.logistics.shipment.controller;

import com.logistics.common.security.JwtTokenProvider;
import com.logistics.common.security.Role;
import com.logistics.shipment.entity.Assignment;
import com.logistics.shipment.entity.AssignmentHistory;
import com.logistics.shipment.model.AssignmentActionType;
import com.logistics.shipment.model.AssignmentStatus;
import com.logistics.shipment.repository.AssignmentHistoryRepository;
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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

@QuarkusTest
class AssignmentHistoryControllerTest {

    @Inject
    JwtTokenProvider jwtTokenProvider;

    @Inject
    AssignmentRepository assignmentRepository;

    @Inject
    AssignmentHistoryRepository assignmentHistoryRepository;

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

        assignmentHistoryRepository.deleteAll();
        assignmentRepository.deleteAll();

        // Seed an assignment
        String orderId = "ORD-CTRL-HIST-1";
        Assignment a = new Assignment();
        a.setId(UUID.randomUUID().toString());
        a.setOrderId(orderId);
        a.setOrderNumber("ORD-" + orderId);
        a.setCustomerId("cust-1");
        a.setRecipientName("Recipient 1");
        a.setRecipientPhone("0901234567");
        a.setDeliveryAddress("123 Delivery St");
        a.setTotalAmount(new BigDecimal("180000.00"));
        a.setStatus(AssignmentStatus.ASSIGNED);
        a.setDriverId("DRV-2");
        a.setCreatedAt(Instant.now());
        a.setUpdatedAt(Instant.now());
        assignmentRepository.persist(a);

        // Seed two history entries
        AssignmentHistory h1 = new AssignmentHistory(orderId, a.getId(), null, "DRV-1",
                AssignmentActionType.AUTO_ASSIGN, "Initial auto assignment", "SYSTEM");
        assignmentHistoryRepository.persist(h1);

        AssignmentHistory h2 = new AssignmentHistory(orderId, a.getId(), "DRV-1", "DRV-2",
                AssignmentActionType.REASSIGN, "Driver 1 delayed", "dispatcher1");
        assignmentHistoryRepository.persist(h2);
    }

    @Test
    @DisplayName("GET /assignments/{orderId}/history returns 200 OK with list of history entries")
    void getHistory_success_asDispatcher() {
        given()
                .header("Authorization", dispatcherToken)
                .when()
                .get("/assignments/ORD-CTRL-HIST-1/history")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("success", equalTo(true))
                .body("data", hasSize(2))
                .body("data[0].actionType", equalTo("AUTO_ASSIGN"))
                .body("data[0].newDriverId", equalTo("DRV-1"))
                .body("data[0].performedBy", equalTo("SYSTEM"))
                .body("data[1].actionType", equalTo("REASSIGN"))
                .body("data[1].previousDriverId", equalTo("DRV-1"))
                .body("data[1].newDriverId", equalTo("DRV-2"))
                .body("data[1].performedBy", equalTo("dispatcher1"));
    }

    @Test
    @DisplayName("GET /api/assignments/{orderId}/history returns 200 OK when accessed via /api prefix")
    void getHistory_success_apiPrefix() {
        given()
                .header("Authorization", adminToken)
                .when()
                .get("/api/assignments/ORD-CTRL-HIST-1/history")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("success", equalTo(true))
                .body("data", hasSize(2));
    }

    @Test
    @DisplayName("GET /assignments/{orderId}/history returns 404 Not Found when order does not exist")
    void getHistory_notFound_whenOrderMissing() {
        given()
                .header("Authorization", dispatcherToken)
                .when()
                .get("/assignments/NON-EXISTENT-ORDER/history")
                .then()
                .statusCode(404)
                .body("success", equalTo(false))
                .body("message", containsString("No assignment record found"));
    }

    @Test
    @DisplayName("GET /assignments/{orderId}/history returns 401 Unauthorized when missing token")
    void getHistory_unauthorized_whenMissingToken() {
        given()
                .when()
                .get("/assignments/ORD-CTRL-HIST-1/history")
                .then()
                .statusCode(401)
                .body("success", equalTo(false))
                .body("message", containsString("Missing or invalid Authorization header"));
    }

    @Test
    @DisplayName("GET /assignments/{orderId}/history returns 403 Forbidden when caller is CUSTOMER or DRIVER")
    void getHistory_forbidden_whenUnauthorizedRole() {
        given()
                .header("Authorization", customerToken)
                .when()
                .get("/assignments/ORD-CTRL-HIST-1/history")
                .then()
                .statusCode(403)
                .body("success", equalTo(false))
                .body("message", containsString("Access denied"));

        given()
                .header("Authorization", driverToken)
                .when()
                .get("/assignments/ORD-CTRL-HIST-1/history")
                .then()
                .statusCode(403)
                .body("success", equalTo(false))
                .body("message", containsString("Access denied"));
    }
}
