package com.logistics.order.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.logistics.common.dto.ApiResponse;
import com.logistics.common.dto.OrderTrackingDto;
import com.logistics.order.exception.AccessDeniedException;
import com.logistics.order.exception.OrderNotFoundException;
import com.logistics.order.exception.TrackingServiceUnavailableException;
import io.quarkus.arc.DefaultBean;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@ApplicationScoped
@DefaultBean
public class DefaultTrackingServiceClient implements TrackingServiceClient {

    private static final Logger log = LoggerFactory.getLogger(DefaultTrackingServiceClient.class);

    @ConfigProperty(name = "tracking.service.url", defaultValue = "http://localhost:8083")
    String trackingServiceUrl;

    @Inject
    ObjectMapper objectMapper;

    private HttpClient httpClient;

    @PostConstruct
    void init() {
        if (objectMapper == null) {
            objectMapper = new ObjectMapper().findAndRegisterModules();
        }
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    public DefaultTrackingServiceClient() {
    }

    public DefaultTrackingServiceClient(String trackingServiceUrl, ObjectMapper objectMapper, HttpClient httpClient) {
        this.trackingServiceUrl = trackingServiceUrl;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper().findAndRegisterModules();
        this.httpClient = httpClient != null ? httpClient : HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }

    @Override
    public OrderTrackingDto getOrderTracking(String authHeader, String orderId, boolean allowStale) {
        String base = trackingServiceUrl != null ? trackingServiceUrl : "http://localhost:8083";
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String endpoint = base + "/tracking/orders/" + orderId + "?allowStale=" + allowStale;

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(4))
                .header("Accept", "application/json")
                .GET();

        if (authHeader != null && !authHeader.isBlank()) {
            requestBuilder.header("Authorization", authHeader);
        }

        try {
            if (httpClient == null) {
                httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
            }

            HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            String responseBody = response.body();

            if (statusCode == 200) {
                ApiResponse<OrderTrackingDto> apiResponse = objectMapper.readValue(
                        responseBody,
                        new TypeReference<ApiResponse<OrderTrackingDto>>() {}
                );
                return apiResponse != null ? apiResponse.getData() : null;
            } else if (statusCode == 403) {
                String message = extractErrorMessage(responseBody, "Access denied: You do not own this order");
                throw new AccessDeniedException(message);
            } else if (statusCode == 404) {
                String message = extractErrorMessage(responseBody, "Order not found in tracking service: " + orderId);
                throw new OrderNotFoundException(message);
            } else if (statusCode == 400) {
                String message = extractErrorMessage(responseBody, "Order is not in a trackable state");
                throw new IllegalStateException(message);
            } else if (statusCode >= 500) {
                log.error("Tracking Service returned HTTP {}: {}", statusCode, responseBody);
                throw new TrackingServiceUnavailableException("Tracking Service returned error HTTP " + statusCode);
            } else {
                String message = extractErrorMessage(responseBody, "Tracking error: HTTP " + statusCode);
                throw new IllegalStateException(message);
            }

        } catch (AccessDeniedException | OrderNotFoundException | IllegalStateException | TrackingServiceUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.error("Tracking Service call failed for order '{}' at {}: {}", orderId, endpoint, e.getMessage());
            throw new TrackingServiceUnavailableException("Tracking Service is currently unavailable: " + e.getMessage(), e);
        }
    }

    private String extractErrorMessage(String body, String defaultMessage) {
        if (body == null || body.isBlank()) {
            return defaultMessage;
        }
        try {
            ApiResponse<?> errorResp = objectMapper.readValue(body, ApiResponse.class);
            if (errorResp != null && errorResp.getMessage() != null && !errorResp.getMessage().isBlank()) {
                return errorResp.getMessage();
            }
        } catch (Exception ignored) {
        }
        return defaultMessage;
    }
}
