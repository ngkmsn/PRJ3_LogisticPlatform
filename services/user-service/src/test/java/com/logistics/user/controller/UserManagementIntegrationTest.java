package com.logistics.user.controller;

import com.logistics.common.security.JwtTokenProvider;
import com.logistics.user.dto.CreateUserRequest;
import com.logistics.user.dto.LoginRequest;
import com.logistics.user.dto.UpdateUserRequest;
import com.logistics.user.dto.UpdateUserStatusRequest;
import com.logistics.user.entity.UserRole;
import com.logistics.user.entity.UserStatus;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
class UserManagementIntegrationTest {

    @Inject
    JwtTokenProvider jwtTokenProvider;

    private String adminToken;
    private String driverToken;

    @BeforeEach
    void setUp() {
        // Token for seeded admin user (id: 00000000-0000-0000-0000-000000000001)
        adminToken = "Bearer " + jwtTokenProvider.generateToken(
                "00000000-0000-0000-0000-000000000001",
                "admin",
                "admin@logistics.com",
                "ADMIN"
        );

        // Token for seeded driver user (id: 00000000-0000-0000-0000-000000000003)
        driverToken = "Bearer " + jwtTokenProvider.generateToken(
                "00000000-0000-0000-0000-000000000003",
                "driver",
                "driver@logistics.com",
                "DRIVER"
        );
    }

    @Test
    @DisplayName("GET /users by Admin returns paged user list successfully")
    void listUsers_admin_success() {
        given()
                .header("Authorization", adminToken)
                .queryParam("page", 0)
                .queryParam("size", 10)
                .when()
                .get("/users")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.content.size()", greaterThanOrEqualTo(4))
                .body("data.totalElements", greaterThanOrEqualTo(4));
    }

    @Test
    @DisplayName("GET /users by non-Admin is rejected with 403 Forbidden")
    void listUsers_nonAdmin_forbidden() {
        given()
                .header("Authorization", driverToken)
                .when()
                .get("/users")
                .then()
                .statusCode(403)
                .body("success", equalTo(false))
                .body("message", containsString("Administrator privileges required"));
    }

    @Test
    @DisplayName("GET /users unauthenticated is rejected with 401 Unauthorized")
    void listUsers_unauthenticated_unauthorized() {
        given()
                .when()
                .get("/users")
                .then()
                .statusCode(401)
                .body("success", equalTo(false))
                .body("message", containsString("Missing or invalid"));
    }

    @Test
    @DisplayName("GET /users/{id} returns details of user without password hash")
    void getUserById_success() {
        given()
                .header("Authorization", adminToken)
                .when()
                .get("/users/00000000-0000-0000-0000-000000000002") // dispatcher
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.userId", equalTo("00000000-0000-0000-0000-000000000002"))
                .body("data.username", equalTo("dispatcher"))
                .body("data.role", equalTo("DISPATCHER"))
                .body("data.password", nullValue())
                .body("data.passwordHash", nullValue());
    }

    @Test
    @DisplayName("POST /users creates new user and allows login with new credentials")
    void createUser_and_login_success() {
        String uniqueUser = "op_" + System.currentTimeMillis();
        CreateUserRequest req = new CreateUserRequest(
                uniqueUser,
                uniqueUser + "@logistics.com",
                "Operator@123",
                UserRole.DISPATCHER
        );

        String newUserId = given()
                .header("Authorization", adminToken)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/users")
                .then()
                .statusCode(201)
                .body("success", equalTo(true))
                .body("data.username", equalTo(uniqueUser))
                .body("data.role", equalTo("DISPATCHER"))
                .body("data.password", nullValue())
                .body("data.passwordHash", nullValue())
                .extract()
                .path("data.userId");

        // Verify the newly created user can log in via /auth/login
        LoginRequest loginReq = new LoginRequest(uniqueUser, "Operator@123");
        given()
                .contentType(ContentType.JSON)
                .body(loginReq)
                .when()
                .post("/auth/login")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.accessToken", notNullValue())
                .body("data.user.id", equalTo(newUserId));
    }

