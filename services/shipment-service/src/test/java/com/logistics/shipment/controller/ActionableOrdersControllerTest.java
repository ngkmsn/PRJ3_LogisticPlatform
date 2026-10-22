package com.logistics.shipment.controller;

import com.logistics.common.security.JwtTokenProvider;
import com.logistics.shipment.entity.Assignment;
import com.logistics.shipment.model.AssignmentStatus;
import com.logistics.shipment.repository.AssignmentRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class ActionableOrdersControllerTest {

    @Inject
    AssignmentRepository assignmentRepository;

    @Inject
    JwtTokenProvider jwtTokenProvider;

    private String dispatcherToken;
    private String adminToken;
    private String customerToken;
    private String driverToken;

    @BeforeEach
    @jakarta.transaction.Transactional
    void setUp() {
        assignmentRepository.deleteAll();

        dispatcherToken = "Bearer " + jwtTokenProvider.generateToken("disp-ctrl-01", "disp1", "disp@test.com", "DISPATCHER");
        adminToken = "Bearer " + jwtTokenProvider.generateToken("admin-ctrl-01", "admin1", "admin@test.com", "ADMIN");
        customerToken = "Bearer " + jwtTokenProvider.generateToken("cust-ctrl-01", "cust1", "cust@test.com", "CUSTOMER");
        driverToken = "Bearer " + jwtTokenProvider.generateToken("drv-ctrl-01", "drv1", "drv@test.com", "DRIVER");

        // Seed 1 UNASSIGNED order
        Assignment u = new Assignment();
        u.setId(UUID.randomUUID().toString());
        u.setOrderId("ord-ctrl-unassigned-1");
        u.setOrderNumber("ORD-CTRL-U1");
        u.setCustomerId("cust-ctrl-1");
        u.setRecipientName("Tran Van A");
        u.setDeliveryAddress("100 Le Loi, District 1, HCMC");
        u.setTotalAmount(new BigDecimal("120000.00"));
        u.setStatus(AssignmentStatus.UNASSIGNED);
        u.setUnassignedReason("NO_ELIGIBLE_DRIVER");
        u.setUnassignedAt(Instant.now());
        u.setCreatedAt(Instant.now());
        u.setUpdatedAt(Instant.now());
        assignmentRepository.persist(u);

        // Seed 1 FAILED order
        Assignment f = new Assignment();
        f.setId(UUID.randomUUID().toString());
        f.setOrderId("ord-ctrl-failed-1");
        f.setOrderNumber("ORD-CTRL-F1");
        f.setCustomerId("cust-ctrl-2");
        f.setRecipientName("Le Thi B");
        f.setDeliveryAddress("200 Hai Ba Trung, District 3, HCMC");
        f.setTotalAmount(new BigDecimal("180000.00"));
        f.setStatus(AssignmentStatus.FAILED);
        f.setDriverId("drv-prev-99");
        f.setFailureReason("Customer phone unreachable");
        f.setFailedAt(Instant.now());
        f.setCreatedAt(Instant.now());
        f.setUpdatedAt(Instant.now());
        assignmentRepository.persist(f);
    }

    @Test
    @DisplayName("GET /assignments/actionable returns 200 OK for DISPATCHER with aggregated actionable orders")
    void getActionableOrders_dispatcher_returns200() {
        given()
                .header("Authorization", dispatcherToken)
                .contentType(ContentType.JSON)
                .when()
                .get("/assignments/actionable")
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.totalElements", equalTo(2))
                .body("data.totalActionable", equalTo(2))
                .body("data.unassignedCount", equalTo(1))
                .body("data.failedCount", equalTo(1))
                .body("data.content", hasSize(2));
    }

    @Test
    @DisplayName("GET /operations/actionable-orders returns 200 OK for ADMIN with category filter")
    void getActionableOrders_adminCategoryFilter_returns200() {
        given()
                .header("Authorization", adminToken)
                .queryParam("category", "FAILED")
                .contentType(ContentType.JSON)
                .when()
                .get("/operations/actionable-orders")
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.content", hasSize(1))
                .body("data.content[0].orderId", equalTo("ord-ctrl-failed-1"))
                .body("data.content[0].issueSeverity", equalTo("CRITICAL"))
                .body("data.content[0].allowedActions", contains("REASSIGN"));
    }

    @Test
    @DisplayName("GET /api/shipments/assignments/actionable alias returns 200 OK with keyword search")
    void getActionableOrders_searchKeyword_returns200() {
        given()
                .header("Authorization", dispatcherToken)
                .queryParam("search", "Hai Ba Trung")
                .contentType(ContentType.JSON)
                .when()
                .get("/api/shipments/assignments/actionable")
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.content", hasSize(1))
                .body("data.content[0].orderId", equalTo("ord-ctrl-failed-1"));
    }

    @Test
    @DisplayName("GET /assignments/actionable/{orderId} returns 200 OK with actionable order detail")
    void getActionableOrderDetail_returns200() {
        given()
                .header("Authorization", dispatcherToken)
                .contentType(ContentType.JSON)
                .when()
                .get("/assignments/actionable/ord-ctrl-unassigned-1")
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.orderId", equalTo("ord-ctrl-unassigned-1"))
                .body("data.status", equalTo("UNASSIGNED"))
                .body("data.allowedActions", contains("ASSIGN"));
    }

    @Test
    @DisplayName("GET /assignments/actionable returns 403 Forbidden for CUSTOMER and DRIVER")
    void getActionableOrders_forbiddenRoles_returns403() {
        given()
                .header("Authorization", customerToken)
                .contentType(ContentType.JSON)
                .when()
                .get("/assignments/actionable")
                .then()
                .statusCode(403);

        given()
                .header("Authorization", driverToken)
                .contentType(ContentType.JSON)
                .when()
                .get("/assignments/actionable")
                .then()
                .statusCode(403);
    }

    @Test
    @DisplayName("GET /assignments/actionable returns 401 Unauthorized for missing token")
    void getActionableOrders_unauthenticated_returns401() {
        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/assignments/actionable")
                .then()
                .statusCode(401);
    }
}
