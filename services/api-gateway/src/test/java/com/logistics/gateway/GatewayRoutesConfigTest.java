package com.logistics.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that all expected route IDs are registered in the {@link RouteLocator}.
 *
 * <p>These tests do NOT make actual HTTP calls to backend services.
 * They only assert that the Gateway's route configuration is complete and
 * correctly wired – protecting against accidental misconfiguration or
 * missing environment variables that would silently drop a route.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GatewayRoutesConfigTest {

    /** Expected route IDs as declared in application.yml */
    private static final List<String> EXPECTED_ROUTE_IDS = List.of(
            "user-service",
            "order-service",
            "shipment-service",
            "notification-service"
    );

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void allExpectedRoutesAreRegistered() {
        List<String> registeredIds = Flux.from(routeLocator.getRoutes())
                .map(route -> route.getId())
                .collectList()
                .block();

        assertThat(registeredIds)
                .as("Gateway must have exactly the four expected route IDs")
                .containsExactlyInAnyOrderElementsOf(EXPECTED_ROUTE_IDS);
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

    // -----------------------------------------------------------------------

    private void assertRouteExists(String routeId) {
        boolean exists = Flux.from(routeLocator.getRoutes())
                .any(route -> routeId.equals(route.getId()))
                .block();

        assertThat(exists)
                .as("Route '%s' should be registered in the Gateway", routeId)
                .isTrue();
    }
}