    @Test
    @DisplayName("POST /users with duplicate username returns 409 Conflict")
    void createUser_duplicateUsername_conflict() {
        CreateUserRequest req = new CreateUserRequest(
                "admin", // already seeded
                "new_admin@logistics.com",
                "Password@123",
                UserRole.ADMIN
        );

        given()
                .header("Authorization", adminToken)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/users")
                .then()
                .statusCode(409)
                .body("success", equalTo(false))
                .body("message", containsString("already taken"));
    }

    @Test
    @DisplayName("POST /users with invalid input returns 400 Bad Request")
    void createUser_invalidInput_badRequest() {
        CreateUserRequest req = new CreateUserRequest(
                "", // blank username
                "not-an-email",
                "123", // short password
                null // null role
        );

        given()
                .header("Authorization", adminToken)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("/users")
                .then()
                .statusCode(400)
                .body("success", equalTo(false));
    }

    @Test
    @DisplayName("PATCH /users/{id} updates allowed fields")
    void updateUser_success() {
        String testUser = "upd_" + System.currentTimeMillis();
        CreateUserRequest createReq = new CreateUserRequest(
                testUser,
                testUser + "@logistics.com",
                "Password@123",
                UserRole.CUSTOMER
        );

        String id = given()
                .header("Authorization", adminToken)
                .contentType(ContentType.JSON)
                .body(createReq)
                .when()
                .post("/users")
                .then()
                .statusCode(201)
                .extract()
                .path("data.userId");

        UpdateUserRequest updateReq = new UpdateUserRequest(
                testUser + "_new@logistics.com",
                UserRole.DRIVER,
                UserStatus.ACTIVE
        );

        given()
                .header("Authorization", adminToken)
                .contentType(ContentType.JSON)
                .body(updateReq)
                .when()
                .patch("/users/" + id)
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("data.email", equalTo(testUser + "_new@logistics.com"))
                .body("data.role", equalTo("DRIVER"));
    }

    @Test
    @DisplayName("PATCH /users/{id}/status locks user, blocks login, and unlocks user successfully")
    void lockAndUnlockUser_lifecycle() {
        String lockUser = "lockable_" + System.currentTimeMillis();
        CreateUserRequest createReq = new CreateUserRequest(
                lockUser,
                lockUser + "@logistics.com",
                "Password@123",
                UserRole.CUSTOMER
        );

        String id = given()
                .header("Authorization", adminToken)
                .contentType(ContentType.JSON)
                .body(createReq)
                .when()
                .post("/users")
                .then()
                .statusCode(201)
                .extract()
                .path("data.userId");

        // 1. Lock the user
        given()
                .header("Authorization", adminToken)
                .contentType(ContentType.JSON)
                .body(new UpdateUserStatusRequest(UserStatus.LOCKED))
                .when()
                .patch("/users/" + id + "/status")
                .then()
                .statusCode(200)
                .body("data.status", equalTo("LOCKED"));

        // 2. Locked user must not be able to log in
        LoginRequest loginReq = new LoginRequest(lockUser, "Password@123");
        given()
                .contentType(ContentType.JSON)
                .body(loginReq)
                .when()
                .post("/auth/login")
                .then()
                .statusCode(403)
                .body("success", equalTo(false))
                .body("message", containsStringIgnoringCase("locked"));

        // 3. Unlock the user
        given()
                .header("Authorization", adminToken)
                .contentType(ContentType.JSON)
                .body(new UpdateUserStatusRequest(UserStatus.ACTIVE))
                .when()
                .patch("/users/" + id + "/status")
                .then()
                .statusCode(200)
                .body("data.status", equalTo("ACTIVE"));

        // 4. User can now log in again
        given()
                .contentType(ContentType.JSON)
                .body(loginReq)
                .when()
                .post("/auth/login")
                .then()
                .statusCode(200)
                .body("success", equalTo(true));
    }

    @Test
    @DisplayName("Cannot lock the last remaining active Administrator in the system")
    void cannotLockLastAdmin_badRequest() {
        // Attempting to lock the seeded admin (only 1 admin initially)
        given()
                .header("Authorization", adminToken)
                .contentType(ContentType.JSON)
                .body(new UpdateUserStatusRequest(UserStatus.LOCKED))
                .when()
                .patch("/users/00000000-0000-0000-0000-000000000001/status")
                .then()
                .statusCode(400)
                .body("success", equalTo(false))
                .body("message", containsString("last remaining active Administrator"));
    }
}
