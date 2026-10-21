package com.logistics.shipment.controller;

import com.logistics.common.security.JwtTokenProvider;
import com.logistics.common.security.Role;
import com.logistics.shipment.dto.UpdateDriverLocationRequest;
import com.logistics.shipment.repository.DriverLocationRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class DriverGpsTrackingControllerTest {

    @Inject
    JwtTokenProvider jwtTokenProvider;

    @Inject
    DriverLocationRepository driverLocationRepository;

    private String driverToken;
    private String customerToken;
    private String adminToken;

    private final String driverUserId = "drv-ctrl-test-01";

    @BeforeEach
    @Transactional
    void setUp() {
        driverLocationRepository.deleteAll();

        driverToken = "Bearer " + jwtTokenProvider.generateToken(
                driverUserId, "driver_ctrl_test", "driver_ctrl@test.com", Role.DRIVER.name()
        );
        customerToken = "Bearer " + jwtTokenProvider.generateToken(
                "cust-ctrl-test-01", "customer_ctrl", "cust@test.com", Role.CUSTOMER.name()
        );
        adminToken = "Bearer " + jwtTokenProvider.generateToken(
                "admin-ctrl-test-01", "admin_ctrl", "admin@test.com", Role.ADMIN.name()
        );
    }

    @Test
    @DisplayName("POST /tracking/location returns 200 OK with recorded location metadata")
    void updateLocation_success() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        UpdateDriverLocationRequest req = new UpdateDriverLocationRequest(
                21.028511, 105.854444, now, 4.2, 28.5, 90.0, "ORD-999"
        );

        given()
                .header("Authorization", driverToken)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/tracking/location")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.driverId", equalTo(driverUserId))
                .body("data.latitude", equalTo(21.028511F))
                .body("data.longitude", equalTo(105.854444F))
                .body("data.orderId", equalTo("ORD-999"))
                .body("data.timestamp", notNullValue());
    }

    @Test
    @DisplayName("POST /shipments/tracking/location returns 200 OK via /shipments alias")
    void updateLocation_shipmentsAlias_success() {
        UpdateDriverLocationRequest req = new UpdateDriverLocationRequest(21.020000, 105.840000);

        given()
                .header("Authorization", driverToken)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/shipments/tracking/location")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.latitude", equalTo(21.02F))
                .body("data.longitude", equalTo(105.84F));
    }

    @Test
    @DisplayName("POST /api/tracking/location returns 200 OK via gateway alias")
    void updateLocation_apiAlias_success() {
        UpdateDriverLocationRequest req = new UpdateDriverLocationRequest(21.030000, 105.850000);

        given()
                .header("Authorization", driverToken)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/api/tracking/location")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.latitude", equalTo(21.03F));
    }

    @Test
    @DisplayName("GET /tracking/location returns current driver's recorded location")
    void getCurrentLocation_success() {
        UpdateDriverLocationRequest req = new UpdateDriverLocationRequest(21.028511, 105.854444);

        // Record location first
        given()
                .header("Authorization", driverToken)
                .contentType(ContentType.JSON)
                .body(req)
                .post("/tracking/location");

        // Fetch location
        given()
                .header("Authorization", driverToken)
                .when()
                .get("/tracking/location")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.driverId", equalTo(driverUserId))
                .body("data.latitude", equalTo(21.028511F));
    }

    @Test
    @DisplayName("GET /tracking/drivers/{driverId}/location returns target driver's location for Admin")
    void getDriverLocation_success() {
        UpdateDriverLocationRequest req = new UpdateDriverLocationRequest(21.028511, 105.854444);

        given()
                .header("Authorization", driverToken)
                .contentType(ContentType.JSON)
                .body(req)
                .post("/tracking/location");

        given()
                .header("Authorization", adminToken)
                .when()
                .get("/tracking/drivers/" + driverUserId + "/location")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.driverId", equalTo(driverUserId));
    }

    @Test
    @DisplayName("POST /tracking/location returns 400 Bad Request when latitude is invalid")
    void updateLocation_invalidLatitude_returnsBadRequest() {
        UpdateDriverLocationRequest req = new UpdateDriverLocationRequest(95.0, 105.854444);

        given()
                .header("Authorization", driverToken)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/tracking/location")
                .then()
                .statusCode(400)
                .body("success", equalTo(false))
                .body("message", containsString("Latitude must be between -90.0 and 90.0"));
    }

    @Test
    @DisplayName("POST /tracking/location returns 400 Bad Request when incoming timestamp is stale")
    void updateLocation_staleTimestamp_returnsBadRequest() {
        Instant t2 = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        UpdateDriverLocationRequest req1 = new UpdateDriverLocationRequest(21.028511, 105.854444, t2);

        given()
                .header("Authorization", driverToken)
                .contentType(ContentType.JSON)
                .body(req1)
                .post("/tracking/location");

        // Older timestamp
        Instant t1 = t2.minus(10, ChronoUnit.MINUTES);
        UpdateDriverLocationRequest reqStale = new UpdateDriverLocationRequest(21.010000, 105.800000, t1);

        given()
                .header("Authorization", driverToken)
                .contentType(ContentType.JSON)
                .body(reqStale)
                .when()
                .post("/tracking/location")
                .then()
                .statusCode(400)
                .body("success", equalTo(false))
                .body("message", containsString("Stale GPS location"));
    }

    @Test
    @DisplayName("POST /tracking/location returns 401 Unauthorized when missing Authorization header")
    void updateLocation_missingToken_returnsUnauthorized() {
        UpdateDriverLocationRequest req = new UpdateDriverLocationRequest(21.028511, 105.854444);

        given()
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/tracking/location")
                .then()
                .statusCode(401)
                .body("success", equalTo(false))
                .body("message", containsString("Missing or invalid Authorization header"));
    }

    @Test
    @DisplayName("POST /tracking/location returns 403 Forbidden when caller is CUSTOMER")
    void updateLocation_customerRole_returnsForbidden() {
        UpdateDriverLocationRequest req = new UpdateDriverLocationRequest(21.028511, 105.854444);

        given()
                .header("Authorization", customerToken)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/tracking/location")
                .then()
                .statusCode(403)
                .body("success", equalTo(false))
                .body("message", containsString("Access denied"));
    }
}
