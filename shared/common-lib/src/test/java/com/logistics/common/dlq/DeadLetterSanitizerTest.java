package com.logistics.common.dlq;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeadLetterSanitizerTest {

    @Test
    @DisplayName("PB-053: Sanitizes passwords, API keys, tokens from raw strings")
    void sanitizesSensitiveFields() {
        String raw = "{\"username\":\"admin\",\"password\":\"SecretPass123!\",\"apiKey\":\"key-xyz-999\"}";
        String sanitized = DeadLetterSanitizer.sanitize(raw);

        assertThat(sanitized).doesNotContain("SecretPass123!");
        assertThat(sanitized).doesNotContain("key-xyz-999");
        assertThat(sanitized).contains("\"password\":\"[REDACTED]\"");
        assertThat(sanitized).contains("\"apiKey\":\"[REDACTED]\"");
    }

    @Test
    @DisplayName("PB-053: Sanitizes Bearer authorization tokens")
    void sanitizesBearerTokens() {
        String raw = "Error invoking external API: Authorization header was Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.abc.def";
        String sanitized = DeadLetterSanitizer.sanitize(raw);

        assertThat(sanitized).doesNotContain("eyJhbGci");
        assertThat(sanitized).contains("Bearer [REDACTED]");
    }

    @Test
    @DisplayName("PB-053: Null or blank input safely handled")
    void nullOrBlankSafe() {
        assertThat(DeadLetterSanitizer.sanitize(null)).isNull();
        assertThat(DeadLetterSanitizer.sanitize("")).isEmpty();
        assertThat(DeadLetterSanitizer.sanitize("   ")).isEqualTo("   ");
    }
}
