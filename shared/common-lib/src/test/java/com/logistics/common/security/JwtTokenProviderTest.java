package com.logistics.common.security;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final long EXPIRATION_MS = 3600000; // 1 hour
    private static final String ISSUER = "logistics-platform";

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(SECRET, EXPIRATION_MS, ISSUER);
    }

    @Test
    void generateToken_containsExpectedClaims() {
        String token = jwtTokenProvider.generateToken("user-123", "john_doe", "john@example.com", "DISPATCHER");

        assertThat(token).isNotBlank();
        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
        assertThat(jwtTokenProvider.extractUserId(token)).isEqualTo("user-123");
        assertThat(jwtTokenProvider.extractUsername(token)).isEqualTo("john_doe");
        assertThat(jwtTokenProvider.extractEmail(token)).isEqualTo("john@example.com");
        assertThat(jwtTokenProvider.extractRole(token)).isEqualTo("DISPATCHER");

        Date exp = jwtTokenProvider.extractExpiration(token);
        assertThat(exp).isAfter(new Date());
    }

    @Test
    void validateToken_returnsFalseForTamperedToken() {
        String token = jwtTokenProvider.generateToken("user-123", "john_doe", "john@example.com", "ADMIN");
        String tamperedToken = token + "corrupted";

        assertThat(jwtTokenProvider.validateToken(tamperedToken)).isFalse();
        assertThatThrownBy(() -> jwtTokenProvider.extractClaims(tamperedToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void validateToken_returnsFalseForExpiredToken() throws InterruptedException {
        JwtTokenProvider shortLivedProvider = new JwtTokenProvider(SECRET, 10L, ISSUER);
        String token = shortLivedProvider.generateToken("user-exp", "expired_user", "exp@example.com", "CUSTOMER");

        Thread.sleep(50L);

        assertThat(shortLivedProvider.validateToken(token)).isFalse();
    }

    @Test
    void validateToken_returnsFalseForNullOrBlank() {
        assertThat(jwtTokenProvider.validateToken(null)).isFalse();
        assertThat(jwtTokenProvider.validateToken("")).isFalse();
        assertThat(jwtTokenProvider.validateToken("   ")).isFalse();
    }

    @Test
    void constructor_shortSecretIsHashedSecurely() {
        JwtTokenProvider shortSecretProvider = new JwtTokenProvider("short-secret", EXPIRATION_MS, ISSUER);
        String token = shortSecretProvider.generateToken("u1", "alice", "alice@example.com", "ADMIN");

        assertThat(token).isNotBlank();
        assertThat(shortSecretProvider.validateToken(token)).isTrue();
    }
}
