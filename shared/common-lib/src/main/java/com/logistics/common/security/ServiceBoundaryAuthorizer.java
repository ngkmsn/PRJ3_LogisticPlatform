package com.logistics.common.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

/**
 * ServiceBoundaryAuthorizer encapsulates authorization enforcement rules for microservice boundaries (PB-058).
 * <p>
 * Ensures each microservice verifies:
 * 1. Authentication (who the caller is - verified JWT token).
 * 2. Authorization (role-based permissions or service-identity scopes).
 * 3. Resource Ownership (customers only access their own resources, drivers only access assigned shipments, IDOR prevention).
 * 4. Least Privilege Service-to-Service boundaries (matching target audience and required action scope).
 */
public final class ServiceBoundaryAuthorizer {

    private static final Logger log = LoggerFactory.getLogger(ServiceBoundaryAuthorizer.class);

    private ServiceBoundaryAuthorizer() {
    }

    /**
     * Verifies that the Authorization header contains a valid, unexpired JWT and extracts the AuthPrincipal.
     *
     * @param authHeader       the HTTP Authorization header
     * @param jwtTokenProvider the JWT token validator and extractor
     * @return non-null AuthPrincipal
     * @throws BadCredentialsException (401) if missing, malformed, invalid, or expired
     */
    public static AuthPrincipal requireAuthenticated(String authHeader, JwtTokenProvider jwtTokenProvider) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Authentication failed at service boundary: missing or invalid Authorization header");
            throw new BadCredentialsException("Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7).trim();
        if (jwtTokenProvider == null || !jwtTokenProvider.validateToken(token)) {
            log.warn("Authentication failed at service boundary: token is invalid, expired, or signature mismatch");
            throw new BadCredentialsException("Token is invalid or expired");
        }

        AuthPrincipal principal = AuthPrincipal.fromJwtToken(token, jwtTokenProvider);
        if (principal == null) {
            log.warn("Authentication failed at service boundary: token identity could not be verified");
            throw new BadCredentialsException("Token identity could not be verified");
        }

        return principal;
    }

    /**
     * Enforces that the principal holds at least one of the allowed user roles.
     *
     * @param principal    authenticated principal
     * @param action       action description for error message and audit logging
     * @param allowedRoles variable list of allowed roles
     * @throws AccessDeniedException (403) if the principal does not possess any of the allowed roles
     */
    public static void requireRole(AuthPrincipal principal, String action, Role... allowedRoles) {
        Objects.requireNonNull(principal, "principal must not be null");

        if (principal.isService()) {
            log.warn("Access denied: Service identity '{}' attempted to access user-role-restricted action: {}",
                    principal.getServiceId(), action);
            throw new AccessDeniedException("Access denied: Machine/Service identities cannot perform user action '" + action + "' directly");
        }

        Role userRole = principal.getRole();
        if (userRole == null) {
            log.warn("Access denied: Principal '{}' has no role assigned for action: {}", principal.getUserId(), action);
            throw new AccessDeniedException("Access denied: No role assigned to user");
        }

        for (Role role : allowedRoles) {
            if (userRole == role) {
                return;
            }
        }

        log.warn("Access denied: User '{}' with role '{}' lacks required role {} for action: {}",
                principal.getUserId(), userRole, Arrays.toString(allowedRoles), action);
        throw new AccessDeniedException("Access denied: Insufficient privileges for " + action + " (requires " + Arrays.toString(allowedRoles) + ")");
    }

    /**
     * Enforces that the principal holds the required business permission.
     *
     * @param principal  authenticated principal
     * @param permission required permission
     * @param action     action description
     */
    public static void requirePermission(AuthPrincipal principal, Permission permission, String action) {
        Objects.requireNonNull(principal, "principal must not be null");
        Objects.requireNonNull(permission, "permission must not be null");

        if (principal.isAdmin()) {
            return;
        }

        if (!principal.hasPermission(permission)) {
            log.warn("Access denied: User '{}' with role '{}' lacks permission '{}' for action: {}",
                    principal.getUserId(), principal.getRole(), permission.getCode(), action);
            throw new AccessDeniedException("Access denied: Lacks required permission '" + permission.getCode() + "' for " + action);
        }
    }

    /**
     * Enforces that the caller is Operations staff (ADMIN or DISPATCHER).
     *
     * @param principal authenticated principal
     * @param action    action description
     */
    public static void requireOperationsOrAdmin(AuthPrincipal principal, String action) {
        requireRole(principal, action, Role.ADMIN, Role.DISPATCHER);
    }

    /**
     * Enforces customer ownership: Customer caller can ONLY access their own resource (IDOR protection).
     *
     * @param principal               authenticated principal
     * @param resourceOwnerCustomerId the customerId on the target resource (e.g. order.customerId)
     * @param resourceType            e.g. "Order"
     * @param resourceId              target resource ID
     * @throws AccessDeniedException (403) if caller is not the owner
     */
    public static void requireCustomerOwnership(AuthPrincipal principal, String resourceOwnerCustomerId,
                                                String resourceType, String resourceId) {
        Objects.requireNonNull(principal, "principal must not be null");

        if (principal.isCustomer()) {
            if (resourceOwnerCustomerId == null || !principal.getUserId().trim().equalsIgnoreCase(resourceOwnerCustomerId.trim())) {
                log.warn("PB-058 [IDOR_BLOCKED]: Customer '{}' attempted unauthorized access to {} '{}' belonging to customer '{}'",
                        principal.getUserId(), resourceType, resourceId, resourceOwnerCustomerId);
                throw new AccessDeniedException("Access denied: You do not own this " + resourceType);
            }
            return;
        }

        // If not customer, caller cannot rely on customer ownership rules
        log.warn("Access denied: Caller '{}' with role '{}' is not a CUSTOMER for {} ownership check",
                principal.getUserId(), principal.getRole(), resourceType);
        throw new AccessDeniedException("Access denied: Only the owning customer or authorized operations may access this " + resourceType);
    }

