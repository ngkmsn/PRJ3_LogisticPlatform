package com.logistics.notification.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class DefaultNotificationPlatformClientTest {

    private HttpClient mockHttpClient;
    private HttpResponse<String> mockResponse;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockHttpClient = mock(HttpClient.class);
        mockResponse = mock(HttpResponse.class);
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("PB-051: Successfully sends notification and parses 202 Accepted response with UUID")
    void testSendNotification_success() throws Exception {
        UUID generatedId = UUID.randomUUID();
        String responseJson = "{\"id\":\"" + generatedId + "\",\"status\":\"QUEUED\"}";

        when(mockResponse.statusCode()).thenReturn(202);
        when(mockResponse.body()).thenReturn(responseJson);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        DefaultNotificationPlatformClient client = new DefaultNotificationPlatformClient(
                "http://localhost:8085",
                "/api/v1/notifications",
                Optional.of("test-api-key"),
                Optional.of("test-jwt-token"),
                3, 5, 3, 10,
                objectMapper,
                mockHttpClient
        );

        NotificationPlatformRequest request = new NotificationPlatformRequest(
                "0988776655", "SMS", "Don hang da giao", "Noi dung giao hang", "NORMAL"
        );

        NotificationPlatformResult result = client.sendNotification(request, "evt-12345");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getStatusCode()).isEqualTo(202);
        assertThat(result.getPlatformNotificationId()).isEqualTo(generatedId);
        assertThat(result.getStatus()).isEqualTo("QUEUED");
        assertThat(result.getAttempts()).isEqualTo(1);

        // Verify request headers
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockHttpClient, times(1)).send(captor.capture(), any());
        HttpRequest sentReq = captor.getValue();
        assertThat(sentReq.headers().firstValue("X-Correlation-Id")).contains("evt-12345");
        assertThat(sentReq.headers().firstValue("X-Idempotency-Key")).contains("evt-12345");
        assertThat(sentReq.headers().firstValue("X-API-Key")).contains("test-api-key");
        assertThat(sentReq.headers().firstValue("Authorization")).contains("Bearer test-jwt-token");
    }

    @Test
    @DisplayName("PB-051: Handles 503 Service Unavailable with Retry-After and retries up to maxRetries")
    void testSendNotification_503Retry() throws Exception {
        when(mockResponse.statusCode()).thenReturn(503);
        when(mockResponse.body()).thenReturn("{\"error\":\"Service Unavailable\",\"status\":503,\"retryAfterSeconds\":1}");
        HttpHeaders headers = HttpHeaders.of(Map.of("Retry-After", List.of("1")), (k, v) -> true);
        when(mockResponse.headers()).thenReturn(headers);

        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        DefaultNotificationPlatformClient client = new DefaultNotificationPlatformClient(
                "http://localhost:8085",
                "/api/v1/notifications",
                Optional.empty(),
                Optional.empty(),
                3, 5, 2, 10,
                objectMapper,
                mockHttpClient
        );

        NotificationPlatformRequest request = new NotificationPlatformRequest(
                "0988776655", "SMS", "Subject", "Content", "HIGH"
        );

        NotificationPlatformResult result = client.sendNotification(request, "evt-503");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getStatusCode()).isEqualTo(503);
        assertThat(result.getAttempts()).isEqualTo(2);
        assertThat(result.getRetryAfterSeconds()).isEqualTo(1L);
        assertThat(result.getErrorMessage()).contains("503");
        verify(mockHttpClient, times(2)).send(any(), any());
    }

    @Test
    @DisplayName("PB-051: Client errors (400 Bad Request) do not retry and fail-fast")
    void testSendNotification_400BadRequest_failsFast() throws Exception {
        when(mockResponse.statusCode()).thenReturn(400);
        when(mockResponse.body()).thenReturn("{\"error\":\"recipient must not be blank\"}");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        DefaultNotificationPlatformClient client = new DefaultNotificationPlatformClient(
                "http://localhost:8085",
                "/api/v1/notifications",
                Optional.empty(),
                Optional.empty(),
                3, 5, 3, 10,
                objectMapper,
                mockHttpClient
        );

        NotificationPlatformRequest request = new NotificationPlatformRequest(
                "", "SMS", "Subject", "Content", "NORMAL"
        );

        NotificationPlatformResult result = client.sendNotification(request, "evt-400");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getStatusCode()).isEqualTo(400);
        assertThat(result.getAttempts()).isEqualTo(1); // Fail-fast: only 1 attempt
        assertThat(result.getErrorMessage()).contains("recipient must not be blank");
        verify(mockHttpClient, times(1)).send(any(), any());
    }

    @Test
    @DisplayName("PB-051: Connection timeout retries and returns descriptive failure without crashing")
    void testSendNotification_connectionTimeout() throws Exception {
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new HttpTimeoutException("Connection timed out after 5000ms"));

        DefaultNotificationPlatformClient client = new DefaultNotificationPlatformClient(
                "http://localhost:8085",
                "/api/v1/notifications",
                Optional.empty(),
                Optional.empty(),
                3, 5, 2, 10,
                objectMapper,
                mockHttpClient
        );

        NotificationPlatformRequest request = new NotificationPlatformRequest(
                "0988776655", "SMS", "Subject", "Content", "NORMAL"
        );

        NotificationPlatformResult result = client.sendNotification(request, "evt-timeout");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getStatusCode()).isEqualTo(408);
        assertThat(result.getAttempts()).isEqualTo(2);
        assertThat(result.getErrorMessage()).contains("timeout");
    }

    @Test
    @DisplayName("PB-051: Connection refused (network failure) retries and returns fail-safe result")
    void testSendNotification_networkFailure() throws Exception {
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Connection refused"));

        DefaultNotificationPlatformClient client = new DefaultNotificationPlatformClient(
                "http://localhost:8085",
                "/api/v1/notifications",
                Optional.empty(),
                Optional.empty(),
                3, 5, 2, 10,
                objectMapper,
                mockHttpClient
        );

        NotificationPlatformRequest request = new NotificationPlatformRequest(
                "0988776655", "SMS", "Subject", "Content", "NORMAL"
        );

        NotificationPlatformResult result = client.sendNotification(request, "evt-conn-refused");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getStatusCode()).isEqualTo(503);
        assertThat(result.getAttempts()).isEqualTo(2);
        assertThat(result.getErrorMessage()).contains("Connection refused");
    }
}
