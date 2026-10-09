package com.logistics.gateway;

import com.logistics.gateway.proxy.GatewayProxyService;
import com.logistics.gateway.route.GatewayRouteDefinition;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.common.mapper.TypeRef;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class GatewayRoutesConfigTest {

    private static final List<String> EXPECTED_ROUTE_IDS = List.of(
            "auth-service",
            "auth-service-direct",
            "user-service",
            "order-service",
            "shipment-service",
            "notification-service"
    );

    @Inject
    GatewayProxyService gatewayProxyService;

    @Test
    void allExpectedRoutesAreRegisteredInService() {
        List<String> registeredIds = gatewayProxyService.getRouteDefinitions().stream()
                .map(GatewayRouteDefinition::getId)
                .toList();

        assertThat(registeredIds)
                .as("Gateway must have all expected route IDs")
                .containsExactlyInAnyOrderElementsOf(EXPECTED_ROUTE_IDS);
    }

    @Test
    void routesExposedViaHttpEndpoint() {
        List<GatewayRouteDefinition> routes = RestAssured.given()
                .when()
                .get("/actuator/gateway/routes")
                .then()
                .statusCode(200)
                .extract()
                .as(new TypeRef<List<GatewayRouteDefinition>>() {});

        List<String> registeredIds = routes.stream()
                .map(GatewayRouteDefinition::getId)
                .toList();

        assertThat(registeredIds)
                .containsExactlyInAnyOrderElementsOf(EXPECTED_ROUTE_IDS);
    }

    @Test
    void authServiceRouteIsRegistered() {
        assertRouteExists("auth-service");
        assertRouteExists("auth-service-direct");
    }

    @Test
    void userServiceRouteIsRegistered() {
        assertRouteExists("user-service");
    }

    @Test
    void orderServiceRouteIsRegistered() {
        assertRouteExists("order-service");
    }

    @Test
    void shipmentServiceRouteIsRegistered() {
        assertRouteExists("shipment-service");
    }

    @Test
    void notificationServiceRouteIsRegistered() {
        assertRouteExists("notification-service");
    }

    private void assertRouteExists(String routeId) {
        boolean exists = gatewayProxyService.getRouteDefinitions().stream()
                .anyMatch(r -> routeId.equals(r.getId()));

        assertThat(exists)
                .as("Route '%s' should be registered in the Gateway", routeId)
                .isTrue();
    }
}
