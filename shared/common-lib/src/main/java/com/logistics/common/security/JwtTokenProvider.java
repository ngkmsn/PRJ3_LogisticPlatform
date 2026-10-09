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
import java.util.Date;
import java.util.Map;

/**
 * Utility and provider class for creating, parsing, and validating JSON Web Tokens (JWT).
 * <p>
 * Uses HMAC-SHA256 for secure digital signatures. Supports custom claims such as
 * {@code userId}, {@code username}, {@code email}, and {@code role}.
 */
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    private final SecretKey signingKey;
    private final long expirationMs;
    private final String issuer;

    public JwtTokenProvider(String secret, long expirationMs, String issuer) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("JWT secret must not be null or blank");
        }
        this.signingKey = createSigningKey(secret);
        this.expirationMs = expirationMs;
        this.issuer = (issuer != null && !issuer.isBlank()) ? issuer : "logistics-platform";
    }

    /**
     * Generates a signed JWT with standard identity claims.
     *
     * @param userId   the unique ID of the user (also set as subject)
     * @param username the username of the user
     * @param email    the email of the user
     * @param role     the role of the user (e.g. ADMIN, DISPATCHER, DRIVER, CUSTOMER)
     * @return the serialized JWT string
     */
    public String generateToken(String userId, String username, String email, String role) {
        return generateToken(userId, username, email, role, Map.of());
    }

    /**
     * Generates a signed JWT with standard identity claims and additional custom claims.
     *
     * @param userId          the unique ID of the user
     * @param username        the username of the user
     * @param email           the email of the user
     * @param role            the role of the user
     * @param additionalClaims additional claims map
     * @return the serialized JWT string
     */
    public String generateToken(String userId, String username, String email, String role, Map<String, Object> additionalClaims) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);

        var builder = Jwts.builder()
                .subject(userId)
                .issuer(issuer)
                .issuedAt(now)
                .expiration(expiryDate)
                .claim("userId", userId)
                .claim("username", username)
                .claim("email", email)
                .claim("role", role);

        if (additionalClaims != null && !additionalClaims.isEmpty()) {
            additionalClaims.forEach(builder::claim);
        }

        return builder.signWith(signingKey).compact();
    }

    /**
     * Validates whether the given token has a valid signature and is not expired.
     *
     * @param token JWT string
     * @return true if valid, false otherwise
     */
    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT token is expired: {}", e.getMessage());
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Parses the claims payload from a signed JWT.
     *
     * @param token JWT string
     * @return Claims payload
     * @throws JwtException if token is invalid or expired
     */
    public Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUserId(String token) {
        return extractClaims(token).get("userId", String.class);
    }

    public String extractUsername(String token) {
        return extractClaims(token).get("username", String.class);
    }

    public String extractEmail(String token) {
        return extractClaims(token).get("email", String.class);
    }

    public String extractRole(String token) {
        return extractClaims(token).get("role", String.class);
    }

    public Date extractExpiration(String token) {
        return extractClaims(token).getExpiration();
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    public String getIssuer() {
        return issuer;
    }

    /**
     * Derives a 256-bit HMAC SHA key from the provided secret string.
     * Ensures minimum 256-bit entropy by computing SHA-256 hash if byte length &lt; 32.
     */
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
