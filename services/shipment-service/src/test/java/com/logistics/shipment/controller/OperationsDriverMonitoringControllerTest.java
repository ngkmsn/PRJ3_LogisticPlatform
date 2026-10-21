package com.logistics.shipment.controller;

import com.logistics.common.model.DriverAvailability;
import com.logistics.common.security.JwtTokenProvider;
import com.logistics.common.security.Role;
import com.logistics.shipment.eligibility.client.DriverClient;
import com.logistics.shipment.eligibility.model.EligibleDriver;
import com.logistics.shipment.entity.Assignment;
import com.logistics.shipment.entity.DriverLocation;
import com.logistics.shipment.exception.DriverDataProviderUnavailableException;
import com.logistics.shipment.model.AssignmentStatus;
import com.logistics.shipment.repository.AssignmentRepository;
import com.logistics.shipment.repository.DriverLocationRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@QuarkusTest
class OperationsDriverMonitoringControllerTest {

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
    private String driverToken;
    private String customerToken;

    private final String driver1Id = "drv-ops-ctrl-01";
    private final String driver2Id = "drv-ops-ctrl-02";
    private final String driver3Id = "drv-ops-ctrl-03";

    @BeforeEach
    void setUp() {
        QuarkusTransaction.requiringNew().run(() -> {
            driverLocationRepository.deleteAll();
            assignmentRepository.deleteAll();
        });

        dispatcherToken = "Bearer " + jwtTokenProvider.generateToken(
                "disp-ctrl-01", "dispatcher", "disp@test.com", Role.DISPATCHER.name()
        );
        adminToken = "Bearer " + jwtTokenProvider.generateToken(
                "admin-ctrl-01", "admin", "admin@test.com", Role.ADMIN.name()
        );
        driverToken = "Bearer " + jwtTokenProvider.generateToken(
                "drv-ctrl-01", "driver", "drv@test.com", Role.DRIVER.name()
        );
        customerToken = "Bearer " + jwtTokenProvider.generateToken(
                "cust-ctrl-01", "customer", "cust@test.com", Role.CUSTOMER.name()
        );

        EligibleDriver d1 = new EligibleDriver(
                driver1Id, "user-ops-01", "Nguyen Van An", "0901234567",
                "MOTORBIKE", "29A-12345", DriverAvailability.AVAILABLE, "ACTIVE"
        );
        EligibleDriver d2 = new EligibleDriver(
                driver2Id, "user-ops-02", "Tran Thi Binh", "0902345678",
                "VAN", "29B-23456", DriverAvailability.AVAILABLE, "ACTIVE"
        );
        EligibleDriver d3 = new EligibleDriver(
                driver3Id, "user-ops-03", "Le Van Cuong", "0903456789",
                "TRUCK", "29C-34567", DriverAvailability.UNAVAILABLE, "ACTIVE"
        );
        when(driverClient.getAllDrivers(anyString())).thenReturn(List.of(d1, d2, d3));
        when(driverClient.findDriverById(driver1Id)).thenReturn(Optional.of(d1));
        when(driverClient.findDriverById(driver2Id)).thenReturn(Optional.of(d2));
        when(driverClient.findDriverById(driver3Id)).thenReturn(Optional.of(d3));
        when(driverClient.findDriverById("drv-unknown")).thenReturn(Optional.empty());
    }

    private void seedAssignmentsAndLocations() {
        QuarkusTransaction.requiringNew().run(() -> {
            // Driver 1: has active in-transit assignment and fresh GPS
            Assignment a1 = new Assignment(
                    UUID.randomUUID().toString(),
                    "ord-ops-101",
                    "ORD-OPS-101",
                    "cust-ctrl-01",
                    "Recipient One",
                    "0987654321",
                    "100 Tran Phu, Ha Dong",
                    BigDecimal.valueOf(150000),
                    AssignmentStatus.IN_TRANSIT,
                    driver1Id,
                    Instant.now().minus(20, ChronoUnit.MINUTES),
                    "Handle with care",
                    Instant.now(),
                    Instant.now()
            );
            assignmentRepository.persist(a1);

            DriverLocation loc1 = new DriverLocation(
                    UUID.randomUUID().toString(),
                    driver1Id,
                    "user-ops-01",
                    21.028511,
                    105.854444,
                    5.0,
                    28.0,
                    180.0,
                    "ord-ops-101",
                    Instant.now().minus(3, ChronoUnit.MINUTES),
                    Instant.now(),
                    Instant.now()
            );
            driverLocationRepository.persist(loc1);

            // Driver 2: has NO assignment, but has stale GPS (> 30 mins)
            DriverLocation loc2 = new DriverLocation(
                    UUID.randomUUID().toString(),
                    driver2Id,
                    "user-ops-02",
                    21.035000,
                    105.840000,
                    10.0,
                    0.0,
                    0.0,
                    null,
                    Instant.now().minus(40, ChronoUnit.MINUTES),
                    Instant.now(),
                    Instant.now()
            );
            driverLocationRepository.persist(loc2);

            // Driver 3: NO assignment, NO GPS
        });
    }

