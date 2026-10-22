package com.logistics.shipment.controller;

import com.logistics.common.dto.DriverOperationalMetricsDto;
import com.logistics.common.dto.OrderMetricsDto;
import com.logistics.common.security.JwtTokenProvider;
import com.logistics.shipment.dashboard.client.OrderMetricsClient;
import com.logistics.shipment.eligibility.client.DriverClient;
import com.logistics.shipment.repository.AssignmentRepository;
import com.logistics.shipment.repository.DriverLocationRepository;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@QuarkusTest
class OperationsDashboardControllerTest {

    @InjectMock
    OrderMetricsClient orderMetricsClient;

    @InjectMock
    DriverClient driverClient;

    @Inject
    AssignmentRepository assignmentRepository;

    @Inject
    DriverLocationRepository driverLocationRepository;

    @Inject
    JwtTokenProvider jwtTokenProvider;

    private String dispatcherToken;
    private String adminToken;
    private String customerToken;
    private String driverToken;

    @BeforeEach
    void setUp() {
        dispatcherToken = "Bearer " + jwtTokenProvider.generateToken("disp-01", "disp1", "disp@test.com", "DISPATCHER");
        adminToken = "Bearer " + jwtTokenProvider.generateToken("admin-01", "admin1", "admin@test.com", "ADMIN");
        customerToken = "Bearer " + jwtTokenProvider.generateToken("cust-01", "cust1", "cust@test.com", "CUSTOMER");
        driverToken = "Bearer " + jwtTokenProvider.generateToken("drv-01", "drv1", "drv@test.com", "DRIVER");

        OrderMetricsDto orderDto = new OrderMetricsDto();
        orderDto.setTotalOrders(100L);
        orderDto.setPendingOrders(20L);
        orderDto.setAssignedOrders(30L);
        orderDto.setDeliveredOrders(50L);

        DriverOperationalMetricsDto driverDto = new DriverOperationalMetricsDto();
        driverDto.setTotalDrivers(15L);
        driverDto.setAvailableDrivers(10L);
        driverDto.setUnavailableDrivers(5L);

        when(orderMetricsClient.getOrderMetrics(anyString())).thenReturn(Optional.of(orderDto));
        when(driverClient.getDriverMetrics(anyString())).thenReturn(Optional.of(driverDto));
    }

    @Test
    @DisplayName("PB-048: Dispatcher can fetch operations dashboard via /shipments/operations/dashboard")
    void getDashboard_dispatcherSuccess() {
        given()
                .header("Authorization", dispatcherToken)
                .contentType(ContentType.JSON)
                .when()
                .get("/shipments/operations/dashboard")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.orderMetrics.totalOrders", equalTo(100))
                .body("data.driverMetrics.totalDrivers", equalTo(15))
                .body("data.attentionItems", notNullValue());
    }

    @Test
    @DisplayName("PB-048: Dispatcher can fetch operations dashboard via /api/shipments/operations/dashboard with refresh flag")
    void getDashboard_apiEndpointWithRefresh() {
        given()
                .header("Authorization", dispatcherToken)
                .queryParam("refresh", "true")
                .contentType(ContentType.JSON)
                .when()
                .get("/api/shipments/operations/dashboard")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.orderMetrics", notNullValue());
    }

    @Test
    @DisplayName("PB-048: Dispatcher can fetch operations dashboard via /api/tracking/operations/dashboard alias")
    void getDashboard_trackingAlias() {
        given()
                .header("Authorization", dispatcherToken)
                .contentType(ContentType.JSON)
                .when()
                .get("/api/tracking/operations/dashboard")
                .then()
                .statusCode(200)
                .body("success", equalTo(true));
    }

    @Test
    @DisplayName("PB-048: Customer role is denied access (403 Forbidden)")
    void getDashboard_customerForbidden() {
        given()
                .header("Authorization", customerToken)
                .contentType(ContentType.JSON)
                .when()
                .get("/api/shipments/operations/dashboard")
                .then()
                .statusCode(403);
    }

    @Test
    @DisplayName("PB-048: Driver role is denied access (403 Forbidden)")
    void getDashboard_driverForbidden() {
        given()
                .header("Authorization", driverToken)
                .contentType(ContentType.JSON)
                .when()
                .get("/api/shipments/operations/dashboard")
                .then()
                .statusCode(403);
    }

    @Test
    @DisplayName("PB-048: Unauthenticated request is rejected (401 Unauthorized)")
    void getDashboard_unauthenticated() {
        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/api/shipments/operations/dashboard")
                .then()
                .statusCode(401);
    }
}
