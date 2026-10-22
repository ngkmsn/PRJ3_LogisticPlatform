package com.logistics.shipment.map.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.logistics.common.dto.GeocodeResponse;
import com.logistics.common.dto.MapCoordinatesDto;
import com.logistics.common.dto.RouteDirectionRequest;
import com.logistics.common.dto.RouteDirectionResponse;
import com.logistics.common.dto.RouteStepDto;
import com.logistics.shipment.map.config.MapConfiguration;
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
import java.util.Locale;

/**
 * Open Source Routing Machine (OSRM) client implementation for OpenStreetMap routing.
 * Decouples external OSRM JSON contract from platform business logic.
 */
@ApplicationScoped
public class OsrmMapProviderClient implements MapProviderClient {

    private static final Logger log = LoggerFactory.getLogger(OsrmMapProviderClient.class);
    public static final String PROVIDER_NAME = "OSRM";

    @Inject
    MapConfiguration configuration;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    MockMapProviderClient mockFallbackClient;

    private HttpClient httpClient;

    public OsrmMapProviderClient() {
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
        if (request == null || request.getOrigin() == null || request.getDestination() == null) {
            throw new MapInvalidRequestException("Origin and destination must be non-null", PROVIDER_NAME);
        }
        if (!request.getOrigin().isValid() || !request.getDestination().isValid()) {
            throw new MapInvalidRequestException("Invalid coordinate boundaries", PROVIDER_NAME);
        }

        String baseUrl = configuration.getServiceUrl();
        if (baseUrl.isBlank()) {
            baseUrl = "http://router.project-osrm.org";
        }
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        String mode = "BICYCLE".equalsIgnoreCase(request.getVehicleMode()) ? "bike" : "driving";
        MapCoordinatesDto o = request.getOrigin();
        MapCoordinatesDto d = request.getDestination();

        // OSRM expects coordinates as {lon},{lat};{lon},{lat}
        String endpoint = String.format(Locale.ROOT, "%s/route/v1/%s/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=geojson&steps=true",
                baseUrl, mode, o.getLongitude(), o.getLatitude(), d.getLongitude(), d.getLatitude());

        long timeoutMs = configuration.getTimeoutMs();
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Accept", "application/json")
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();

            if (statusCode == 429) {
                throw new MapRateLimitException("OSRM rate limit exceeded (HTTP 429)", PROVIDER_NAME);
            }
            if (statusCode == 400) {
                throw new MapInvalidRequestException("OSRM rejected request as invalid: " + response.body(), PROVIDER_NAME);
            }
            if (statusCode >= 500) {
                throw new MapServiceUnavailableException("OSRM server error (HTTP " + statusCode + "): " + response.body(), PROVIDER_NAME);
            }
            if (statusCode < 200 || statusCode >= 300) {
                throw new MapServiceUnavailableException("Unexpected response from OSRM: " + statusCode, PROVIDER_NAME);
            }

            return parseOsrmResponse(response.body(), request);

        } catch (HttpTimeoutException e) {
            log.warn("OSRM map provider timed out after {} ms: {}", timeoutMs, e.getMessage());
            throw new MapServiceTimeoutException("OSRM request timed out after " + timeoutMs + "ms", e, PROVIDER_NAME);
        } catch (ConnectException e) {
            log.warn("Failed to connect to OSRM map provider at {}: {}", baseUrl, e.getMessage());
            throw new MapServiceUnavailableException("Failed to connect to OSRM service at " + baseUrl, e, PROVIDER_NAME);
        } catch (IOException e) {
            log.warn("I/O error communicating with OSRM map provider: {}", e.getMessage());
            throw new MapServiceUnavailableException("I/O failure communicating with OSRM service: " + e.getMessage(), e, PROVIDER_NAME);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MapServiceUnavailableException("OSRM request interrupted", e, PROVIDER_NAME);
        }
    }

    @Override
    public GeocodeResponse geocode(String address) throws MapException {
        // Fall back to built-in geocoding lookup
        return mockFallbackClient.geocode(address);
    }

    @Override
    public GeocodeResponse reverseGeocode(double latitude, double longitude) throws MapException {
        return mockFallbackClient.reverseGeocode(latitude, longitude);
    }

    private RouteDirectionResponse parseOsrmResponse(String responseJson, RouteDirectionRequest request) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            String code = root.path("code").asText("");
            if (!"Ok".equalsIgnoreCase(code)) {
                throw new MapInvalidRequestException("OSRM returned non-OK status: " + code, PROVIDER_NAME);
            }

            JsonNode routeNode = root.path("routes").get(0);
            if (routeNode == null) {
                throw new MapInvalidRequestException("No route found between coordinates", PROVIDER_NAME);
            }

            double distanceMeters = routeNode.path("distance").asDouble(0.0);
            long durationSeconds = Math.round(routeNode.path("duration").asDouble(0.0));

            // Extract geometry coordinates
            List<MapCoordinatesDto> geometry = new ArrayList<>();
            JsonNode coordinatesNode = routeNode.path("geometry").path("coordinates");
            if (coordinatesNode.isArray()) {
                for (JsonNode coord : coordinatesNode) {
                    double lon = coord.get(0).asDouble();
                    double lat = coord.get(1).asDouble();
                    geometry.add(MapCoordinatesDto.of(lat, lon));
                }
            }

            // Extract turn-by-turn steps
            List<RouteStepDto> steps = new ArrayList<>();
            JsonNode legs = routeNode.path("legs");
            if (legs.isArray() && !legs.isEmpty()) {
                JsonNode stepsNode = legs.get(0).path("steps");
                if (stepsNode.isArray()) {
                    for (JsonNode s : stepsNode) {
                        String name = s.path("name").asText("");
                        String maneuverType = s.path("maneuver").path("type").asText("straight");
                        String modifier = s.path("maneuver").path("modifier").asText("");
                        double stepDist = s.path("distance").asDouble(0.0);
                        double stepDur = s.path("duration").asDouble(0.0);

                        String instruction = formatStepInstruction(maneuverType, modifier, name, stepDist);
                        steps.add(new RouteStepDto(instruction, stepDist, stepDur, maneuverType, null, null));
                    }
                }
            }

            String summary = String.format(Locale.ROOT, "OSRM route: %.1f km, ~%d mins",
                    distanceMeters / 1000.0, Math.max(1, durationSeconds / 60));

            return RouteDirectionResponse.success(distanceMeters, durationSeconds, geometry, steps, summary, PROVIDER_NAME);

        } catch (MapException e) {
            throw e;
        } catch (Exception e) {
            throw new MapServiceUnavailableException("Failed to parse OSRM response JSON: " + e.getMessage(), e, PROVIDER_NAME);
        }
    }

    private String formatStepInstruction(String maneuver, String modifier, String streetName, double distanceMeters) {
        String street = streetName.isBlank() ? "current road" : streetName;
        return switch (maneuver) {
            case "depart" -> "Depart onto " + street;
            case "arrive" -> "Arrive at destination";
            case "turn" -> "Turn " + modifier + " onto " + street;
            case "new name" -> "Continue onto " + street;
            default -> "Follow " + street + " for " + Math.round(distanceMeters) + "m";
        };
    }
}
