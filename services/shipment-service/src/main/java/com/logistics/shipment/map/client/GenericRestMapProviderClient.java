package com.logistics.shipment.map.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.logistics.common.dto.GeocodeResponse;
import com.logistics.common.dto.MapCoordinatesDto;
import com.logistics.common.dto.RouteDirectionRequest;
import com.logistics.common.dto.RouteDirectionResponse;
import com.logistics.common.dto.RouteStepDto;
import com.logistics.shipment.map.config.MapConfiguration;
import com.logistics.shipment.map.exception.MapConfigurationException;
import com.logistics.shipment.map.exception.MapException;
import com.logistics.shipment.map.exception.MapInvalidRequestException;
import com.logistics.shipment.map.exception.MapRateLimitException;
import com.logistics.shipment.map.exception.MapServiceTimeoutException;
import com.logistics.shipment.map.exception.MapServiceUnavailableException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Generic REST map provider client allowing connection to proprietary or third-party
 * routing services with authentication via API Key.
 */
@ApplicationScoped
public class GenericRestMapProviderClient implements MapProviderClient {

    private static final Logger log = LoggerFactory.getLogger(GenericRestMapProviderClient.class);
    public static final String PROVIDER_NAME = "GENERIC_REST";

    @Inject
    MapConfiguration configuration;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    MockMapProviderClient mockFallbackClient;

    private HttpClient httpClient;

    public GenericRestMapProviderClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    // For testing
    public void setHttpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public void setConfiguration(MapConfiguration configuration) {
        this.configuration = configuration;
    }

    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public RouteDirectionResponse getDirections(RouteDirectionRequest request) throws MapException {
        validateConfiguration();

        if (request == null || request.getOrigin() == null || request.getDestination() == null) {
            throw new MapInvalidRequestException("Origin and destination must be non-null", PROVIDER_NAME);
        }
        if (!request.getOrigin().isValid() || !request.getDestination().isValid()) {
            throw new MapInvalidRequestException("Invalid coordinate boundaries", PROVIDER_NAME);
        }

        String baseUrl = configuration.getServiceUrl();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        String endpoint = baseUrl + "/directions";

        String apiKey = configuration.getApiKey();
        long timeoutMs = configuration.getTimeoutMs();

        try {
            String requestBody = objectMapper.writeValueAsString(request);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("X-API-KEY", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();

            if (status == 401 || status == 403) {
                throw new MapConfigurationException("Invalid or unauthorized API key for map provider", PROVIDER_NAME);
            }
            if (status == 429) {
                throw new MapRateLimitException("Provider rate limit exceeded (HTTP 429)", PROVIDER_NAME);
            }
            if (status == 400) {
                throw new MapInvalidRequestException("Provider rejected request: " + response.body(), PROVIDER_NAME);
            }
            if (status >= 500) {
                throw new MapServiceUnavailableException("Provider server error (HTTP " + status + "): " + response.body(), PROVIDER_NAME);
            }
            if (status < 200 || status >= 300) {
                throw new MapServiceUnavailableException("Unexpected response from map provider: " + status, PROVIDER_NAME);
            }

            return parseGenericResponse(response.body(), request);

        } catch (HttpTimeoutException e) {
            log.warn("Generic REST map provider timed out after {} ms: {}", timeoutMs, e.getMessage());
            throw new MapServiceTimeoutException("Map provider request timed out after " + timeoutMs + "ms", e, PROVIDER_NAME);
        } catch (ConnectException e) {
            log.warn("Failed to connect to generic REST map provider at {}: {}", baseUrl, e.getMessage());
            throw new MapServiceUnavailableException("Failed to connect to map provider at " + baseUrl, e, PROVIDER_NAME);
        } catch (IOException e) {
            log.warn("I/O error communicating with generic REST map provider: {}", e.getMessage());
            throw new MapServiceUnavailableException("I/O error communicating with map provider: " + e.getMessage(), e, PROVIDER_NAME);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MapServiceUnavailableException("Map provider request interrupted", e, PROVIDER_NAME);
        }
    }

    @Override
    public GeocodeResponse geocode(String address) throws MapException {
        validateConfiguration();
        return mockFallbackClient.geocode(address);
    }

    @Override
    public GeocodeResponse reverseGeocode(double latitude, double longitude) throws MapException {
        validateConfiguration();
        return mockFallbackClient.reverseGeocode(latitude, longitude);
    }

    private void validateConfiguration() {
        if (configuration.getApiKey() == null || configuration.getApiKey().isBlank()) {
            throw new MapConfigurationException("API key is missing or blank for GENERIC_REST map provider", PROVIDER_NAME);
        }
        if (configuration.getServiceUrl() == null || configuration.getServiceUrl().isBlank()) {
            throw new MapConfigurationException("Service URL is missing or blank for GENERIC_REST map provider", PROVIDER_NAME);
        }
    }

    private RouteDirectionResponse parseGenericResponse(String body, RouteDirectionRequest request) {
        try {
            JsonNode root = objectMapper.readTree(body);
            double distMeters = root.path("distanceMeters").asDouble(0.0);
            long durSec = root.path("durationSeconds").asLong(0L);
            String summary = root.path("summary").asText("Route calculated by generic provider");

            List<MapCoordinatesDto> geom = new ArrayList<>();
            JsonNode geomNode = root.path("geometry");
            if (geomNode.isArray()) {
                for (JsonNode p : geomNode) {
                    geom.add(new MapCoordinatesDto(p.path("latitude").asDouble(), p.path("longitude").asDouble()));
                }
            }

            List<RouteStepDto> steps = new ArrayList<>();
            JsonNode stepsNode = root.path("steps");
            if (stepsNode.isArray()) {
                for (JsonNode s : stepsNode) {
                    steps.add(new RouteStepDto(
                            s.path("instruction").asText(),
                            s.path("distanceMeters").asDouble(),
                            s.path("durationSeconds").asDouble(),
                            s.path("maneuver").asText(),
                            null, null
                    ));
                }
            }

            return RouteDirectionResponse.success(distMeters, durSec, geom, steps, summary, PROVIDER_NAME);
        } catch (Exception e) {
            throw new MapServiceUnavailableException("Failed to parse provider response: " + e.getMessage(), e, PROVIDER_NAME);
        }
    }
}
