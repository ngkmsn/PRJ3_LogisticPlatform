package com.logistics.shipment.controller;

import com.logistics.common.dto.MapCoordinatesDto;
import com.logistics.common.dto.RouteDirectionRequest;
import com.logistics.shipment.entity.Assignment;
import com.logistics.shipment.entity.DriverLocation;
import com.logistics.shipment.model.AssignmentStatus;
import com.logistics.shipment.repository.AssignmentRepository;
import com.logistics.shipment.repository.DriverLocationRepository;
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
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class MapControllerIntegrationTest {

    @Inject
    AssignmentRepository assignmentRepository;

    @Inject
    DriverLocationRepository driverLocationRepository;

    private String testOrderId;
    private String testDriverId;

    @BeforeEach
    @Transactional
    void setupTestData() {
        testOrderId = "ORD-MAP-TEST-" + UUID.randomUUID().toString().substring(0, 8);
        testDriverId = "drv-map-" + UUID.randomUUID().toString().substring(0, 8);

        Assignment assignment = new Assignment();
        assignment.setId(UUID.randomUUID().toString());
        assignment.setOrderId(testOrderId);
        assignment.setOrderNumber("NUM-" + testOrderId);
        assignment.setCustomerId("cust-01");
        assignment.setRecipientName("Tran Van B");
        assignment.setRecipientPhone("0912345678");
        assignment.setDeliveryAddress("123 Nguyễn Trãi, Quận 1, TP. Hồ Chí Minh");
        assignment.setTotalAmount(BigDecimal.valueOf(150000));
        assignment.setStatus(AssignmentStatus.IN_TRANSIT);
        assignment.setDriverId(testDriverId);
        assignment.setCreatedAt(Instant.now());
        assignment.setAssignedAt(Instant.now());
        assignmentRepository.persist(assignment);

        DriverLocation loc = new DriverLocation();
        loc.setId(UUID.randomUUID().toString());
        loc.setDriverId(testDriverId);
        loc.setUserId("usr-drv-01");
        loc.setLatitude(10.7769);
        loc.setLongitude(106.7009);
        loc.setTimestamp(Instant.now());
        loc.setCreatedAt(Instant.now());
        loc.setUpdatedAt(Instant.now());
        driverLocationRepository.persist(loc);
    }

    @Test
    @DisplayName("POST /tracking/map/directions: Successfully calculate directions")
    void testPostDirectionsSuccess() {
        RouteDirectionRequest request = new RouteDirectionRequest(
                MapCoordinatesDto.of(10.7769, 106.7009), // HCMC center
                MapCoordinatesDto.of(10.7540, 106.6660), // District 5
                "MOTORCYCLE"
        );

        given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/tracking/map/directions")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.success", equalTo(true))
                .body("data.distanceKm", greaterThan(0.0f))
                .body("data.durationMinutes", greaterThan(0))
                .body("data.geometry", notNullValue())
                .body("data.steps", hasSize(greaterThan(0)))
                .body("data.provider", equalTo("MOCK"));
    }

    @Test
    @DisplayName("GET /tracking/map/directions: Query param route calculation")
    void testGetDirectionsQueryParam() {
        given()
                .queryParam("originLat", 21.0285)
                .queryParam("originLon", 105.8542)
                .queryParam("destLat", 21.0362)
                .queryParam("destLon", 105.7905)
                .queryParam("vehicleMode", "CAR")
                .when()
                .get("/tracking/map/directions")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.distanceKm", greaterThan(0.0f))
                .body("data.durationMinutes", greaterThan(0));
    }

    @Test
    @DisplayName("POST /tracking/map/directions: Invalid coordinates return 400 Bad Request")
    void testPostDirectionsInvalidCoordinates() {
        RouteDirectionRequest invalidLat = new RouteDirectionRequest(
                MapCoordinatesDto.of(99.0, 106.7009), // Lat > 90
                MapCoordinatesDto.of(10.7540, 106.6660)
        );

        given()
                .contentType(ContentType.JSON)
                .body(invalidLat)
                .when()
                .post("/tracking/map/directions")
                .then()
                .statusCode(400)
                .body("success", equalTo(false))
                .body("message", containsString("Invalid origin coordinates"));
    }

    @Test
    @DisplayName("GET /tracking/map/geocode: Geocode valid address returns coordinates")
    void testGeocodeSuccess() {
        given()
                .queryParam("address", "Hoàn Kiếm, Hà Nội")
                .when()
                .get("/tracking/map/geocode")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.success", equalTo(true))
                .body("data.latitude", equalTo(21.0288f))
                .body("data.longitude", equalTo(105.8525f));
    }

    @Test
    @DisplayName("GET /tracking/map/geocode: Empty address returns 400 Bad Request")
    void testGeocodeEmptyAddress() {
        given()
                .queryParam("address", "   ")
                .when()
                .get("/tracking/map/geocode")
                .then()
                .statusCode(400)
                .body("success", equalTo(false));
    }

    @Test
    @DisplayName("GET /tracking/map/reverse-geocode: Reverse geocode valid coordinates")
    void testReverseGeocodeSuccess() {
        given()
                .queryParam("lat", 10.7769)
                .queryParam("lon", 106.7009)
                .when()
                .get("/tracking/map/reverse-geocode")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.formattedAddress", containsString("Vietnam"));
    }

    @Test
    @DisplayName("GET /tracking/orders/{orderId}/directions: Order direction returns route from driver GPS to dest")
    void testOrderDirections() {
        given()
                .when()
                .get("/tracking/orders/" + testOrderId + "/directions")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.distanceKm", greaterThan(0.0f))
                .body("data.steps", hasSize(greaterThan(0)));
    }

    @Test
    @DisplayName("GET /api/tracking/map/directions: API prefix alias works identically")
    void testApiPrefixAlias() {
        given()
                .queryParam("originLat", 10.7769)
                .queryParam("originLon", 106.7009)
                .queryParam("destLat", 10.7540)
                .queryParam("destLon", 106.6660)
                .when()
                .get("/api/tracking/map/directions")
                .then()
                .statusCode(200)
                .body("success", equalTo(true));
    }
}
