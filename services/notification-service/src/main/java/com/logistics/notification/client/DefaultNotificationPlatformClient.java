package com.logistics.notification.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logistics.common.circuitbreaker.CircuitBreaker;
import com.logistics.common.circuitbreaker.CircuitBreakerConfig;
import com.logistics.common.circuitbreaker.CircuitBreakerRegistry;
import com.logistics.common.ratelimit.EventRateLimiter;
import com.logistics.common.retry.BackoffCalculator;
import com.logistics.common.retry.ErrorClassifier;
import com.logistics.common.retry.RetryPolicy;
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

    @ConfigProperty(name = "notification.platform.max-backoff-ms", defaultValue = "10000")
    long maxBackoffMs = 10000;

    @ConfigProperty(name = "notification.platform.backoff-multiplier", defaultValue = "2.0")
    double backoffMultiplier = 2.0;

    @ConfigProperty(name = "notification.platform.jitter-factor", defaultValue = "0.2")
    double jitterFactor = 0.2;

    @ConfigProperty(name = "notification.circuit-breaker.enabled", defaultValue = "true")
    boolean circuitBreakerEnabled = true;

    @ConfigProperty(name = "notification.circuit-breaker.failure-threshold", defaultValue = "3")
    int cbFailureThreshold = 3;

    @ConfigProperty(name = "notification.circuit-breaker.open-duration-ms", defaultValue = "10000")
    long cbOpenDurationMs = 10000;

    @ConfigProperty(name = "notification.circuit-breaker.success-threshold", defaultValue = "2")
    int cbSuccessThreshold = 2;

    @ConfigProperty(name = "notification.rate-limit.enabled", defaultValue = "true")
    boolean rateLimitEnabled = true;

    @ConfigProperty(name = "notification.rate-limit.permits-per-second", defaultValue = "50.0")
    double rateLimitPermitsPerSecond = 50.0;

    @ConfigProperty(name = "notification.rate-limit.burst-capacity", defaultValue = "50.0")
    double rateLimitBurstCapacity = 50.0;

    @Inject
    ObjectMapper objectMapper;

    private HttpClient httpClient;
    private BackoffCalculator backoffCalculator;
    private CircuitBreaker circuitBreaker;
    private EventRateLimiter eventRateLimiter;

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
        this(platformUrl, endpoint, apiKey, authToken, connectTimeoutSeconds, requestTimeoutSeconds,
                maxRetries, retryDelayMs, 10000, 2.0, 0.2, objectMapper, httpClient);
    }

    public DefaultNotificationPlatformClient(String platformUrl, String endpoint,
                                           Optional<String> apiKey, Optional<String> authToken,
                                           int connectTimeoutSeconds, int requestTimeoutSeconds,
                                           int maxRetries, long retryDelayMs,
                                           long maxBackoffMs, double backoffMultiplier, double jitterFactor,
                                           ObjectMapper objectMapper, HttpClient httpClient) {
        this.platformUrl = platformUrl;
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.authToken = authToken;
        this.connectTimeoutSeconds = connectTimeoutSeconds;
        this.requestTimeoutSeconds = requestTimeoutSeconds;
        this.maxRetries = maxRetries;
        this.retryDelayMs = retryDelayMs;
        this.maxBackoffMs = maxBackoffMs;
        this.backoffMultiplier = backoffMultiplier;
        this.jitterFactor = jitterFactor;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient != null ? httpClient : HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .build();
        initBackoffCalculator();
        initCircuitBreaker();
    }

    public void initCircuitBreaker() {
        if (!circuitBreakerEnabled) {
            this.circuitBreaker = null;
            return;
        }
        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.builder()
                .failureThreshold(Math.max(1, cbFailureThreshold))
                .openDuration(Duration.ofMillis(Math.max(100, cbOpenDurationMs)))
                .successThreshold(Math.max(1, cbSuccessThreshold))
                .trialCalls(Math.max(1, cbSuccessThreshold))
                .failurePredicate(ErrorClassifier::isTransientError)
                .build();
        this.circuitBreaker = CircuitBreakerRegistry.getInstance().getOrCreate("notification-platform", cbConfig);
    }

    public void setCircuitBreaker(CircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }

    public CircuitBreaker getCircuitBreaker() {
        if (this.circuitBreaker == null && this.circuitBreakerEnabled) {
            initCircuitBreaker();
        }
        return this.circuitBreaker;
    }

    public void setCircuitBreakerEnabled(boolean circuitBreakerEnabled) {
        this.circuitBreakerEnabled = circuitBreakerEnabled;
    }

    public EventRateLimiter getEventRateLimiter() {
        if (this.eventRateLimiter == null && this.rateLimitEnabled) {
            this.eventRateLimiter = new EventRateLimiter(
                    Math.max(1.0, rateLimitPermitsPerSecond),
                    Math.max(1.0, rateLimitBurstCapacity)
            );
        }
        return this.eventRateLimiter;
    }

    public void setEventRateLimiter(EventRateLimiter eventRateLimiter) {
        this.eventRateLimiter = eventRateLimiter;
    }

    public void setRateLimitEnabled(boolean rateLimitEnabled) {
        this.rateLimitEnabled = rateLimitEnabled;
    }

    private void initBackoffCalculator() {
        long initial = Math.max(1, retryDelayMs);
        long max = Math.max(initial, maxBackoffMs);
        double mult = Math.max(1.0, backoffMultiplier);
        double jitter = Math.max(0.0, Math.min(1.0, jitterFactor));

        RetryPolicy policy = RetryPolicy.builder()
                .maxRetries(maxRetries)
                .initialBackoff(Duration.ofMillis(initial))
                .maxBackoff(Duration.ofMillis(max))
                .multiplier(mult)
                .jitterFactor(jitter)
                .build();
        this.backoffCalculator = new BackoffCalculator(policy);
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

        if (circuitBreaker == null && circuitBreakerEnabled) {
            initCircuitBreaker();
        }

        // PB-054: Fast-fail immediately if Circuit Breaker is OPEN to protect thread and network resources
        if (circuitBreakerEnabled && circuitBreaker != null && circuitBreaker.isOpen()) {
            log.warn("PB-054 [CIRCUIT_SHORT_CIRCUIT] Notification Platform circuit breaker is OPEN. Fast-failing request for eventId='{}' to conserve resources.", eventId);
            return NotificationPlatformResult.circuitOpen(
                    String.format("Circuit breaker '%s' is OPEN: Notification Platform is currently unavailable.", circuitBreaker.getName())
            );
        }

        // PB-055: Rate limit outbound calls to prevent downstream saturation without dropping events
        if (rateLimitEnabled && getEventRateLimiter() != null) {
            getEventRateLimiter().acquire();
        }

        int attempts = 0;
        int lastStatusCode = 0;
        String lastErrorMsg = null;
        Long retryAfterSeconds = null;

        // When in HALF_OPEN, execute exactly 1 probe call to test health without redundant retries
        int effectiveMaxRetries = (circuitBreaker != null && circuitBreaker.isHalfOpen()) ? 1 : Math.max(1, maxRetries);

        while (attempts < effectiveMaxRetries) {
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
                    if (circuitBreaker != null) {
                        circuitBreaker.recordSuccess();
                    }
                    return NotificationPlatformResult.success(respObj.getId(), respObj.getStatus(), lastStatusCode, attempts);
                }

                // 2. Client non-retryable errors: 400 Bad Request, 401 Unauthorized, 403 Forbidden
                if (lastStatusCode == 400 || lastStatusCode == 401 || lastStatusCode == 403) {
                    lastErrorMsg = String.format("Notification Platform rejected request with HTTP %d: %s", lastStatusCode, responseBody);
                    log.warn("Non-retryable response from Notification Platform: {}", lastErrorMsg);
                    if (circuitBreaker != null && circuitBreaker.isHalfOpen()) {
                        // In HALF_OPEN, receiving a valid HTTP 4xx response proves the downstream server is alive
                        circuitBreaker.recordSuccess();
                    }
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

            // Sleep backoff delay if retries remain using Exponential Backoff with Jitter (PB-052)
            if (attempts < maxRetries) {
                if (backoffCalculator == null) {
                    initBackoffCalculator();
                }
                Duration downstreamRetryAfter = (retryAfterSeconds != null && retryAfterSeconds > 0)
                        ? Duration.ofSeconds(retryAfterSeconds)
                        : null;
                Duration delay = backoffCalculator.computeBackoff(attempts, downstreamRetryAfter);

                log.warn("PB-052 [HTTP_RETRY_BACKOFF] op='NotificationPlatformClient.send', failedAttempt={}/{}, delay={}ms (retryAfterHeader={}), lastStatus={}",
                        attempts, maxRetries, delay.toMillis(),
                        downstreamRetryAfter != null ? downstreamRetryAfter.toSeconds() + "s" : "none",
                        lastStatusCode);

                try {
                    Thread.sleep(delay.toMillis());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        log.error("Exhausted all {} attempts to call Notification Platform. Final status: {}, error: {}",
                attempts, lastStatusCode, lastErrorMsg);
        if (circuitBreaker != null) {
            circuitBreaker.recordFailure(new IOException(lastErrorMsg != null ? lastErrorMsg : "Notification Platform unavailable (HTTP " + lastStatusCode + ")"));
        }
        return NotificationPlatformResult.failure(lastStatusCode, lastErrorMsg, retryAfterSeconds, attempts);
    }
}
