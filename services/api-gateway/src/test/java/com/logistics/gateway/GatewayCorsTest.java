package com.logistics.gateway;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
class GatewayCorsTest {

    @Test
    @DisplayName("Preflight from a localhost dev origin is allowed with Authorization header")
    void preflight_localhostOrigin_allowed() {
        given()
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "Authorization,Content-Type")
                .when()
                .options("/api/orders")
                .then()
                .statusCode(200)
                .header("Access-Control-Allow-Origin", is("http://localhost:5173"))
                .header("Access-Control-Allow-Methods", containsString("POST"))
                .header("Access-Control-Allow-Headers", containsString("Authorization"));
    }

    @Test
    @DisplayName("Preflight from the Electron desktop origin app://dispatcher is allowed")
    void preflight_electronOrigin_allowed() {
        given()
                .header("Origin", "app://dispatcher")
                .header("Access-Control-Request-Method", "GET")
                .when()
                .options("/api/orders")
                .then()
                .statusCode(200)
                .header("Access-Control-Allow-Origin", is("app://dispatcher"));
    }

    @Test
    @DisplayName("Preflight from an unknown origin is not granted CORS access")
    void preflight_foreignOrigin_rejected() {
        given()
                .header("Origin", "https://evil.example.com")
                .header("Access-Control-Request-Method", "POST")
                .when()
                .options("/api/orders")
                .then()
                .header("Access-Control-Allow-Origin", nullValue());
    }
}
