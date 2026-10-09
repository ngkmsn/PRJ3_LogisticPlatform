package com.logistics.user.controller;

import com.logistics.common.model.DriverAvailability;
import com.logistics.common.security.JwtTokenProvider;
import com.logistics.user.dto.CreateDriverProfileRequest;
import com.logistics.user.dto.CreateUserRequest;
import com.logistics.user.dto.UpdateDriverAvailabilityRequest;
import com.logistics.user.dto.UpdateDriverProfileRequest;
import com.logistics.user.entity.UserRole;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class DriverManagementIntegrationTest {

    @Inject
    JwtTokenProvider jwtTokenProvider;

    private String adminToken;
    private String driverToken;
    private String customerToken;

    @BeforeEach
    void setUp() {
        // Seeded admin user: 00000000-0000-0000-0000-000000000001
        adminToken = "Bearer " + jwtTokenProvider.generateToken(
                "00000000-0000-0000-0000-000000000001", "admin", "admin@logistics.com", "ADMIN"
        );

        // Seeded driver user: 00000000-0000-0000-0000-000000000003
        driverToken = "Bearer " + jwtTokenProvider.generateToken(
                "00000000-0000-0000-0000-000000000003", "driver", "driver@logistics.com", "DRIVER"
        );

        // Seeded customer user: 00000000-0000-0000-0000-000000000004
        customerToken = "Bearer " + jwtTokenProvider.generateToken(
                "00000000-0000-0000-0000-000000000004", "customer", "customer@logistics.com", "CUSTOMER"
        );
    }

    @Test
    @DisplayName("GET /drivers by Admin returns paged driver profiles list")
    void listDrivers_admin_success() {
        given()
                .header("Authorization", adminToken)
                .when()
                .get("/drivers")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.content.size()", greaterThanOrEqualTo(1))
                .body("data.totalElements", greaterThanOrEqualTo(1));
    }

    @Test
    @DisplayName("GET /drivers by Driver is rejected with 403 Forbidden")
    void listDrivers_driver_forbidden() {
        given()
                .header("Authorization", driverToken)
                .when()
                .get("/drivers")
                .then()
                .statusCode(403)
                .body("success", equalTo(false))
                .body("message", containsString("privileges required"));
    }

    @Test
    @DisplayName("GET /drivers unauthenticated is rejected with 401 Unauthorized")
    void listDrivers_unauthenticated_unauthorized() {
        given()
                .when()
                .get("/drivers")
                .then()
                .statusCode(401)
                .body("success", equalTo(false));
    }

    @Test
    @DisplayName("GET /drivers/me by Driver returns their own profile")
    void getMyDriverProfile_driver_success() {
        given()
                .header("Authorization", driverToken)
                .when()
                .get("/drivers/me")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.userId", equalTo("00000000-0000-0000-0000-000000000003"))
                .body("data.username", equalTo("driver"))
                .body("data.licenseNumber", equalTo("GPLX-79A-998877"));
    }

    @Test
    @DisplayName("GET /drivers/{id} by Admin returns driver profile details")
    void getDriverById_admin_success() {
        given()
                .header("Authorization", adminToken)
                .when()
                .get("/drivers/00000000-0000-0000-0001-000000000001")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.fullName", containsString("Nguyễn Văn Lái Xe"))
                .body("data.userRole", equalTo("DRIVER"));
    }

    @Test
    @DisplayName("POST /drivers creates driver profile linked to a valid user having DRIVER role")
    void createDriverProfile_lifecycle_success() {
        // 1. Create a new user with DRIVER role
        String uniqueDriver = "new_driver_" + System.currentTimeMillis();
        CreateUserRequest userReq = new CreateUserRequest(
                uniqueDriver,
                uniqueDriver + "@logistics.com",
                "Driver@123",
                UserRole.DRIVER
        );

        String newUserId = given()
                .header("Authorization", adminToken)
                .contentType(ContentType.JSON)
                .body(userReq)
                .when()
                .post("/users")
                .then()
                .statusCode(201)
                .extract()
                .path("data.userId");

        // 2. Admin creates driver profile for the newly created user
        String uniqueLicense = "GPLX-" + System.currentTimeMillis();
        CreateDriverProfileRequest profileReq = new CreateDriverProfileRequest(
                newUserId,
                "Bác Tài Mới",
                "0912345678",
                uniqueLicense,
                "D",
                "TRUCK_5T",
                "51D-999.88",
                "TP. Hồ Chí Minh",
                "ACTIVE"
        );

        String profileId = given()
                .header("Authorization", adminToken)
                .contentType(ContentType.JSON)
                .body(profileReq)
                .when()
                .post("/drivers")
                .then()
                .statusCode(201)
                .body("success", equalTo(true))
                .body("data.userId", equalTo(newUserId))
                .body("data.fullName", equalTo("Bác Tài Mới"))
                .body("data.licenseNumber", equalTo(uniqueLicense))
                .body("data.username", equalTo(uniqueDriver))
                .extract()
                .path("data.id");

        // 3. Verify driver can access their profile using their own token
        String newDriverToken = "Bearer " + jwtTokenProvider.generateToken(
                newUserId, uniqueDriver, uniqueDriver + "@logistics.com", "DRIVER"
        );

        given()
                .header("Authorization", newDriverToken)
                .when()
                .get("/drivers/me")
                .then()
                .statusCode(200)
                .body("data.id", equalTo(profileId))
                .body("data.vehiclePlate", equalTo("51D-999.88"));
    }

    @Test
    @DisplayName("POST /drivers duplicate driver profile for same user returns 409 Conflict")
    void createDriverProfile_duplicateUserId_conflict() {
        // Seeded driver already has a profile (id: 00000000-0000-0000-0000-000000000003)
        CreateDriverProfileRequest profileReq = new CreateDriverProfileRequest(
                "00000000-0000-0000-0000-000000000003",
                "Trùng Lặp Tài Xế",
                "0987654321",
                "GPLX-NEW-" + System.currentTimeMillis(),
                "C",
                "VAN",
                "29D-111.22",
                "Hà Nội",
                "ACTIVE"
        );

        given()
                .header("Authorization", adminToken)
                .contentType(ContentType.JSON)
                .body(profileReq)
                .when()
                .post("/drivers")
                .then()
                .statusCode(409)
                .body("success", equalTo(false))
                .body("message", containsString("already exists"));
    }

    @Test
    @DisplayName("POST /drivers for user without DRIVER role returns 400 Bad Request")
    void createDriverProfile_userNotDriver_badRequest() {
        // Customer user: 00000000-0000-0000-0000-000000000004
        CreateDriverProfileRequest profileReq = new CreateDriverProfileRequest(
                "00000000-0000-0000-0000-000000000004",
                "Khách Làm Tài Xế",
                "0987654321",
                "GPLX-CUST-" + System.currentTimeMillis(),
                "B2",
                "CAR",
                "30A-999.99",
                "Hà Nội",
                "ACTIVE"
        );

        given()
                .header("Authorization", adminToken)
                .contentType(ContentType.JSON)
                .body(profileReq)
                .when()
                .post("/drivers")
                .then()
                .statusCode(400)
                .body("success", equalTo(false))
                .body("message", containsString("does not have DRIVER role"));
    }

    @Test
    @DisplayName("PATCH /drivers/{id} allows Driver to update their own profile")
    void updateDriverProfile_selfDriver_success() {
        UpdateDriverProfileRequest updateReq = new UpdateDriverProfileRequest(
                "Nguyễn Văn Lái Xe (Đã Cập Nhật)",
                "0977889900",
                null,
                "C",
                "TRUCK_3T",
                "29C-999.00",
                "Hải Phòng, Việt Nam",
                null
        );

        given()
                .header("Authorization", driverToken)
                .contentType(ContentType.JSON)
                .body(updateReq)
                .when()
                .patch("/drivers/00000000-0000-0000-0001-000000000001")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.fullName", equalTo("Nguyễn Văn Lái Xe (Đã Cập Nhật)"))
                .body("data.phoneNumber", equalTo("0977889900"))
                .body("data.vehiclePlate", equalTo("29C-999.00"));
    }

    @Test
    @DisplayName("PATCH /drivers/{id} by Driver on another driver's profile is rejected with 403 Forbidden")
    void updateDriverProfile_otherDriver_forbidden() {
        // Create another driver
        String otherDriver = "other_driver_" + System.currentTimeMillis();
        String otherUserId = given()
                .header("Authorization", adminToken)
                .contentType(ContentType.JSON)
                .body(new CreateUserRequest(otherDriver, otherDriver + "@logistics.com", "Pass@123", UserRole.DRIVER))
                .when()
                .post("/users")
                .then()
                .statusCode(201)
                .extract()
                .path("data.userId");

        String otherProfileId = given()
                .header("Authorization", adminToken)
                .contentType(ContentType.JSON)
                .body(new CreateDriverProfileRequest(
                        otherUserId, "Tài Xế Khác", "0911223344", "GPLX-" + System.currentTimeMillis(),
                        "C", "VAN", "29B-123.45", "Hà Nội", "ACTIVE"
                ))
                .when()
                .post("/drivers")
                .then()
                .statusCode(201)
                .extract()
                .path("data.id");

        // Seeded driver tries to modify other driver's profile
        UpdateDriverProfileRequest updateReq = new UpdateDriverProfileRequest(
                "Hack Name", "0900000000", null, null, null, null, null, null
        );

        given()
                .header("Authorization", driverToken)
                .contentType(ContentType.JSON)
                .body(updateReq)
                .when()
                .patch("/drivers/" + otherProfileId)
                .then()
                .statusCode(403)
                .body("success", equalTo(false))
                .body("message", containsString("You can only update your own driver profile"));
    }

    @Test
    @DisplayName("CUSTOMER role cannot view or update driver profiles (403 Forbidden)")
    void customerRole_forbiddenAccess() {
        given()
                .header("Authorization", customerToken)
                .when()
                .get("/drivers/00000000-0000-0000-0001-000000000001")
                .then()
                .statusCode(403)
                .body("success", equalTo(false));

        given()
                .header("Authorization", customerToken)
                .contentType(ContentType.JSON)
                .body(new UpdateDriverProfileRequest("Hack", null, null, null, null, null, null, null))
                .when()
                .patch("/drivers/00000000-0000-0000-0001-000000000001")
                .then()
                .statusCode(403)
                .body("success", equalTo(false));
    }

    @Test
    @DisplayName("GET /drivers/me/availability returns current driver availability (default UNAVAILABLE)")
    void getMyAvailability_defaultUnavailable_success() {
        given()
                .header("Authorization", driverToken)
                .when()
                .get("/drivers/me/availability")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.userId", equalTo("00000000-0000-0000-0000-000000000003"))
                .body("data.availability", equalTo("UNAVAILABLE"));
    }

    @Test
    @DisplayName("PATCH /drivers/me/availability toggles AVAILABLE <-> UNAVAILABLE and persists in DB")
    void updateMyAvailability_toggle_success() {
        // 1. Driver switches to AVAILABLE
        given()
                .header("Authorization", driverToken)
                .contentType(ContentType.JSON)
                .body(new UpdateDriverAvailabilityRequest(DriverAvailability.AVAILABLE))
                .when()
                .patch("/drivers/me/availability")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.availability", equalTo("AVAILABLE"));

        // 2. Verify state is persisted when re-fetching
        given()
                .header("Authorization", driverToken)
                .when()
                .get("/drivers/me/availability")
                .then()
                .statusCode(200)
                .body("data.availability", equalTo("AVAILABLE"));

        // 3. Verify driver profile also reflects availability
        given()
                .header("Authorization", driverToken)
                .when()
                .get("/drivers/me")
                .then()
                .statusCode(200)
                .body("data.availability", equalTo("AVAILABLE"));

        // 4. Driver switches back to UNAVAILABLE
        given()
                .header("Authorization", driverToken)
                .contentType(ContentType.JSON)
                .body(new UpdateDriverAvailabilityRequest(DriverAvailability.UNAVAILABLE))
                .when()
                .patch("/drivers/me/availability")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.availability", equalTo("UNAVAILABLE"));

        // 5. Verify state is updated to UNAVAILABLE
        given()
                .header("Authorization", driverToken)
                .when()
                .get("/drivers/me/availability")
                .then()
                .statusCode(200)
                .body("data.availability", equalTo("UNAVAILABLE"));
    }

    @Test
    @DisplayName("PATCH /drivers/me/availability by Customer is rejected with 403 Forbidden")
    void updateMyAvailability_customerForbidden() {
        given()
                .header("Authorization", customerToken)
                .contentType(ContentType.JSON)
                .body(new UpdateDriverAvailabilityRequest(DriverAvailability.AVAILABLE))
                .when()
                .patch("/drivers/me/availability")
                .then()
                .statusCode(403)
                .body("success", equalTo(false))
                .body("message", containsString("Only drivers can update their availability"));
    }

    @Test
    @DisplayName("PATCH /drivers/me/availability unauthenticated is rejected with 401 Unauthorized")
    void updateMyAvailability_unauthenticated_rejected() {
        given()
                .contentType(ContentType.JSON)
                .body(new UpdateDriverAvailabilityRequest(DriverAvailability.AVAILABLE))
                .when()
                .patch("/drivers/me/availability")
                .then()
                .statusCode(401)
                .body("success", equalTo(false));
    }

    @Test
    @DisplayName("PATCH /drivers/me/availability with invalid body is rejected with 400 Bad Request")
    void updateMyAvailability_invalidBody_badRequest() {
        // Missing availability field
        given()
                .header("Authorization", driverToken)
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .patch("/drivers/me/availability")
                .then()
                .statusCode(400)
                .body("success", equalTo(false));

        // Invalid enum string
        given()
                .header("Authorization", driverToken)
                .contentType(ContentType.JSON)
                .body("{\"availability\": \"UNKNOWN_STATE\"}")
                .when()
                .patch("/drivers/me/availability")
                .then()
                .statusCode(400);
    }

    @Test
    @DisplayName("New driver profile created with POST /drivers defaults to UNAVAILABLE")
    void newDriverProfile_defaultAvailabilityUnavailable() {
        String uniqueDriver = "avail_driver_" + System.currentTimeMillis();
        CreateUserRequest userReq = new CreateUserRequest(
                uniqueDriver,
                uniqueDriver + "@logistics.com",
                "Driver@123",
                UserRole.DRIVER
        );

        String newUserId = given()
                .header("Authorization", adminToken)
                .contentType(ContentType.JSON)
                .body(userReq)
                .when()
                .post("/users")
                .then()
                .statusCode(201)
                .extract()
                .path("data.userId");

        String uniqueLicense = "GPLX-AVAIL-" + System.currentTimeMillis();
        CreateDriverProfileRequest profileReq = new CreateDriverProfileRequest(
                newUserId,
                "Tài Xế Mới Mặc Định",
                "0911223399",
                uniqueLicense,
                "B2",
                "VAN",
                "30F-123.99",
                "Hà Nội",
                "ACTIVE"
        );

        String profileId = given()
                .header("Authorization", adminToken)
                .contentType(ContentType.JSON)
                .body(profileReq)
                .when()
                .post("/drivers")
                .then()
                .statusCode(201)
                .body("data.availability", equalTo("UNAVAILABLE"))
                .extract()
                .path("data.id");

        // Verify with GET /drivers/{id}/availability
        given()
                .header("Authorization", adminToken)
                .when()
                .get("/drivers/" + profileId + "/availability")
                .then()
                .statusCode(200)
                .body("data.availability", equalTo("UNAVAILABLE"));
    }

    @Test
    @DisplayName("API prefix /api/drivers/me/availability routes correctly")
    void apiRoute_driverAvailability_success() {
        given()
                .header("Authorization", driverToken)
                .when()
                .get("/api/drivers/me/availability")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.availability", notNullValue());

        given()
                .header("Authorization", driverToken)
                .contentType(ContentType.JSON)
                .body(new UpdateDriverAvailabilityRequest(DriverAvailability.UNAVAILABLE))
                .when()
                .patch("/api/drivers/me/availability")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.availability", equalTo("UNAVAILABLE"));
    }

    @Test
    @DisplayName("GET /drivers/me/eligibility returns NOT eligible when driver is UNAVAILABLE")
    void getMyEligibility_unavailable_notEligible() {
        // Ensure driver is UNAVAILABLE
        given()
                .header("Authorization", driverToken)
                .contentType(ContentType.JSON)
                .body(new UpdateDriverAvailabilityRequest(DriverAvailability.UNAVAILABLE))
                .when()
                .patch("/drivers/me/availability")
                .then()
                .statusCode(200);

        given()
                .header("Authorization", driverToken)
                .when()
                .get("/drivers/me/eligibility")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.eligible", equalTo(false))
                .body("data.ineligibilityReasons", org.hamcrest.Matchers.hasItem("DRIVER_UNAVAILABLE"));
    }

    @Test
    @DisplayName("GET /drivers/me/eligibility returns ELIGIBLE when driver is AVAILABLE and meets all rules")
    void getMyEligibility_available_eligible() {
        // 1. Switch to AVAILABLE
        given()
                .header("Authorization", driverToken)
                .contentType(ContentType.JSON)
                .body(new UpdateDriverAvailabilityRequest(DriverAvailability.AVAILABLE))
                .when()
                .patch("/drivers/me/availability")
                .then()
                .statusCode(200);

        // 2. Check eligibility
        given()
                .header("Authorization", driverToken)
                .when()
                .get("/drivers/me/eligibility")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.eligible", equalTo(true))
                .body("data.ineligibilityReasons.size()", equalTo(0));

        // Cleanup: reset back to UNAVAILABLE
        given()
                .header("Authorization", driverToken)
                .contentType(ContentType.JSON)
                .body(new UpdateDriverAvailabilityRequest(DriverAvailability.UNAVAILABLE))
                .when()
                .patch("/drivers/me/availability")
                .then()
                .statusCode(200);
    }

    @Test
    @DisplayName("GET /drivers/{id}/eligibility by Admin evaluates target driver")
    void getDriverEligibility_admin_success() {
        given()
                .header("Authorization", adminToken)
                .when()
                .get("/drivers/00000000-0000-0000-0001-000000000001/eligibility")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.driverId", equalTo("00000000-0000-0000-0001-000000000001"))
                .body("data.evaluatedAt", notNullValue());
    }

    @Test
    @DisplayName("GET /drivers/me/eligibility by Customer is rejected with 403 Forbidden")
    void getMyEligibility_customerForbidden() {
        given()
                .header("Authorization", customerToken)
                .when()
                .get("/drivers/me/eligibility")
                .then()
                .statusCode(403)
                .body("success", equalTo(false));
    }

    @Test
    @DisplayName("GET /drivers/me/eligibility unauthenticated is rejected with 401 Unauthorized")
    void getMyEligibility_unauthenticated_rejected() {
        given()
                .when()
                .get("/drivers/me/eligibility")
                .then()
                .statusCode(401)
                .body("success", equalTo(false));
    }

    @Test
    @DisplayName("GET /api/drivers/me/eligibility routes correctly via Gateway prefix")
    void apiRoute_driverEligibility_success() {
        given()
                .header("Authorization", driverToken)
                .when()
                .get("/api/drivers/me/eligibility")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.evaluatedAt", notNullValue());
    }
}
