package com.logistics.notification.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Optional;

@ApplicationScoped
@DefaultBean
public class DefaultNotificationPlatformClient implements NotificationPlatformClient {

    private static final Logger log = LoggerFactory.getLogger(DefaultNotificationPlatformClient.class);

    @ConfigProperty(name = "notification.platform.url", defaultValue = "http://localhost:8085")
    String platformUrl;

    @ConfigProperty(name = "notification.platform.endpoint", defaultValue = "/api/v1/notifications")
    String endpoint;

    @ConfigProperty(name = "notification.platform.api-key")
    Optional<String> apiKey;

    @ConfigProperty(name = "notification.platform.auth-token")
    Optional<String> authToken;

    @ConfigProperty(name = "notification.platform.connect-timeout-seconds", defaultValue = "3")
    int connectTimeoutSeconds;

    @ConfigProperty(name = "notification.platform.request-timeout-seconds", defaultValue = "5")
    int requestTimeoutSeconds;

    @ConfigProperty(name = "notification.platform.max-retries", defaultValue = "3")
    int maxRetries;

    @ConfigProperty(name = "notification.platform.retry-delay-ms", defaultValue = "500")
    long retryDelayMs;

    @Inject
    ObjectMapper objectMapper;

    private HttpClient httpClient;

    public DefaultNotificationPlatformClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    public DefaultNotificationPlatformClient(String platformUrl, String endpoint,
                                           Optional<String> apiKey, Optional<String> authToken,
                                           int connectTimeoutSeconds, int requestTimeoutSeconds,
                                           int maxRetries, long retryDelayMs,
                                           ObjectMapper objectMapper, HttpClient httpClient) {
        this.platformUrl = platformUrl;
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.authToken = authToken;
        this.connectTimeoutSeconds = connectTimeoutSeconds;
        this.requestTimeoutSeconds = requestTimeoutSeconds;
        this.maxRetries = maxRetries;
        this.retryDelayMs = retryDelayMs;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient != null ? httpClient : HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .build();
    }

    @Override
    public NotificationPlatformResult sendNotification(NotificationPlatformRequest request, String eventId) {
        String base = (platformUrl != null && !platformUrl.isBlank()) ? platformUrl.trim() : "http://localhost:8085";
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String path = (endpoint != null && !endpoint.isBlank()) ? endpoint.trim() : "/api/v1/notifications";
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        String fullUrl = base + path;

        if (objectMapper == null) {
            objectMapper = new ObjectMapper().findAndRegisterModules();
        }

        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            log.error("Failed to serialize NotificationPlatformRequest: {}", e.getMessage(), e);
            return NotificationPlatformResult.failure(400, "Serialization error: " + e.getMessage(), null, 0);
        }

        int attempts = 0;
        int lastStatusCode = 0;
        String lastErrorMsg = null;
        Long retryAfterSeconds = null;

        while (attempts < Math.max(1, maxRetries)) {
            attempts++;
            try {
                HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(fullUrl))
                        .timeout(Duration.ofSeconds(Math.max(1, requestTimeoutSeconds)))
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody));

                if (eventId != null && !eventId.isBlank()) {
                    reqBuilder.header("X-Correlation-Id", eventId);
                    reqBuilder.header("X-Idempotency-Key", eventId);
                }

                if (apiKey != null && apiKey.isPresent() && !apiKey.get().isBlank()) {
                    reqBuilder.header("X-API-Key", apiKey.get().trim());
                }

                if (authToken != null && authToken.isPresent() && !authToken.get().isBlank()) {
                    reqBuilder.header("Authorization", "Bearer " + authToken.get().trim());
                }

                log.info("Sending notification request to Notification Platform at '{}' (attempt {}/{}, eventId={})",
                        fullUrl, attempts, maxRetries, eventId);

                HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
                lastStatusCode = response.statusCode();
                String responseBody = response.body();

                // 1. Success: 202 Accepted (Standard ingestion contract) or 200 OK
                if (lastStatusCode == 202 || lastStatusCode == 200) {
                    NotificationPlatformResponse respObj = objectMapper.readValue(responseBody, NotificationPlatformResponse.class);
                    log.info("Notification Platform accepted request: id='{}', status='{}' (HTTP {})",
                            respObj.getId(), respObj.getStatus(), lastStatusCode);
                    return NotificationPlatformResult.success(respObj.getId(), respObj.getStatus(), lastStatusCode, attempts);
                }

                // 2. Client non-retryable errors: 400 Bad Request, 401 Unauthorized, 403 Forbidden
                if (lastStatusCode == 400 || lastStatusCode == 401 || lastStatusCode == 403) {
                    lastErrorMsg = String.format("Notification Platform rejected request with HTTP %d: %s", lastStatusCode, responseBody);
                    log.warn("Non-retryable response from Notification Platform: {}", lastErrorMsg);
                    return NotificationPlatformResult.failure(lastStatusCode, lastErrorMsg, null, attempts);
                }

                // 3. Retryable server errors: 429 Too Many Requests, 503 Service Unavailable, 500, 502, 504
                Optional<String> retryAfterHeader = response.headers().firstValue("Retry-After");
                if (retryAfterHeader.isPresent()) {
                    try {
                        retryAfterSeconds = Long.parseLong(retryAfterHeader.get().trim());
                    } catch (NumberFormatException ignored) {
                    }
                }

                lastErrorMsg = String.format("Notification Platform returned HTTP %d: %s", lastStatusCode, responseBody);
                log.warn("Retryable HTTP {} from Notification Platform (attempt {}/{}, retryAfter={}): {}",
                        lastStatusCode, attempts, maxRetries, retryAfterSeconds, lastErrorMsg);

            } catch (HttpTimeoutException e) {
                lastStatusCode = 408;
                lastErrorMsg = "Request timeout to Notification Platform: " + e.getMessage();
                log.warn("Timeout connecting to Notification Platform (attempt {}/{}): {}", attempts, maxRetries, e.getMessage());
            } catch (IOException e) {
                lastStatusCode = 503;
                lastErrorMsg = "I/O connection error to Notification Platform: " + e.getMessage();
                log.warn("I/O error communicating with Notification Platform (attempt {}/{}): {}", attempts, maxRetries, e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return NotificationPlatformResult.failure(500, "Thread interrupted during call to Notification Platform", null, attempts);
            } catch (Exception e) {
                lastStatusCode = 500;
                lastErrorMsg = "Unexpected error calling Notification Platform: " + e.getMessage();
                log.error("Unexpected error calling Notification Platform: {}", e.getMessage(), e);
            }

            // Sleep backoff delay if retries remain
            if (attempts < maxRetries) {
                long sleepMs = retryDelayMs;
                if (retryAfterSeconds != null && retryAfterSeconds > 0 && retryAfterSeconds <= 5) {
                    sleepMs = retryAfterSeconds * 1000;
                }
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        log.error("Exhausted all {} attempts to call Notification Platform. Final status: {}, error: {}",
                attempts, lastStatusCode, lastErrorMsg);
        return NotificationPlatformResult.failure(lastStatusCode, lastErrorMsg, retryAfterSeconds, attempts);
    }
}