    @Test
    @DisplayName("GET /tracking/operations/drivers returns 200 OK with driver monitoring data for DISPATCHER")
    void getDriversMonitoring_dispatcher_success() {
        seedAssignmentsAndLocations();

        given()
                .header("Authorization", dispatcherToken)
                .contentType(ContentType.JSON)
                .when()
                .get("/tracking/operations/drivers")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.totalElements", equalTo(3))
                .body("data.content", hasSize(3))
                // Driver 1 assertions
                .body("data.content.find { it.driverId == '" + driver1Id + "' }.hasActiveOrder", equalTo(true))
                .body("data.content.find { it.driverId == '" + driver1Id + "' }.activeOrderId", equalTo("ord-ops-101"))
                .body("data.content.find { it.driverId == '" + driver1Id + "' }.activeOrderStatus", equalTo("IN_TRANSIT"))
                .body("data.content.find { it.driverId == '" + driver1Id + "' }.operationalStatus", equalTo("BUSY"))
                .body("data.content.find { it.driverId == '" + driver1Id + "' }.hasGps", equalTo(true))
                .body("data.content.find { it.driverId == '" + driver1Id + "' }.latitude", equalTo(21.028511F))
                .body("data.content.find { it.driverId == '" + driver1Id + "' }.locationStale", equalTo(false))
                // Driver 2 assertions (stale GPS, no order)
                .body("data.content.find { it.driverId == '" + driver2Id + "' }.hasActiveOrder", equalTo(false))
                .body("data.content.find { it.driverId == '" + driver2Id + "' }.operationalStatus", equalTo("AVAILABLE"))
                .body("data.content.find { it.driverId == '" + driver2Id + "' }.hasGps", equalTo(true))
                .body("data.content.find { it.driverId == '" + driver2Id + "' }.locationStale", equalTo(true))
                .body("data.content.find { it.driverId == '" + driver2Id + "' }.staleWarning", notNullValue())
                // Driver 3 assertions (no GPS, unavailable)
                .body("data.content.find { it.driverId == '" + driver3Id + "' }.hasActiveOrder", equalTo(false))
                .body("data.content.find { it.driverId == '" + driver3Id + "' }.hasGps", equalTo(false))
                .body("data.content.find { it.driverId == '" + driver3Id + "' }.latitude", nullValue())
                .body("data.content.find { it.driverId == '" + driver3Id + "' }.locationStale", equalTo(false));
    }

    @Test
    @DisplayName("GET /tracking/operations/drivers returns 200 OK for ADMIN")
    void getDriversMonitoring_admin_success() {
        seedAssignmentsAndLocations();

        given()
                .header("Authorization", adminToken)
                .contentType(ContentType.JSON)
                .when()
                .get("/tracking/operations/drivers")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.totalElements", equalTo(3));
    }

    @Test
    @DisplayName("GET /tracking/operations/drivers rejected with 403 Forbidden for DRIVER")
    void getDriversMonitoring_driver_forbidden() {
        given()
                .header("Authorization", driverToken)
                .contentType(ContentType.JSON)
                .when()
                .get("/tracking/operations/drivers")
                .then()
                .statusCode(403)
                .body("success", equalTo(false))
                .body("message", containsString("Operations (Dispatcher or Administrator) privileges required"));
    }

    @Test
    @DisplayName("GET /tracking/operations/drivers rejected with 403 Forbidden for CUSTOMER")
    void getDriversMonitoring_customer_forbidden() {
        given()
                .header("Authorization", customerToken)
                .contentType(ContentType.JSON)
                .when()
                .get("/tracking/operations/drivers")
                .then()
                .statusCode(403)
                .body("success", equalTo(false))
                .body("message", containsString("Operations (Dispatcher or Administrator) privileges required"));
    }

    @Test
    @DisplayName("GET /tracking/operations/drivers rejected with 401 Unauthorized when unauthenticated")
    void getDriversMonitoring_unauthenticated_unauthorized() {
        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/tracking/operations/drivers")
                .then()
                .statusCode(401)
                .body("success", equalTo(false));
    }

