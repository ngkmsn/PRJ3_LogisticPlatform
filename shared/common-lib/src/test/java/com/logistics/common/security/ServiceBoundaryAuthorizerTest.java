package com.logistics.common.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceBoundaryAuthorizerTest {

    private static final String SECRET = "unit-test-secret-key-must-be-very-long-32-chars!!";
    private JwtTokenProvider jwtTokenProvider;
    private ServiceTokenProvider serviceTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(SECRET, 3600000L, "logistics-platform");
        serviceTokenProvider = new ServiceTokenProvider("shipment-service", SECRET, "shipment-service", 300L);
    }

    @Test
    @DisplayName("requireAuthenticated: fails on null or invalid Bearer format")
    void requireAuthenticated_missingOrInvalidHeader() {
        assertThatThrownBy(() -> ServiceBoundaryAuthorizer.requireAuthenticated(null, jwtTokenProvider))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Missing or invalid Authorization header");

        assertThatThrownBy(() -> ServiceBoundaryAuthorizer.requireAuthenticated("Basic abc", jwtTokenProvider))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Missing or invalid Authorization header");

        assertThatThrownBy(() -> ServiceBoundaryAuthorizer.requireAuthenticated("Bearer invalid-jwt-garbage", jwtTokenProvider))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Token is invalid or expired");
    }

    @Test
    @DisplayName("requireAuthenticated: succeeds on valid user token and service token")
    void requireAuthenticated_validTokens() {
        String userToken = jwtTokenProvider.generateToken("usr-1", "alice", "alice@example.com", "CUSTOMER");
        AuthPrincipal userPrincipal = ServiceBoundaryAuthorizer.requireAuthenticated("Bearer " + userToken, jwtTokenProvider);
        assertThat(userPrincipal).isNotNull();
        assertThat(userPrincipal.getUserId()).isEqualTo("usr-1");
        assertThat(userPrincipal.getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(userPrincipal.isService()).isFalse();

        String s2sToken = serviceTokenProvider.generateToken("order-service", Set.of("order:read"));
        AuthPrincipal s2sPrincipal = ServiceBoundaryAuthorizer.requireAuthenticated("Bearer " + s2sToken, jwtTokenProvider);
        assertThat(s2sPrincipal).isNotNull();
        assertThat(s2sPrincipal.isService()).isTrue();
        assertThat(s2sPrincipal.getServiceId()).isEqualTo("shipment-service");
        assertThat(s2sPrincipal.hasAudience("order-service")).isTrue();
    }

    @Test
    @DisplayName("requireRole: allows matching role, rejects service or mismatched role")
    void requireRole_enforcement() {
        AuthPrincipal customer = new AuthPrincipal("c-1", "alice", "c@ex.com", Role.CUSTOMER);
        AuthPrincipal admin = new AuthPrincipal("a-1", "admin", "a@ex.com", Role.ADMIN);
        AuthPrincipal service = new AuthPrincipal("svc-1", "svc-1", null, Role.SERVICE, Set.of("scope"), Set.of("aud"));

        // Allowed
        assertThatCode(() -> ServiceBoundaryAuthorizer.requireRole(customer, "view orders", Role.CUSTOMER))
                .doesNotThrowAnyException();
        assertThatCode(() -> ServiceBoundaryAuthorizer.requireRole(admin, "admin actions", Role.ADMIN, Role.DISPATCHER))
                .doesNotThrowAnyException();

        // Mismatched role
        assertThatThrownBy(() -> ServiceBoundaryAuthorizer.requireRole(customer, "dispatch order", Role.ADMIN, Role.DISPATCHER))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Insufficient privileges");

        // Service identity blocked from user-role action
        assertThatThrownBy(() -> ServiceBoundaryAuthorizer.requireRole(service, "customer profile", Role.CUSTOMER))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Machine/Service identities cannot perform user action");
    }

    @Test
    @DisplayName("requirePermission: enforces RBAC permissions")
    void requirePermission_enforcement() {
        AuthPrincipal customer = new AuthPrincipal("c-1", "alice", "c@ex.com", Role.CUSTOMER);
        AuthPrincipal driver = new AuthPrincipal("d-1", "bob", "d@ex.com", Role.DRIVER);
        AuthPrincipal admin = new AuthPrincipal("a-1", "root", "a@ex.com", Role.ADMIN);

        // Admin has all permissions
        assertThatCode(() -> ServiceBoundaryAuthorizer.requirePermission(admin, Permission.ORDER_CREATE, "create order"))
                .doesNotThrowAnyException();
        assertThatCode(() -> ServiceBoundaryAuthorizer.requirePermission(admin, Permission.SHIPMENT_LOCATION_PING, "ping GPS"))
                .doesNotThrowAnyException();

        // Customer can create order, but cannot ping shipment location
        assertThatCode(() -> ServiceBoundaryAuthorizer.requirePermission(customer, Permission.ORDER_CREATE, "create order"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> ServiceBoundaryAuthorizer.requirePermission(customer, Permission.SHIPMENT_LOCATION_PING, "ping GPS"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Lacks required permission 'shipment:location_ping'");

        // Driver can ping GPS, but cannot create order
        assertThatCode(() -> ServiceBoundaryAuthorizer.requirePermission(driver, Permission.SHIPMENT_LOCATION_PING, "ping GPS"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> ServiceBoundaryAuthorizer.requirePermission(driver, Permission.ORDER_CREATE, "create order"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Lacks required permission 'order:create'");
    }

    @Test
    @DisplayName("requireCustomerOwnership: prevents IDOR access to orders of other customers")
    void requireCustomerOwnership_idorProtection() {
        AuthPrincipal customer1 = new AuthPrincipal("cust-100", "alice", "c1@ex.com", Role.CUSTOMER);
        AuthPrincipal customer2 = new AuthPrincipal("cust-200", "bob", "c2@ex.com", Role.CUSTOMER);
        AuthPrincipal driver = new AuthPrincipal("drv-1", "charlie", "d@ex.com", Role.DRIVER);

        // Owner customer can access own order
        assertThatCode(() -> ServiceBoundaryAuthorizer.requireCustomerOwnership(customer1, "cust-100", "Order", "ORD-1"))
                .doesNotThrowAnyException();

        // Another customer attempting IDOR is blocked
        assertThatThrownBy(() -> ServiceBoundaryAuthorizer.requireCustomerOwnership(customer2, "cust-100", "Order", "ORD-1"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Access denied: You do not own this Order");

        // Driver calling customer ownership is blocked
        assertThatThrownBy(() -> ServiceBoundaryAuthorizer.requireCustomerOwnership(driver, "cust-100", "Order", "ORD-1"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only the owning customer or authorized operations may access this Order");
    }

    @Test
    @DisplayName("requireCustomerOwnershipOrOperations: allows owner customer, Admin, and Dispatcher, rejects others")
    void requireCustomerOwnershipOrOperations_enforcement() {
        AuthPrincipal customerOwner = new AuthPrincipal("cust-100", "alice", "c1@ex.com", Role.CUSTOMER);
        AuthPrincipal customerStranger = new AuthPrincipal("cust-200", "bob", "c2@ex.com", Role.CUSTOMER);
        AuthPrincipal dispatcher = new AuthPrincipal("disp-1", "operator", "op@ex.com", Role.DISPATCHER);
        AuthPrincipal admin = new AuthPrincipal("adm-1", "admin", "adm@ex.com", Role.ADMIN);
        AuthPrincipal driver = new AuthPrincipal("drv-1", "driver", "drv@ex.com", Role.DRIVER);

        // Owner succeeds
        assertThatCode(() -> ServiceBoundaryAuthorizer.requireCustomerOwnershipOrOperations(customerOwner, "cust-100", "Order", "ORD-1"))
                .doesNotThrowAnyException();

        // Operations succeed
        assertThatCode(() -> ServiceBoundaryAuthorizer.requireCustomerOwnershipOrOperations(dispatcher, "cust-100", "Order", "ORD-1"))
                .doesNotThrowAnyException();
        assertThatCode(() -> ServiceBoundaryAuthorizer.requireCustomerOwnershipOrOperations(admin, "cust-100", "Order", "ORD-1"))
                .doesNotThrowAnyException();

        // Stranger customer is blocked
        assertThatThrownBy(() -> ServiceBoundaryAuthorizer.requireCustomerOwnershipOrOperations(customerStranger, "cust-100", "Order", "ORD-1"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You do not own this Order");

        // Driver is blocked
        assertThatThrownBy(() -> ServiceBoundaryAuthorizer.requireCustomerOwnershipOrOperations(driver, "cust-100", "Order", "ORD-1"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("requireDriverOwnership: prevents unassigned drivers from updating shipment state (IDOR protection)")
    void requireDriverOwnership_idorProtection() {
        AuthPrincipal assignedDriver = new AuthPrincipal("usr-drv-1", "dave", "d@ex.com", Role.DRIVER);
        AuthPrincipal intruderDriver = new AuthPrincipal("usr-drv-2", "eve", "e@ex.com", Role.DRIVER);
        AuthPrincipal customer = new AuthPrincipal("cust-1", "alice", "c@ex.com", Role.CUSTOMER);

        String assignedDriverId = "dp-profile-1";
        String resolvedDriverProfileId = "dp-profile-1";

        // Assigned driver succeeds
        assertThatCode(() -> ServiceBoundaryAuthorizer.requireDriverOwnership(
                assignedDriver, assignedDriverId, resolvedDriverProfileId, "Shipment", "ORD-99"))
                .doesNotThrowAnyException();

        // Intruder driver attempting to manipulate shipment is blocked
        assertThatThrownBy(() -> ServiceBoundaryAuthorizer.requireDriverOwnership(
                intruderDriver, assignedDriverId, "dp-profile-2", "Shipment", "ORD-99"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You are not the assigned driver for Shipment 'ORD-99'");

        // Customer attempting driver operation is blocked
        assertThatThrownBy(() -> ServiceBoundaryAuthorizer.requireDriverOwnership(
                customer, assignedDriverId, null, "Shipment", "ORD-99"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only the assigned driver may perform operations on this Shipment");
    }

    @Test
    @DisplayName("requireServiceScope: enforces service identity, target audience, and required scope")
    void requireServiceScope_enforcement() {
        AuthPrincipal user = new AuthPrincipal("u-1", "alice", "u@ex.com", Role.CUSTOMER);
        AuthPrincipal s2sValid = new AuthPrincipal("shipment-service", "shipment-service", null, Role.SERVICE,
                Set.of("user:read", "driver:read"), Set.of("user-service"));
        AuthPrincipal s2sWrongAud = new AuthPrincipal("shipment-service", "shipment-service", null, Role.SERVICE,
                Set.of("driver:read"), Set.of("order-service"));
        AuthPrincipal s2sMissingScope = new AuthPrincipal("shipment-service", "shipment-service", null, Role.SERVICE,
                Set.of("other:scope"), Set.of("user-service"));

        // Valid S2S call
        assertThatCode(() -> ServiceBoundaryAuthorizer.requireServiceScope(s2sValid, "user-service", "driver:read", "query drivers"))
                .doesNotThrowAnyException();

        // User attempting S2S endpoint
        assertThatThrownBy(() -> ServiceBoundaryAuthorizer.requireServiceScope(user, "user-service", "driver:read", "query drivers"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Service identity required");

        // Audience mismatch
        assertThatThrownBy(() -> ServiceBoundaryAuthorizer.requireServiceScope(s2sWrongAud, "user-service", "driver:read", "query drivers"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Invalid token audience: expected user-service");

        // Missing scope
        assertThatThrownBy(() -> ServiceBoundaryAuthorizer.requireServiceScope(s2sMissingScope, "user-service", "driver:read", "query drivers"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Insufficient service permissions: required scope driver:read");
    }

    @Test
    @DisplayName("requireUserPermissionOrServiceScope: handles dual-calling boundaries seamlessly")
    void requireUserPermissionOrServiceScope_dualBoundary() {
        AuthPrincipal dispatcher = new AuthPrincipal("disp-1", "dispatcher", "d@ex.com", Role.DISPATCHER);
        AuthPrincipal customer = new AuthPrincipal("cust-1", "customer", "c@ex.com", Role.CUSTOMER);
        AuthPrincipal serviceValid = new AuthPrincipal("shipment-service", "shipment-service", null, Role.SERVICE,
                Set.of("order:metrics:read"), Set.of("order-service"));
        AuthPrincipal serviceNoScope = new AuthPrincipal("shipment-service", "shipment-service", null, Role.SERVICE,
                Set.of("order:read"), Set.of("order-service"));

        // Dispatcher has ORDER_READ permission $\to$ allowed
        assertThatCode(() -> ServiceBoundaryAuthorizer.requireUserPermissionOrServiceScope(
                dispatcher, Permission.ORDER_READ, "order-service", "order:metrics:read", "get order metrics"))
                .doesNotThrowAnyException();

        // Customer lacks ORDER_UPDATE or operations permission
        assertThatThrownBy(() -> ServiceBoundaryAuthorizer.requireUserPermissionOrServiceScope(
                customer, Permission.ORDER_UPDATE, "order-service", "order:metrics:read", "update order status"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Lacks required permission");

        // Service with scope allowed
        assertThatCode(() -> ServiceBoundaryAuthorizer.requireUserPermissionOrServiceScope(
                serviceValid, Permission.ORDER_READ, "order-service", "order:metrics:read", "get order metrics"))
                .doesNotThrowAnyException();

        // Service without scope rejected
        assertThatThrownBy(() -> ServiceBoundaryAuthorizer.requireUserPermissionOrServiceScope(
                serviceNoScope, Permission.ORDER_READ, "order-service", "order:metrics:read", "get order metrics"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Insufficient service permissions: required scope order:metrics:read");
    }
}
