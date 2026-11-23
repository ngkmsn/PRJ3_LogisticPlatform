package com.logistics.common.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceToServiceAuthTest {

    private static final String PRIMARY_SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String SECONDARY_SECRET = "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210";
    private static final String ISSUER = "logistics-platform";

    private ServiceTokenProvider shipmentServiceProvider;
    private ServiceTokenValidator userTokenValidator;

    @BeforeEach
    void setUp() {
        shipmentServiceProvider = new ServiceTokenProvider("shipment-service", PRIMARY_SECRET, ISSUER, 300L);
        userTokenValidator = new ServiceTokenValidator("user-service", PRIMARY_SECRET, SECONDARY_SECRET, ISSUER);
    }

    @Test
    @DisplayName("Valid service token generation and validation succeeds with matching audience and scope")
    void validServiceToken_success() {
        String token = shipmentServiceProvider.generateToken("user-service", Set.of("driver:read", "driver:metrics"));

        assertThat(token).isNotBlank();

        ServiceTokenValidationResult result = userTokenValidator.validate(token, "driver:read");
        assertThat(result.isValid()).isTrue();
        assertThat(result.getStatus()).isEqualTo(ServiceTokenValidationResult.Status.SUCCESS);

        ServicePrincipal principal = result.getPrincipal();
        assertThat(principal).isNotNull();
        assertThat(principal.getServiceId()).isEqualTo("shipment-service");
        assertThat(principal.getAudiences()).contains("user-service");
        assertThat(principal.getScopes()).containsExactlyInAnyOrder("driver:read", "driver:metrics");
        assertThat(principal.hasScope("driver:read")).isTrue();
        assertThat(principal.hasScope("driver:metrics")).isTrue();
        assertThat(principal.hasScope("driver:write")).isFalse();
    }

    @Test
    @DisplayName("Missing Authorization token returns MISSING_TOKEN")
    void missingToken_rejected() {
        ServiceTokenValidationResult resultNull = userTokenValidator.validate(null);
        assertThat(resultNull.isValid()).isFalse();
        assertThat(resultNull.getStatus()).isEqualTo(ServiceTokenValidationResult.Status.MISSING_TOKEN);

        ServiceTokenValidationResult resultBlank = userTokenValidator.validate("   ");
        assertThat(resultBlank.isValid()).isFalse();
        assertThat(resultBlank.getStatus()).isEqualTo(ServiceTokenValidationResult.Status.MISSING_TOKEN);
    }

    @Test
    @DisplayName("Tampered token or invalid signature returns INVALID_SIGNATURE")
    void invalidSignature_rejected() {
        String token = shipmentServiceProvider.generateToken("user-service", Set.of("driver:read"));
        String tampered = token.substring(0, token.length() - 5) + "ABCDE";

        ServiceTokenValidationResult result = userTokenValidator.validate(tampered);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getStatus()).isEqualTo(ServiceTokenValidationResult.Status.INVALID_SIGNATURE);
    }

    @Test
    @DisplayName("Expired token returns EXPIRED_TOKEN")
    void expiredToken_rejected() throws InterruptedException {
        // Provider with 1 second TTL
        ServiceTokenProvider shortLivedProvider = new ServiceTokenProvider("shipment-service", PRIMARY_SECRET, ISSUER, 1L);
        String token = shortLivedProvider.generateToken("user-service", Set.of("driver:read"), 1L);

        Thread.sleep(1200L); // wait for token to expire

        ServiceTokenValidationResult result = userTokenValidator.validate(token);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getStatus()).isEqualTo(ServiceTokenValidationResult.Status.EXPIRED_TOKEN);
    }

    @Test
    @DisplayName("Audience mismatch returns AUDIENCE_MISMATCH when token target is another service")
    void audienceMismatch_rejected() {
        // Token minted for order-service, sent to user-service
        String tokenForOrderService = shipmentServiceProvider.generateToken("order-service", Set.of("order:metrics:read"));

        ServiceTokenValidationResult result = userTokenValidator.validate(tokenForOrderService);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getStatus()).isEqualTo(ServiceTokenValidationResult.Status.AUDIENCE_MISMATCH);
        assertThat(result.getMessage()).contains("expected 'user-service'");
    }

    @Test
    @DisplayName("Issuer mismatch returns INVALID_ISSUER")
    void issuerMismatch_rejected() {
        ServiceTokenProvider foreignProvider = new ServiceTokenProvider("shipment-service", PRIMARY_SECRET, "foreign-system", 300L);
        String foreignToken = foreignProvider.generateToken("user-service", Set.of("driver:read"));

        ServiceTokenValidationResult result = userTokenValidator.validate(foreignToken);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getStatus()).isEqualTo(ServiceTokenValidationResult.Status.INVALID_ISSUER);
    }

    @Test
    @DisplayName("Insufficient scope returns INSUFFICIENT_SCOPE when caller lacks required permission")
    void insufficientScope_rejected() {
        String tokenWithoutWrite = shipmentServiceProvider.generateToken("user-service", Set.of("driver:read"));

        ServiceTokenValidationResult result = userTokenValidator.validate(tokenWithoutWrite, "driver:write");
        assertThat(result.isValid()).isFalse();
        assertThat(result.getStatus()).isEqualTo(ServiceTokenValidationResult.Status.INSUFFICIENT_SCOPE);
        assertThat(result.getMessage()).contains("driver:write");
    }

    @Test
    @DisplayName("Wildcard scope '*' grants access to all required scopes")
    void wildcardScope_allowed() {
        String adminToken = shipmentServiceProvider.generateToken("user-service", Set.of("*"));

        ServiceTokenValidationResult result = userTokenValidator.validate(adminToken, "driver:write");
        assertThat(result.isValid()).isTrue();
        assertThat(result.getStatus()).isEqualTo(ServiceTokenValidationResult.Status.SUCCESS);
    }

    @Test
    @DisplayName("Zero-downtime secret rotation: token signed with secondary key validates successfully")
    void secondaryKeyRotation_success() {
        // Provider signing with secondary (rotated) key
        ServiceTokenProvider rotatedProvider = new ServiceTokenProvider("shipment-service", SECONDARY_SECRET, ISSUER, 300L);
        String token = rotatedProvider.generateToken("user-service", Set.of("driver:read"));

        // Validator has primary + secondary; should fall back to secondary key and succeed
        ServiceTokenValidationResult result = userTokenValidator.validate(token, "driver:read");
        assertThat(result.isValid()).isTrue();
        assertThat(result.getStatus()).isEqualTo(ServiceTokenValidationResult.Status.SUCCESS);
        assertThat(result.getPrincipal().getServiceId()).isEqualTo("shipment-service");
    }

    @Test
    @DisplayName("ServiceTokenManager caches token and reuses it on subsequent requests")
    void serviceTokenManager_cachesAndReusesToken() {
        ServiceTokenManager manager = new ServiceTokenManager("shipment-service", PRIMARY_SECRET, ISSUER, 300L, 30L);

        String token1 = manager.getServiceToken("order-service", Set.of("order:metrics:read"));
        String token2 = manager.getServiceToken("order-service", Set.of("order:metrics:read"));

        assertThat(token1).isEqualTo(token2);
        assertThat(manager.getCacheSize()).isEqualTo(1);

        // Different audience creates distinct cached entry
        String tokenUser = manager.getServiceToken("user-service", Set.of("driver:read"));
        assertThat(tokenUser).isNotEqualTo(token1);
        assertThat(manager.getCacheSize()).isEqualTo(2);

        // Invalidation clears specific audience
        manager.invalidate("order-service");
        String token1Renewed = manager.getServiceToken("order-service", Set.of("order:metrics:read"));
        assertThat(token1Renewed).isNotBlank();
    }

    @Test
    @DisplayName("ServiceTokenProvider.maskToken redacts credentials to prevent leakage in logs")
    void maskToken_redactsCredentials() {
        String token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.abcdef123456";

        String masked = ServiceTokenProvider.maskToken(token);
        assertThat(masked).startsWith("eyJhbGci...");
        assertThat(masked).endsWith("***");
        assertThat(masked).doesNotContain("abcdef123456");

        String maskedBearer = ServiceTokenProvider.maskToken("Bearer " + token);
        assertThat(maskedBearer).startsWith("eyJhbGci...");
        assertThat(maskedBearer).endsWith("***");

        assertThat(ServiceTokenProvider.maskToken(null)).isEqualTo("[EMPTY]");
        assertThat(ServiceTokenProvider.maskToken("")).isEqualTo("[EMPTY]");
        assertThat(ServiceTokenProvider.maskToken("short")).isEqualTo("***");
    }

    @Test
    @DisplayName("AuthPrincipal.fromJwtToken parses service token with Role.SERVICE and scopes")
    void authPrincipal_parsesServiceToken() {
        JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(PRIMARY_SECRET, 3600000L, ISSUER);
        String serviceToken = shipmentServiceProvider.generateToken("user-service", Set.of("driver:read", "driver:metrics"));

        AuthPrincipal principal = AuthPrincipal.fromJwtToken(serviceToken, jwtTokenProvider);

        assertThat(principal).isNotNull();
        assertThat(principal.isService()).isTrue();
        assertThat(principal.getRole()).isEqualTo(Role.SERVICE);
        assertThat(principal.getUserId()).isEqualTo("shipment-service");
        assertThat(principal.getServiceId()).isEqualTo("shipment-service");
        assertThat(principal.getScopes()).contains("driver:read", "driver:metrics");
        assertThat(principal.hasScope("driver:read")).isTrue();
        assertThat(principal.hasAudience("user-service")).isTrue();
    }
}

