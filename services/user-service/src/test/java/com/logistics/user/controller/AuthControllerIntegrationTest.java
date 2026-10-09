package com.logistics.user.controller;

import com.logistics.common.security.JwtTokenProvider;
import com.logistics.user.dto.LoginRequest;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class AuthControllerIntegrationTest {

    @Inject
    JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("POST /auth/login with valid seeded admin credentials returns token and user info")
    void login_admin_success() {
        LoginRequest request = new LoginRequest("admin", "Admin@123");

        String token = given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/auth/login")
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("message", is("Login successful"))
                .body("data.accessToken", notNullValue())
                .body("data.tokenType", is("Bearer"))
                .body("data.user.username", is("admin"))
                .body("data.user.email", is("admin@logistics.com"))
                .body("data.user.role", is("ADMIN"))
                .body("data.user.status", is("ACTIVE"))
                .extract()
                .path("data.accessToken");

        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
        assertThat(jwtTokenProvider.extractUsername(token)).isEqualTo("admin");
        assertThat(jwtTokenProvider.extractRole(token)).isEqualTo("ADMIN");
        assertThat(jwtTokenProvider.extractEmail(token)).isEqualTo("admin@logistics.com");
    }

    @Test
    @DisplayName("POST /auth/login allows login with email identifier")
    void login_with_email_success() {
        LoginRequest request = new LoginRequest("driver@logistics.com", "Driver@123");

        given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/auth/login")
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.user.username", is("driver"))
                .body("data.user.role", is("DRIVER"));
    }

    @Test
    @DisplayName("POST /auth/login with incorrect password fails with 401 Unauthorized")
    void login_wrong_password_fails() {
        LoginRequest request = new LoginRequest("admin", "WrongPassword123");

        given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/auth/login")
                .then()
                .statusCode(401)
                .body("success", is(false))
                .body("message", is("Invalid username or password"));
    }

    @Test
    @DisplayName("POST /auth/login with unknown username fails with 401 Unauthorized")
    void login_unknown_user_fails() {
        LoginRequest request = new LoginRequest("nonexistent_user", "AnyPassword");

        given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/auth/login")
                .then()
                .statusCode(401)
                .body("success", is(false))
                .body("message", is("Invalid username or password"));
    }

    @Test
    @DisplayName("POST /auth/login with missing fields returns 400 Bad Request")
    void login_blank_fields_badRequest() {
        LoginRequest request = new LoginRequest("", "");

        given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/auth/login")
                .then()
                .statusCode(400)
                .body("success", is(false))
                .body("message", containsString("required"));
    }

    @Test
    @DisplayName("GET /auth/verify validates valid Bearer token and returns claims")
    void verify_token_success() {
        String token = jwtTokenProvider.generateToken(
                "00000000-0000-0000-0000-000000000002",
                "dispatcher",
                "dispatcher@logistics.com",
                "DISPATCHER"
        );

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/auth/verify")
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.username", is("dispatcher"))
                .body("data.role", is("DISPATCHER"));
    }

    @Test
    @DisplayName("GET /auth/verify with invalid token returns 401 Unauthorized")
    void verify_token_invalid_fails() {
        given()
                .header("Authorization", "Bearer invalid-tampered-token")
                .when()
                .get("/auth/verify")
                .then()
                .statusCode(401)
                .body("success", is(false))
                .body("message", containsString("invalid or expired"));
    }

    @Test
    @DisplayName("GET /auth/verify with missing header returns 401 Unauthorized")
    void verify_token_missing_header_fails() {
        given()
                .when()
                .get("/auth/verify")
                .then()
                .statusCode(401)
                .body("success", is(false))
                .body("message", containsString("Missing or invalid"));
    }

    @Test
    @DisplayName("GET /auth/me with valid token returns current user profile and role")
    void get_current_user_me_success() {
        // Seeded admin user: id = 00000000-0000-0000-0000-000000000001
        String token = jwtTokenProvider.generateToken(
                "00000000-0000-0000-0000-000000000001",
                "admin",
                "admin@logistics.com",
                "ADMIN"
        );

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/auth/me")
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("message", is("User profile retrieved successfully"))
                .body("data.userId", is("00000000-0000-0000-0000-000000000001"))
                .body("data.username", is("admin"))
                .body("data.email", is("admin@logistics.com"))
                .body("data.role", is("ADMIN"))
                .body("data.status", is("ACTIVE"))
                .body("data.createdAt", notNullValue());
    }

    @Test
    @DisplayName("GET /users/me with valid token returns current driver profile")
    void get_current_user_via_users_me_success() {
        // Seeded driver user: id = 00000000-0000-0000-0000-000000000003
        String token = jwtTokenProvider.generateToken(
                "00000000-0000-0000-0000-000000000003",
                "driver",
                "driver@logistics.com",
                "DRIVER"
        );

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/users/me")
                .then()
                .statusCode(200)
                .body("success", is(true))
                .body("data.userId", is("00000000-0000-0000-0000-000000000003"))
                .body("data.username", is("driver"))
                .body("data.role", is("DRIVER"))
                .body("data.status", is("ACTIVE"));
    }

    @Test
    @DisplayName("GET /auth/me unauthenticated returns 401 Unauthorized")
    void get_current_user_unauthenticated_fails() {
        given()
                .when()
                .get("/auth/me")
                .then()
                .statusCode(401)
                .body("success", is(false))
                .body("message", containsString("Missing or invalid"));
    }

    @Test
    @DisplayName("GET /auth/me with invalid or expired token returns 401 Unauthorized")
    void get_current_user_invalid_token_fails() {
        given()
                .header("Authorization", "Bearer invalid-expired-token")
                .when()
                .get("/auth/me")
                .then()
                .statusCode(401)
                .body("success", is(false))
                .body("message", containsString("invalid or expired"));
    }
}
