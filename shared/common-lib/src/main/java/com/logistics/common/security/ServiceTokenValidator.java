package com.logistics.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ServiceTokenValidator {

    private static final Logger log = LoggerFactory.getLogger(ServiceTokenValidator.class);

    private final String expectedAudience;
    private final SecretKey primarySigningKey;
    private final SecretKey secondarySigningKey; // Optional key for zero-downtime secret rotation
    private final String expectedIssuer;

    public ServiceTokenValidator(String expectedAudience, String primarySecret, String secondarySecret, String expectedIssuer) {
        if (expectedAudience == null || expectedAudience.isBlank()) {
            throw new IllegalArgumentException("expectedAudience must not be null or blank");
        }
        if (primarySecret == null || primarySecret.isBlank()) {
            throw new IllegalArgumentException("primarySecret must not be null or blank");
        }
        this.expectedAudience = expectedAudience.trim();
        this.primarySigningKey = createSigningKey(primarySecret);
        this.secondarySigningKey = (secondarySecret != null && !secondarySecret.isBlank()) ? createSigningKey(secondarySecret) : null;
        this.expectedIssuer = (expectedIssuer != null && !expectedIssuer.isBlank()) ? expectedIssuer.trim() : "logistics-platform";
    }

    public ServiceTokenValidator(String expectedAudience, String primarySecret, String expectedIssuer) {
        this(expectedAudience, primarySecret, null, expectedIssuer);
    }

    public ServiceTokenValidationResult validate(String rawToken, String requiredScope) {
        if (rawToken == null || rawToken.isBlank()) {
            return ServiceTokenValidationResult.failure(
                    ServiceTokenValidationResult.Status.MISSING_TOKEN,
                    "Missing Authorization token"
            );
        }

        String token = rawToken.trim();
        if (token.startsWith("Bearer ")) {
            token = token.substring(7).trim();
        }

        Claims claims;
        try {
            claims = parseClaims(token, primarySigningKey);
        } catch (ExpiredJwtException e) {
            log.warn("PB-057: Service token is expired: {}", e.getMessage());
            return ServiceTokenValidationResult.failure(
                    ServiceTokenValidationResult.Status.EXPIRED_TOKEN,
                    "Service token has expired"
            );
        } catch (JwtException e) {
            // If primary key failed, try secondary rotation key if configured
            if (secondarySigningKey != null) {
                try {
                    claims = parseClaims(token, secondarySigningKey);
                    log.info("PB-057: Service token verified with secondary rotation key");
                } catch (ExpiredJwtException ex) {
                    log.warn("PB-057: Service token is expired (rotation key): {}", ex.getMessage());
                    return ServiceTokenValidationResult.failure(
                            ServiceTokenValidationResult.Status.EXPIRED_TOKEN,
                            "Service token has expired"
                    );
                } catch (JwtException ex) {
                    log.warn("PB-057: Service token validation failed with both keys: {}", ex.getMessage());
                    return ServiceTokenValidationResult.failure(
                            ServiceTokenValidationResult.Status.INVALID_SIGNATURE,
                            "Invalid service token signature or format"
                    );
                }
            } else {
                log.warn("PB-057: Invalid service token signature: {}", e.getMessage());
                return ServiceTokenValidationResult.failure(
                        ServiceTokenValidationResult.Status.INVALID_SIGNATURE,
                        "Invalid service token signature or format"
                );
            }
        }

        // 1. Validate Issuer
        String issuer = claims.getIssuer();
        if (expectedIssuer != null && !expectedIssuer.equalsIgnoreCase(issuer)) {
            log.warn("PB-057: Token issuer mismatch. Expected '{}', got '{}'", expectedIssuer, issuer);
            return ServiceTokenValidationResult.failure(
                    ServiceTokenValidationResult.Status.INVALID_ISSUER,
                    "Invalid token issuer: expected " + expectedIssuer + " but was " + issuer
            );
        }

        // 2. Validate Audience
        Set<String> tokenAudiences = claims.getAudience();
        if (tokenAudiences == null || !tokenAudiences.contains(expectedAudience)) {
            log.warn("PB-057: Token audience mismatch. Expected '{}', token contains {}", expectedAudience, tokenAudiences);
            return ServiceTokenValidationResult.failure(
                    ServiceTokenValidationResult.Status.AUDIENCE_MISMATCH,
                    "Invalid token audience: expected '" + expectedAudience + "' but found " + tokenAudiences
            );
        }

        // 3. Extract Service Identity & Scopes
        String serviceId = claims.get(ServiceTokenClaims.CLAIM_SERVICE_ID, String.class);
        if (serviceId == null || serviceId.isBlank()) {
            serviceId = claims.getSubject();
        }
        if (serviceId == null || serviceId.isBlank()) {
            return ServiceTokenValidationResult.failure(
                    ServiceTokenValidationResult.Status.INVALID_IDENTITY,
                    "Token does not contain valid service identity"
            );
        }

        Set<String> scopes = extractScopes(claims);
        Instant issuedAt = claims.getIssuedAt() != null ? claims.getIssuedAt().toInstant() : Instant.now();
        Instant expiresAt = claims.getExpiration() != null ? claims.getExpiration().toInstant() : Instant.now();

        ServicePrincipal principal = new ServicePrincipal(serviceId, tokenAudiences, scopes, issuedAt, expiresAt);

        // 4. Validate Scope / Least Privilege
        if (requiredScope != null && !requiredScope.isBlank()) {
            if (!principal.hasScope(requiredScope)) {
                log.warn("PB-057: Service '{}' lacks required scope '{}'. Granted scopes: {}",
                        serviceId, requiredScope, scopes);
                return ServiceTokenValidationResult.failure(
                        ServiceTokenValidationResult.Status.INSUFFICIENT_SCOPE,
                        "Insufficient service permissions: required scope '" + requiredScope + "'"
                );
            }
        }

        return ServiceTokenValidationResult.success(principal);
    }

    public ServiceTokenValidationResult validate(String rawToken) {
        return validate(rawToken, null);
    }

    public String getExpectedAudience() {
        return expectedAudience;
    }

    public String getExpectedIssuer() {
        return expectedIssuer;
    }

    private Claims parseClaims(String token, SecretKey key) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    @SuppressWarnings("unchecked")
    private Set<String> extractScopes(Claims claims) {
        Object rawScopes = claims.get(ServiceTokenClaims.CLAIM_SCOPES);
        if (rawScopes instanceof Collection<?>) {
            Set<String> set = new HashSet<>();
            for (Object item : (Collection<?>) rawScopes) {
                if (item != null) {
                    set.add(item.toString());
                }
            }
            return set;
        } else if (rawScopes instanceof String s) {
            return Set.of(s.split("[,\\s]+"));
        }
        return Collections.emptySet();
    }

    private static SecretKey createSigningKey(String secret) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                keyBytes = digest.digest(keyBytes);
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-256 algorithm not available", e);
            }
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }
}

