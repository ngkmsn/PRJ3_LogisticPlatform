package com.logistics.user.controller;

import com.logistics.common.security.JwtTokenProvider;
import com.logistics.user.dto.LoginRequest;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class CustomerRegistrationIntegrationTest {

    @Inject
    JwtTokenProvider jwtTokenProvider;

    private static String uniqueName() {
        return "cust" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    @DisplayName("POST /api/auth/register creates an ACTIVE CUSTOMER, signs in, and the password works for login")
    void register_success_thenLogin() {
        String username = uniqueName();
        String token = given()
                .contentType(ContentType.JSON)
                .body(Map.of("username", username, "email", username + "@example.com", "password", "Secret@123"))
                .when()
                .post("/api/auth/register")
                .then()
                .statusCode(201)
                .body("success", is(true))
                .body("data.accessToken", notNullValue())
                .body("data.user.username", is(username))
                .body("data.user.role", is("CUSTOMER"))
                .body("data.user.status", is("ACTIVE"))
                .extract().path("data.accessToken");

        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
        assertThat(jwtTokenProvider.extractRole(token)).isEqualTo("CUSTOMER");

        given()
                .contentType(ContentType.JSON)
                .body(new LoginRequest(username, "Secret@123"))
                .when()
                .post("/auth/login")
                .then()
                .statusCode(200)
                .body("data.user.role", is("CUSTOMER"));
    }

    @Test
    @DisplayName("A role in the request body is ignored: self-registration always creates a CUSTOMER")
    void register_cannotEscalateRole() {
        String username = uniqueName();
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("username", username, "email", username + "@example.com",
                        "password", "Secret@123", "role", "ADMIN"))
                .when()
                .post("/auth/register")
                .then()
                .statusCode(201)
                .body("data.user.role", is("CUSTOMER"));
    }

    @Test
    @DisplayName("Duplicate username or email returns 409 Conflict")
    void register_duplicate_conflict() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("username", "admin", "email", uniqueName() + "@example.com", "password", "Secret@123"))
                .when()
                .post("/api/auth/register")
                .then()
                .statusCode(409)
                .body("success", is(false));

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("username", uniqueName(), "email", "admin@logistics.com", "password", "Secret@123"))
                .when()
                .post("/api/auth/register")
                .then()
                .statusCode(409);
    }

    @Test
    @DisplayName("Invalid input (bad email, short password) returns 400")
    void register_invalidInput_badRequest() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("username", uniqueName(), "email", "not-an-email", "password", "123"))
                .when()
                .post("/api/auth/register")
                .then()
                .statusCode(400);
    }
}