    @Test
    @DisplayName("GET /tracking/operations/drivers filtering by search query")
    void getDriversMonitoring_searchFilter() {
        seedAssignmentsAndLocations();

        given()
                .header("Authorization", dispatcherToken)
                .queryParam("search", "Binh")
                .contentType(ContentType.JSON)
                .when()
                .get("/tracking/operations/drivers")
                .then()
                .statusCode(200)
                .body("data.totalElements", equalTo(1))
                .body("data.content[0].fullName", equalTo("Tran Thi Binh"));
    }

    @Test
    @DisplayName("GET /tracking/operations/drivers filtering by status BUSY")
    void getDriversMonitoring_statusFilter() {
        seedAssignmentsAndLocations();

        given()
                .header("Authorization", dispatcherToken)
                .queryParam("status", "BUSY")
                .contentType(ContentType.JSON)
                .when()
                .get("/tracking/operations/drivers")
                .then()
                .statusCode(200)
                .body("data.totalElements", equalTo(1))
                .body("data.content[0].driverId", equalTo(driver1Id));
    }

    @Test
    @DisplayName("GET /tracking/operations/drivers filtering by hasGps=false")
    void getDriversMonitoring_hasGpsFilter() {
        seedAssignmentsAndLocations();

        given()
                .header("Authorization", dispatcherToken)
                .queryParam("hasGps", false)
                .contentType(ContentType.JSON)
                .when()
                .get("/tracking/operations/drivers")
                .then()
                .statusCode(200)
                .body("data.totalElements", equalTo(1))
                .body("data.content[0].driverId", equalTo(driver3Id));
    }

    @Test
    @DisplayName("GET /tracking/operations/drivers with pagination")
    void getDriversMonitoring_pagination() {
        seedAssignmentsAndLocations();

        given()
                .header("Authorization", dispatcherToken)
                .queryParam("page", 0)
                .queryParam("size", 2)
                .contentType(ContentType.JSON)
                .when()
                .get("/tracking/operations/drivers")
                .then()
                .statusCode(200)
                .body("data.page", equalTo(0))
                .body("data.size", equalTo(2))
                .body("data.totalElements", equalTo(3))
                .body("data.totalPages", equalTo(2))
                .body("data.content", hasSize(2));
    }

    @Test
    @DisplayName("GET /tracking/operations/drivers/{driverId} returns 200 OK for valid driver")
    void getDriverMonitoringDetails_success() {
        seedAssignmentsAndLocations();

        given()
                .header("Authorization", dispatcherToken)
                .contentType(ContentType.JSON)
                .when()
                .get("/tracking/operations/drivers/" + driver1Id)
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.driverId", equalTo(driver1Id))
                .body("data.fullName", equalTo("Nguyen Van An"))
                .body("data.hasActiveOrder", equalTo(true))
                .body("data.activeOrderId", equalTo("ord-ops-101"))
                .body("data.hasGps", equalTo(true))
                .body("data.latitude", equalTo(21.028511F));
    }

    @Test
    @DisplayName("GET /tracking/operations/drivers/{driverId} returns 404 NOT_FOUND for unknown driver")
    void getDriverMonitoringDetails_notFound() {
        given()
                .header("Authorization", dispatcherToken)
                .contentType(ContentType.JSON)
                .when()
                .get("/tracking/operations/drivers/drv-unknown")
                .then()
                .statusCode(404)
                .body("success", equalTo(false))
                .body("message", containsString("Driver not found with identifier: drv-unknown"));
    }

    @Test
    @DisplayName("When DriverClient service is unavailable, returns 503 SERVICE_UNAVAILABLE")
    void getDriversMonitoring_serviceUnavailable_returns503() {
        when(driverClient.getAllDrivers(anyString()))
                .thenThrow(new DriverDataProviderUnavailableException("User service connection failed"));

        given()
                .header("Authorization", dispatcherToken)
                .contentType(ContentType.JSON)
                .when()
                .get("/tracking/operations/drivers")
                .then()
                .statusCode(503)
                .body("success", equalTo(false))
                .body("message", containsString("Driver data provider is currently unavailable"));
    }

    @Test
    @DisplayName("Gateway aliases /shipments/tracking/operations/drivers and /api/tracking/operations/drivers work properly")
    void gatewayAliases_success() {
        seedAssignmentsAndLocations();

        // 1. Shipment tracking alias
        given()
                .header("Authorization", dispatcherToken)
                .contentType(ContentType.JSON)
                .when()
                .get("/shipments/tracking/operations/drivers")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.totalElements", equalTo(3));

        // 2. Api tracking alias
        given()
                .header("Authorization", dispatcherToken)
                .contentType(ContentType.JSON)
                .when()
                .get("/api/tracking/operations/drivers")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.totalElements", equalTo(3));
    }
}
