package com.logistics.common.retry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorClassifierTest {

    @Test
    @DisplayName("PB-052: Transient I/O and connection exceptions are retryable")
    void transientErrors_areRetryable() {
        assertThat(ErrorClassifier.isRetryable(new ConnectException("Connection refused"))).isTrue();
        assertThat(ErrorClassifier.isRetryable(new SocketTimeoutException("Read timed out"))).isTrue();
        assertThat(ErrorClassifier.isRetryable(new HttpTimeoutException("Request timed out"))).isTrue();
        assertThat(ErrorClassifier.isRetryable(new IOException("Broken pipe"))).isTrue();

        // Nested transient cause inside RuntimeException
        RuntimeException wrapped = new RuntimeException("Outer wrapper", new ConnectException("Underlying connect failed"));
        assertThat(ErrorClassifier.isRetryable(wrapped)).isTrue();
    }

    @Test
    @DisplayName("PB-052: Client, syntax, and validation exceptions are strictly non-retryable")
    void clientErrors_areNonRetryable() {
        assertThat(ErrorClassifier.isRetryable(new IllegalArgumentException("Invalid parameter"))).isFalse();
        assertThat(ErrorClassifier.isRetryable(new NullPointerException("Missing field"))).isFalse();
        assertThat(ErrorClassifier.isRetryable(new IndexOutOfBoundsException())).isFalse();
        assertThat(ErrorClassifier.isRetryable(null)).isFalse();

        // Nested validation error inside a generic exception
        Exception wrapped = new Exception("Processing error", new IllegalArgumentException("Bad input"));
        assertThat(ErrorClassifier.isRetryable(wrapped)).isFalse();
    }

    @Test
    @DisplayName("PB-052: HTTP status codes retryable vs non-retryable classification")
    void httpStatusCodeClassification() {
        // Retryable: 408, 429, 500, 502, 503, 504
        assertThat(ErrorClassifier.isRetryableHttpStatus(408)).isTrue();
        assertThat(ErrorClassifier.isRetryableHttpStatus(429)).isTrue();
        assertThat(ErrorClassifier.isRetryableHttpStatus(500)).isTrue();
        assertThat(ErrorClassifier.isRetryableHttpStatus(502)).isTrue();
        assertThat(ErrorClassifier.isRetryableHttpStatus(503)).isTrue();
        assertThat(ErrorClassifier.isRetryableHttpStatus(504)).isTrue();

        // Non-retryable: 200, 201, 204, 400, 401, 403, 404, 409, 422
        assertThat(ErrorClassifier.isRetryableHttpStatus(200)).isFalse();
        assertThat(ErrorClassifier.isRetryableHttpStatus(201)).isFalse();
        assertThat(ErrorClassifier.isRetryableHttpStatus(400)).isFalse();
        assertThat(ErrorClassifier.isRetryableHttpStatus(401)).isFalse();
        assertThat(ErrorClassifier.isRetryableHttpStatus(403)).isFalse();
        assertThat(ErrorClassifier.isRetryableHttpStatus(404)).isFalse();
        assertThat(ErrorClassifier.isRetryableHttpStatus(409)).isFalse();
        assertThat(ErrorClassifier.isRetryableHttpStatus(422)).isFalse();
    }
}
