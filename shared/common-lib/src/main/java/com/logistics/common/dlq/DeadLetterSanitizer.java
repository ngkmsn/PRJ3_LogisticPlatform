package com.logistics.common.dlq;

import java.util.regex.Pattern;

/**
 * Utility to scrub sensitive security credentials (passwords, tokens, API keys, secrets)
 * from payloads, metadata, and error messages before persisting to Dead Letter Queues or logs.
 */
public final class DeadLetterSanitizer {

    private static final Pattern SENSITIVE_KEY_VALUE_PATTERN = Pattern.compile(
            "(\"?(?:password|token|secret|apiKey|api_key|secret_key|authToken|authorization|access_token|client_secret)\"?\\s*[:=]\\s*\"?)([^\"\\s,}]+)(\"?)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern BEARER_TOKEN_PATTERN = Pattern.compile(
            "Bearer\\s+[^\\s,}\"']+",
            Pattern.CASE_INSENSITIVE
    );

    private DeadLetterSanitizer() {
    }

    /**
     * Sanitizes raw JSON or error messages by masking credential values with "[REDACTED]".
     *
     * @param input raw string possibly containing sensitive tokens
     * @return sanitized string safe for logging, audit, and DLQ inspection
     */
    public static String sanitize(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }

        String sanitized = SENSITIVE_KEY_VALUE_PATTERN.matcher(input).replaceAll("$1[REDACTED]$3");
        sanitized = BEARER_TOKEN_PATTERN.matcher(sanitized).replaceAll("Bearer [REDACTED]");
        return sanitized;
    }
}
