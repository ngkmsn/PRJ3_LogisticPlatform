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

    @Test
    @DisplayName("PB-054: Circuit breaker trips OPEN after failureThreshold consecutive failures")
    void testCircuitBreaker_tripsOpenAfterFailures() throws Exception {
        when(mockResponse.statusCode()).thenReturn(503);
        when(mockResponse.body()).thenReturn("{\"error\":\"Service Unavailable\"}");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        DefaultNotificationPlatformClient client = new DefaultNotificationPlatformClient(
                "http://localhost:8085", "/api/v1/notifications",
                Optional.empty(), Optional.empty(),
                3, 5, 1, 10,
                objectMapper, mockHttpClient
        );

        com.logistics.common.circuitbreaker.CircuitBreaker cb = new com.logistics.common.circuitbreaker.CircuitBreaker(
                "test-cb",
                com.logistics.common.circuitbreaker.CircuitBreakerConfig.builder()
                        .failureThreshold(2)
                        .openDuration(java.time.Duration.ofMillis(100))
                        .successThreshold(2)
                        .trialCalls(2)
                        .build()
        );
        client.setCircuitBreaker(cb);

        NotificationPlatformRequest request = new NotificationPlatformRequest("0988776655", "SMS", "Sub", "Content", "NORMAL");

        // Call 1: fails -> failures = 1
        client.sendNotification(request, "evt-1");
        assertThat(cb.isClosed()).isTrue();

        // Call 2: fails -> failures = 2 -> trips to OPEN
        client.sendNotification(request, "evt-2");
        assertThat(cb.isOpen()).isTrue();
    }

    @Test
    @DisplayName("PB-054: When circuit is OPEN, calls are short-circuited immediately without HTTP calls")
    void testCircuitBreaker_shortCircuitsWhenOpen() throws Exception {
        DefaultNotificationPlatformClient client = new DefaultNotificationPlatformClient(
                "http://localhost:8085", "/api/v1/notifications",
                Optional.empty(), Optional.empty(),
                3, 5, 1, 10,
                objectMapper, mockHttpClient
        );

        com.logistics.common.circuitbreaker.CircuitBreaker cb = new com.logistics.common.circuitbreaker.CircuitBreaker(
                "test-short-circuit",
                com.logistics.common.circuitbreaker.CircuitBreakerConfig.builder()
                        .failureThreshold(1)
                        .openDuration(java.time.Duration.ofMillis(1000))
                        .build()
        );
        cb.recordFailure(new IOException("Downstream down"));
        assertThat(cb.isOpen()).isTrue();

        client.setCircuitBreaker(cb);

        NotificationPlatformRequest request = new NotificationPlatformRequest("0988776655", "SMS", "Sub", "Content", "NORMAL");
        NotificationPlatformResult result = client.sendNotification(request, "evt-fast-fail");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.isCircuitOpen()).isTrue();
        assertThat(result.getAttempts()).isZero();
        assertThat(result.getErrorMessage()).contains("is OPEN");

        // Verify NO HTTP requests were sent
        verify(mockHttpClient, never()).send(any(), any());
    }

    @Test
    @DisplayName("PB-054: Client errors (400 Bad Request) do NOT trip the circuit breaker")
    void testCircuitBreaker_clientErrorsDoNotTripCircuit() throws Exception {
        when(mockResponse.statusCode()).thenReturn(400);
        when(mockResponse.body()).thenReturn("{\"error\":\"invalid payload\"}");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        DefaultNotificationPlatformClient client = new DefaultNotificationPlatformClient(
                "http://localhost:8085", "/api/v1/notifications",
                Optional.empty(), Optional.empty(),
                3, 5, 1, 10,
                objectMapper, mockHttpClient
        );

        com.logistics.common.circuitbreaker.CircuitBreaker cb = new com.logistics.common.circuitbreaker.CircuitBreaker(
                "test-client-errors",
                com.logistics.common.circuitbreaker.CircuitBreakerConfig.builder()
                        .failureThreshold(2)
                        .build()
        );
        client.setCircuitBreaker(cb);

        NotificationPlatformRequest request = new NotificationPlatformRequest("0988776655", "SMS", "Sub", "Content", "NORMAL");

        // 3 consecutive 400 Bad Request responses
        client.sendNotification(request, "evt-400-1");
        client.sendNotification(request, "evt-400-2");
        client.sendNotification(request, "evt-400-3");

        // Circuit must remain CLOSED
        assertThat(cb.isClosed()).isTrue();
        assertThat(cb.getMetrics().getCircuitOpenCount()).isZero();
    }

    @Test
    @DisplayName("PB-054: Recovers from HALF_OPEN to CLOSED when downstream becomes healthy")
    void testCircuitBreaker_halfOpenRecovery() throws Exception {
        DefaultNotificationPlatformClient client = new DefaultNotificationPlatformClient(
                "http://localhost:8085", "/api/v1/notifications",
                Optional.empty(), Optional.empty(),
                3, 5, 1, 10,
                objectMapper, mockHttpClient
        );

        com.logistics.common.circuitbreaker.CircuitBreaker cb = new com.logistics.common.circuitbreaker.CircuitBreaker(
                "test-recovery",
                com.logistics.common.circuitbreaker.CircuitBreakerConfig.builder()
                        .failureThreshold(1)
                        .openDuration(java.time.Duration.ofMillis(80))
                        .successThreshold(2)
                        .trialCalls(2)
                        .build()
        );
        cb.recordFailure(new IOException("Downstream down"));
        assertThat(cb.isOpen()).isTrue();

        client.setCircuitBreaker(cb);

        // Wait for open window to expire
        Thread.sleep(100);
        assertThat(cb.isHalfOpen()).isTrue();

        // Downstream now recovers: returns 202
        when(mockResponse.statusCode()).thenReturn(202);
        when(mockResponse.body()).thenReturn("{\"id\":\"" + UUID.randomUUID() + "\",\"status\":\"QUEUED\"}");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        NotificationPlatformRequest request = new NotificationPlatformRequest("0988776655", "SMS", "Sub", "Content", "NORMAL");

        // Probe call 1 succeeds
        NotificationPlatformResult r1 = client.sendNotification(request, "evt-probe-1");
        assertThat(r1.isSuccess()).isTrue();
        assertThat(cb.isHalfOpen()).isTrue();

        // Probe call 2 succeeds -> circuit closes!
        NotificationPlatformResult r2 = client.sendNotification(request, "evt-probe-2");
        assertThat(r2.isSuccess()).isTrue();
        assertThat(cb.isClosed()).isTrue();
    }

    @Test
    @DisplayName("PB-055: Rate Limiting regulates downstream requests using Token Bucket without dropping events")
    void testSendNotification_rateLimiting_regulatesThroughput() throws Exception {
        when(mockResponse.statusCode()).thenReturn(202);
        when(mockResponse.body()).thenReturn("{\"id\":\"" + UUID.randomUUID() + "\",\"status\":\"QUEUED\"}");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        DefaultNotificationPlatformClient client = new DefaultNotificationPlatformClient(
                "http://localhost:8085",
                "/api/v1/notifications",
                Optional.empty(),
                Optional.empty(),
                1, 1, 1, 10, 100, 2.0, 0.0,
                objectMapper, mockHttpClient
        );

        // Configure a rate limiter of 10 permits/sec with burst 2.0
        com.logistics.common.ratelimit.EventRateLimiter rateLimiter =
                new com.logistics.common.ratelimit.EventRateLimiter(10.0, 2.0);
        client.setEventRateLimiter(rateLimiter);

        NotificationPlatformRequest request = new NotificationPlatformRequest("user-1", "EMAIL", "Sub", "Body", "NORMAL");

        // First 2 calls use available burst permits
        NotificationPlatformResult r1 = client.sendNotification(request, "evt-rl-1");
        NotificationPlatformResult r2 = client.sendNotification(request, "evt-rl-2");
        assertThat(r1.isSuccess()).isTrue();
        assertThat(r2.isSuccess()).isTrue();

        // 3rd call will acquire permit cleanly after refill without dropping event
        NotificationPlatformResult r3 = client.sendNotification(request, "evt-rl-3");
        assertThat(r3.isSuccess()).isTrue();
        verify(mockHttpClient, times(3)).send(any(HttpRequest.class), any());
    }
}
