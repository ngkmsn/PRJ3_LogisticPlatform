package com.logistics.gateway;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class GatewayAuthRoutingIntegrationTest {

    private static HttpServer mockUserService;
    private static int mockPort;

    @BeforeAll
    static void startMockBackend() throws IOException {
        mockUserService = HttpServer.create(new InetSocketAddress(0), 0);
        mockPort = mockUserService.getAddress().getPort();

        mockUserService.createContext("/auth/login", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                byte[] requestBytes = exchange.getRequestBody().readAllBytes();
                String requestBody = new String(requestBytes, StandardCharsets.UTF_8);

                if (requestBody.contains("wrong")) {
                    String errorResponse = "{\"success\":false,\"message\":\"Invalid username or password\",\"data\":null}";
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(401, errorResponse.getBytes(StandardCharsets.UTF_8).length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(errorResponse.getBytes(StandardCharsets.UTF_8));
                    }
                } else {
                    String successResponse = "{\"success\":true,\"message\":\"Login successful\",\"data\":{\"accessToken\":\"mock-gw-jwt-token\",\"tokenType\":\"Bearer\",\"expiresIn\":86400,\"user\":{\"username\":\"admin\",\"role\":\"ADMIN\"}}}";
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, successResponse.getBytes(StandardCharsets.UTF_8).length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(successResponse.getBytes(StandardCharsets.UTF_8));
                    }
                }
            }
        });

        HttpHandler meHandler = new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
                if (authHeader == null || !authHeader.startsWith("Bearer mock-valid-token")) {
                    String errorResponse = "{\"success\":false,\"message\":\"Missing or invalid Authorization header\",\"data\":null}";
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(401, errorResponse.getBytes(StandardCharsets.UTF_8).length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(errorResponse.getBytes(StandardCharsets.UTF_8));
                    }
                } else {
                    String successResponse = "{\"success\":true,\"message\":\"User profile retrieved successfully\",\"data\":{\"userId\":\"00000000-0000-0000-0000-000000000001\",\"username\":\"admin\",\"email\":\"admin@logistics.com\",\"role\":\"ADMIN\",\"status\":\"ACTIVE\"}}";
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, successResponse.getBytes(StandardCharsets.UTF_8).length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(successResponse.getBytes(StandardCharsets.UTF_8));
                    }
                }
            }
        };

        mockUserService.createContext("/auth/me", meHandler);
        mockUserService.createContext("/users/me", meHandler);

        HttpHandler usersHandler = new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
                String method = exchange.getRequestMethod();
                if (authHeader == null || !authHeader.startsWith("Bearer mock-admin-token")) {
                    String error = "{\"success\":false,\"message\":\"Access denied: Administrator privileges required\",\"data\":null}";
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(403, error.getBytes(StandardCharsets.UTF_8).length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(error.getBytes(StandardCharsets.UTF_8));
                    }
                } else {
                    String path = exchange.getRequestURI().getPath();
                    String resp;
                    if (path.endsWith("/roles")) {
                        resp = "{\"success\":true,\"message\":\"Supported roles and permissions retrieved successfully\",\"data\":[{\"role\":\"ADMIN\"},{\"role\":\"DISPATCHER\"},{\"role\":\"DRIVER\"},{\"role\":\"CUSTOMER\"}]}";
                    } else if (path.endsWith("/role")) {
                        resp = "{\"success\":true,\"message\":\"User role updated successfully\",\"data\":{\"userId\":\"user-123\",\"role\":\"DRIVER\"}}";
                    } else {
                        resp = "{\"success\":true,\"message\":\"Operation successful\",\"data\":{\"userId\":\"user-123\",\"username\":\"newuser\",\"role\":\"DISPATCHER\"}}";
                    }
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(method.equalsIgnoreCase("POST") ? 201 : 200, resp.getBytes(StandardCharsets.UTF_8).length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(resp.getBytes(StandardCharsets.UTF_8));
                    }
                }
            }
        };

        mockUserService.createContext("/users", usersHandler);

        HttpHandler driversHandler = new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
                String method = exchange.getRequestMethod();
                String path = exchange.getRequestURI().getPath();
                boolean isDriverAuth = authHeader != null && authHeader.startsWith("Bearer mock-driver-token");
                boolean isAdminAuth = authHeader != null && authHeader.startsWith("Bearer mock-admin-token");

                if (path.contains("/availability")) {
                    if (!isDriverAuth && !isAdminAuth) {
                        String error = "{\"success\":false,\"message\":\"Access denied\",\"data\":null}";
                        exchange.getResponseHeaders().set("Content-Type", "application/json");
                        exchange.sendResponseHeaders(403, error.getBytes(StandardCharsets.UTF_8).length);
                        try (OutputStream os = exchange.getResponseBody()) {
                            os.write(error.getBytes(StandardCharsets.UTF_8));
                        }
                        return;
                    }
                    String resp = "{\"success\":true,\"message\":\"Driver availability updated successfully\",\"data\":{\"driverId\":\"driver-prof-1\",\"availability\":\"AVAILABLE\"}}";
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, resp.getBytes(StandardCharsets.UTF_8).length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(resp.getBytes(StandardCharsets.UTF_8));
                    }
                    return;
                }

                if (path.contains("/eligibility")) {
                    if (!isDriverAuth && !isAdminAuth) {
                        String error = "{\"success\":false,\"message\":\"Access denied\",\"data\":null}";
                        exchange.getResponseHeaders().set("Content-Type", "application/json");
                        exchange.sendResponseHeaders(403, error.getBytes(StandardCharsets.UTF_8).length);
                        try (OutputStream os = exchange.getResponseBody()) {
                            os.write(error.getBytes(StandardCharsets.UTF_8));
                        }
                        return;
                    }
                    String resp = "{\"success\":true,\"message\":\"Driver eligibility evaluated successfully\",\"data\":{\"driverId\":\"driver-prof-1\",\"eligible\":true}}";
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, resp.getBytes(StandardCharsets.UTF_8).length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(resp.getBytes(StandardCharsets.UTF_8));
                    }
                    return;
                }

                if (!isAdminAuth) {
                    String error = "{\"success\":false,\"message\":\"Access denied: Administrator privileges required\",\"data\":null}";
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(403, error.getBytes(StandardCharsets.UTF_8).length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(error.getBytes(StandardCharsets.UTF_8));
                    }
                } else {
                    String resp = "{\"success\":true,\"message\":\"Driver operation successful\",\"data\":{\"id\":\"driver-prof-1\",\"fullName\":\"Test Driver\"}}";
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(method.equalsIgnoreCase("POST") ? 201 : 200, resp.getBytes(StandardCharsets.UTF_8).length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(resp.getBytes(StandardCharsets.UTF_8));
                    }
                }
            }
        };
        mockUserService.createContext("/drivers", driversHandler);

        HttpHandler ordersHandler = new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
                String method = exchange.getRequestMethod();
                if (authHeader == null || !authHeader.startsWith("Bearer mock-customer-token")) {
                    String error = "{\"success\":false,\"message\":\"Access denied: Only customers are permitted to access orders\",\"data\":null}";
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(403, error.getBytes(StandardCharsets.UTF_8).length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(error.getBytes(StandardCharsets.UTF_8));
                    }
                } else if (method.equalsIgnoreCase("GET")) {
                    String resp = "{\"success\":true,\"message\":\"Orders retrieved successfully\",\"data\":{\"content\":[{\"id\":\"ord-123\",\"orderNumber\":\"ORD-001\",\"status\":\"PENDING\"}],\"totalElements\":1,\"page\":0,\"size\":10,\"totalPages\":1}}";
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, resp.getBytes(StandardCharsets.UTF_8).length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(resp.getBytes(StandardCharsets.UTF_8));
                    }
                } else {
                    String resp = "{\"success\":true,\"message\":\"Order created successfully\",\"data\":{\"id\":\"ord-123\",\"orderNumber\":\"ORD-001\",\"status\":\"PENDING\"}}";
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(201, resp.getBytes(StandardCharsets.UTF_8).length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(resp.getBytes(StandardCharsets.UTF_8));
                    }
                }
            }
        };
        mockUserService.createContext("/orders", ordersHandler);

        mockUserService.start();
        System.setProperty("USER_SERVICE_URL", "http://localhost:" + mockPort);
        System.setProperty("ORDER_SERVICE_URL", "http://localhost:" + mockPort);
    }

    @AfterAll
    static void stopMockBackend() {
        System.clearProperty("USER_SERVICE_URL");
        System.clearProperty("ORDER_SERVICE_URL");
        if (mockUserService != null) {
            mockUserService.stop(0);
        }
    }

    @Test
    @DisplayName("Gateway successfully routes POST /api/auth/login with StripPrefix to backend user service")
    void gatewayRoutesApiAuthLoginSuccessfully() {
        String loginPayload = "{\"username\":\"admin\",\"password\":\"Admin@123\"}";

        RestAssured.given()
                .contentType(ContentType.JSON)
                .body(loginPayload)
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("success", equalTo(true))
                .body("data.accessToken", equalTo("mock-gw-jwt-token"))
                .body("data.user.role", equalTo("ADMIN"));
    }

    @Test
    @DisplayName("Gateway successfully routes POST /auth/login (direct) to backend user service")
    void gatewayRoutesDirectAuthLoginSuccessfully() {
        String loginPayload = "{\"username\":\"admin\",\"password\":\"Admin@123\"}";

        RestAssured.given()
                .contentType(ContentType.JSON)
                .body(loginPayload)
                .when()
                .post("/auth/login")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("success", equalTo(true))
                .body("data.accessToken", equalTo("mock-gw-jwt-token"));
    }

    @Test
    @DisplayName("Gateway routes failure response 401 when backend rejects invalid credentials")
    void gatewayRoutesAuthLoginFailureCorrectly() {
        String loginPayload = "{\"username\":\"admin\",\"password\":\"wrong\"}";

        RestAssured.given()
                .contentType(ContentType.JSON)
                .body(loginPayload)
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(401)
                .body("success", equalTo(false))
                .body("message", equalTo("Invalid username or password"));
    }

    @Test
    @DisplayName("Gateway successfully routes GET /api/auth/me with StripPrefix to backend user service")
    void gatewayRoutesApiAuthMeSuccessfully() {
        RestAssured.given()
                .header("Authorization", "Bearer mock-valid-token")
                .when()
                .get("/api/auth/me")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("success", equalTo(true))
                .body("data.userId", equalTo("00000000-0000-0000-0000-000000000001"))
                .body("data.username", equalTo("admin"))
                .body("data.role", equalTo("ADMIN"));
    }

    @Test
    @DisplayName("Gateway successfully routes GET /api/users/me with StripPrefix to backend user service")
    void gatewayRoutesApiUsersMeSuccessfully() {
        RestAssured.given()
                .header("Authorization", "Bearer mock-valid-token")
                .when()
                .get("/api/users/me")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("success", equalTo(true))
                .body("data.userId", equalTo("00000000-0000-0000-0000-000000000001"))
                .body("data.username", equalTo("admin"))
                .body("data.role", equalTo("ADMIN"));
    }

    @Test
    @DisplayName("Gateway routes unauthenticated request to /api/auth/me with 401 Unauthorized")
    void gatewayRoutesUnauthenticatedApiAuthMeWith401() {
        RestAssured.given()
                .when()
                .get("/api/auth/me")
                .then()
                .statusCode(401)
                .body("success", equalTo(false))
                .body("message", equalTo("Missing or invalid Authorization header"));
    }

    @Test
    @DisplayName("Gateway successfully routes GET /api/users with StripPrefix to backend user service")
    void gatewayRoutesApiUsersListSuccessfully() {
        RestAssured.given()
                .header("Authorization", "Bearer mock-admin-token")
                .when()
                .get("/api/users")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("success", equalTo(true));
    }

    @Test
    @DisplayName("Gateway successfully routes POST /api/users with StripPrefix to backend user service")
    void gatewayRoutesApiUsersCreateSuccessfully() {
        String newUserPayload = "{\"username\":\"newuser\",\"email\":\"newuser@logistics.com\",\"password\":\"Pass@123\",\"role\":\"DISPATCHER\"}";

        RestAssured.given()
                .header("Authorization", "Bearer mock-admin-token")
                .contentType(ContentType.JSON)
                .body(newUserPayload)
                .when()
                .post("/api/users")
                .then()
                .statusCode(201)
                .contentType(ContentType.JSON)
                .body("success", equalTo(true))
                .body("data.username", equalTo("newuser"));
    }

    @Test
    @DisplayName("Gateway routes non-admin request to /api/users with 403 Forbidden")
    void gatewayRoutesApiUsersNonAdminForbidden() {
        RestAssured.given()
                .header("Authorization", "Bearer mock-driver-token")
                .when()
                .get("/api/users")
                .then()
                .statusCode(403)
                .body("success", equalTo(false))
                .body("message", containsString("Administrator privileges required"));
    }

    @Test
    @DisplayName("Gateway successfully routes GET /api/users/roles with StripPrefix to backend user service")
    void gatewayRoutesApiUsersRolesSuccessfully() {
        RestAssured.given()
                .header("Authorization", "Bearer mock-admin-token")
                .when()
                .get("/api/users/roles")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("success", equalTo(true))
                .body("data.size()", equalTo(4));
    }

    @Test
    @DisplayName("Gateway successfully routes PATCH /api/users/{id}/role with StripPrefix to backend user service")
    void gatewayRoutesApiUsersUpdateRoleSuccessfully() {
        String payload = "{\"role\":\"DRIVER\"}";

        RestAssured.given()
                .header("Authorization", "Bearer mock-admin-token")
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .patch("/api/users/user-123/role")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("success", equalTo(true))
                .body("data.role", equalTo("DRIVER"));
    }

    @Test
    @DisplayName("Gateway successfully routes GET /api/drivers with StripPrefix to backend user service")
    void gatewayRoutesApiDriversListSuccessfully() {
        RestAssured.given()
                .header("Authorization", "Bearer mock-admin-token")
                .when()
                .get("/api/drivers")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("success", equalTo(true))
                .body("data.id", equalTo("driver-prof-1"));
    }

    @Test
    @DisplayName("Gateway successfully routes POST /api/drivers with StripPrefix to backend user service")
    void gatewayRoutesApiDriversCreateSuccessfully() {
        String payload = "{\"userId\":\"usr-1\",\"fullName\":\"Test Driver\",\"phoneNumber\":\"0988776655\",\"licenseNumber\":\"GPLX-1\",\"licenseClass\":\"C\",\"vehicleType\":\"TRUCK\",\"vehiclePlate\":\"29C-12345\"}";

        RestAssured.given()
                .header("Authorization", "Bearer mock-admin-token")
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/api/drivers")
                .then()
                .statusCode(201)
                .contentType(ContentType.JSON)
                .body("success", equalTo(true))
                .body("data.id", equalTo("driver-prof-1"));
    }

    @Test
    @DisplayName("Gateway successfully routes PATCH /api/drivers/me/availability with StripPrefix to backend user service")
    void gatewayRoutesApiDriverAvailabilitySuccessfully() {
        String payload = "{\"availability\":\"AVAILABLE\"}";

        RestAssured.given()
                .header("Authorization", "Bearer mock-driver-token")
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .patch("/api/drivers/me/availability")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("success", equalTo(true))
                .body("data.availability", equalTo("AVAILABLE"));
    }

    @Test
    @DisplayName("Gateway successfully routes GET /api/drivers/me/eligibility with StripPrefix to backend user service")
    void gatewayRoutesApiDriverEligibilitySuccessfully() {
        RestAssured.given()
                .header("Authorization", "Bearer mock-driver-token")
                .when()
                .get("/api/drivers/me/eligibility")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("success", equalTo(true))
                .body("data.eligible", equalTo(true));
    }

    @Test
    @DisplayName("Gateway successfully routes POST /api/orders with StripPrefix to backend order service")
    void gatewayRoutesApiOrdersCreateSuccessfully() {
        String payload = "{\"recipientName\":\"Test\",\"recipientPhone\":\"0900000000\",\"deliveryAddress\":\"Hanoi\",\"items\":[{\"productName\":\"Item\",\"quantity\":1,\"unitPrice\":10000}]}";

        RestAssured.given()
                .header("Authorization", "Bearer mock-customer-token")
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/api/orders")
                .then()
                .statusCode(201)
                .contentType(ContentType.JSON)
                .body("success", equalTo(true))
                .body("data.status", equalTo("PENDING"));
    }

    @Test
    @DisplayName("Gateway successfully routes GET /api/orders with StripPrefix and query params to backend order service")
    void gatewayRoutesApiOrdersListSuccessfully() {
        RestAssured.given()
                .header("Authorization", "Bearer mock-customer-token")
                .queryParam("page", 0)
                .queryParam("size", 10)
                .when()
                .get("/api/orders")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("success", equalTo(true))
                .body("data.content[0].orderNumber", equalTo("ORD-001"));
    }
}
