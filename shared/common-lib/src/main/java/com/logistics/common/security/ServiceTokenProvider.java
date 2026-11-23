package com.logistics.common.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class ServiceTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(ServiceTokenProvider.class);

    private final String serviceId;
    private final SecretKey signingKey;
    private final String issuer;
    private final long defaultTtlSeconds;

    public ServiceTokenProvider(String serviceId, String secret, String issuer, long defaultTtlSeconds) {
        if (serviceId == null || serviceId.isBlank()) {
            throw new IllegalArgumentException("serviceId must not be null or blank");
        }
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("secret must not be null or blank");
        }
        this.serviceId = serviceId.trim();
        this.signingKey = createSigningKey(secret);
        this.issuer = (issuer != null && !issuer.isBlank()) ? issuer.trim() : "logistics-platform";
        this.defaultTtlSeconds = defaultTtlSeconds > 0 ? defaultTtlSeconds : 300L;
    }

    public ServiceTokenProvider(String serviceId, String secret, String issuer) {
        this(serviceId, secret, issuer, 300L);
    }

    public String generateToken(String targetAudience, Set<String> scopes) {
        return generateToken(targetAudience, scopes, defaultTtlSeconds);
    }

    public String generateToken(String targetAudience, Set<String> scopes, long ttlSeconds) {
        if (targetAudience == null || targetAudience.isBlank()) {
            throw new IllegalArgumentException("targetAudience must not be null or blank");
        }
        long ttl = ttlSeconds > 0 ? ttlSeconds : defaultTtlSeconds;
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + (ttl * 1000L));

        var builder = Jwts.builder()
                .subject(serviceId)
                .issuer(issuer)
                .audience().add(targetAudience.trim()).and()
                .claim(ServiceTokenClaims.CLAIM_SERVICE_ID, serviceId)
                .claim("userId", serviceId)
                .claim("username", serviceId)
                .claim("email", serviceId + "@service.local")
                .claim(ServiceTokenClaims.CLAIM_ROLE, ServiceTokenClaims.ROLE_SERVICE)
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(expiryDate);

        if (scopes != null && !scopes.isEmpty()) {
            builder.claim(ServiceTokenClaims.CLAIM_SCOPES, List.copyOf(scopes));
        } else {
            builder.claim(ServiceTokenClaims.CLAIM_SCOPES, List.of("service:call"));
        }

        return builder.signWith(signingKey).compact();
    }

    public String getServiceId() {
        return serviceId;
    }

    public String getIssuer() {
        return issuer;
    }

    public long getDefaultTtlSeconds() {
        return defaultTtlSeconds;
    }

    public static String maskToken(String token) {
        if (token == null || token.isBlank()) {
            return "[EMPTY]";
        }
        String trimmed = token.trim();
        if (trimmed.startsWith("Bearer ")) {
            trimmed = trimmed.substring(7).trim();
        }
        if (trimmed.length() <= 12) {
            return "***";
        }
        return trimmed.substring(0, 8) + "...***";
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