    /**
     * Enforces customer ownership OR Operations (ADMIN/DISPATCHER) access.
     */
    public static void requireCustomerOwnershipOrOperations(AuthPrincipal principal, String resourceOwnerCustomerId,
                                                            String resourceType, String resourceId) {
        Objects.requireNonNull(principal, "principal must not be null");

        if (principal.isOperations() || principal.isAdmin()) {
            return;
        }

        requireCustomerOwnership(principal, resourceOwnerCustomerId, resourceType, resourceId);
    }

    /**
     * Enforces driver ownership: Driver caller can ONLY access or update resources assigned to them (IDOR protection).
     *
     * @param principal               authenticated principal
     * @param assignedDriverId        the driverId currently assigned on the resource
     * @param resolvedDriverProfileId the driver's profileId (if distinct from userId)
     * @param resourceType            e.g. "Shipment" or "Assignment"
     * @param resourceId              target resource ID
     * @throws AccessDeniedException (403) if caller is not the assigned driver
     */
    public static void requireDriverOwnership(AuthPrincipal principal, String assignedDriverId,
                                              String resolvedDriverProfileId, String resourceType, String resourceId) {
        Objects.requireNonNull(principal, "principal must not be null");

        if (!principal.isDriver()) {
            log.warn("Access denied: Caller '{}' with role '{}' is not a DRIVER for {} assignment check",
                    principal.getUserId(), principal.getRole(), resourceType);
            throw new AccessDeniedException("Access denied: Only the assigned driver may perform operations on this " + resourceType);
        }

        String userId = principal.getUserId();
        boolean isAssignedDriver = assignedDriverId != null && (
                assignedDriverId.trim().equalsIgnoreCase(userId.trim()) ||
                        (resolvedDriverProfileId != null && assignedDriverId.trim().equalsIgnoreCase(resolvedDriverProfileId.trim()))
        );

        if (!isAssignedDriver) {
            log.warn("PB-058 [IDOR_BLOCKED]: Driver '{}' (profileId='{}') attempted unauthorized operation on {} '{}' assigned to driver '{}'",
                    userId, resolvedDriverProfileId, resourceType, resourceId, assignedDriverId);
            throw new AccessDeniedException("Access denied: You are not the assigned driver for " + resourceType + " '" + resourceId + "'");
        }
    }

    /**
     * Enforces driver ownership OR Operations access.
     */
    public static void requireDriverOwnershipOrOperations(AuthPrincipal principal, String assignedDriverId,
                                                          String resolvedDriverProfileId, String resourceType, String resourceId) {
        Objects.requireNonNull(principal, "principal must not be null");

        if (principal.isOperations() || principal.isAdmin()) {
            return;
        }

        requireDriverOwnership(principal, assignedDriverId, resolvedDriverProfileId, resourceType, resourceId);
    }

    /**
     * Enforces that a Service caller has the correct audience and required scope.
     *
     * @param principal        authenticated principal
     * @param expectedAudience the current service ID expecting the call (e.g. "order-service")
     * @param requiredScope    the least privilege scope needed (e.g. "order:read")
     * @param action           action description
     */
    public static void requireServiceScope(AuthPrincipal principal, String expectedAudience, String requiredScope, String action) {
        Objects.requireNonNull(principal, "principal must not be null");

        if (!principal.isService()) {
            log.warn("Access denied: User caller '{}' attempted to access service-to-service endpoint: {}",
                    principal.getUserId(), action);
            throw new AccessDeniedException("Access denied: Service identity required for " + action);
        }

        if (expectedAudience != null && !principal.hasAudience(expectedAudience)) {
            log.warn("PB-057/PB-058: Service token audience mismatch: expected '{}' but got {}",
                    expectedAudience, principal.getAudiences());
            throw new AccessDeniedException("Invalid token audience: expected " + expectedAudience);
        }

        if (requiredScope != null && !principal.hasScope(requiredScope) && !principal.hasScope("service:call")) {
            log.warn("PB-057/PB-058: Service '{}' lacks required scope '{}'. Granted scopes: {}",
                    principal.getServiceId(), requiredScope, principal.getScopes());
            throw new AccessDeniedException("Insufficient service permissions: required scope " + requiredScope);
        }
    }

    /**
     * Validates dual-access boundary: Allows either a user with a specific permission/role, or a service identity with an authorized scope.
     *
     * @param principal        authenticated principal
     * @param userPermission   permission required if caller is a human user
     * @param expectedAudience audience required if caller is a service
     * @param serviceScope     scope required if caller is a service
     * @param action           action description
     */
    public static void requireUserPermissionOrServiceScope(AuthPrincipal principal, Permission userPermission,
                                                           String expectedAudience, String serviceScope, String action) {
        Objects.requireNonNull(principal, "principal must not be null");

        if (principal.isService()) {
            requireServiceScope(principal, expectedAudience, serviceScope, action);
        } else {
            requirePermission(principal, userPermission, action);
        }
    }
}

