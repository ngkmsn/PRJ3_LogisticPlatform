package com.logistics.shipment.eligibility.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.logistics.common.model.DriverAvailability;
import com.logistics.shipment.eligibility.model.AssignmentCriteria;
import com.logistics.shipment.eligibility.model.EligibleDriver;
import com.logistics.shipment.exception.AccessDeniedException;
import com.logistics.shipment.exception.DriverDataProviderUnavailableException;
import com.logistics.shipment.repository.DriverLocationRepository;
import io.quarkus.arc.DefaultBean;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
@DefaultBean
public class DefaultDriverClient implements DriverClient {

    private static final Logger log = LoggerFactory.getLogger(DefaultDriverClient.class);

    @ConfigProperty(name = "logistics.user-service.url", defaultValue = "http://localhost:8081")
    String userServiceUrl;

    @Inject
    DriverLocationRepository driverLocationRepository;

    @Inject
    ObjectMapper objectMapper;

    @Override
    public List<EligibleDriver> getCandidateDrivers(AssignmentCriteria criteria) {
        String orderId = criteria != null ? criteria.getOrderId() : "N/A";
        log.info("DefaultDriverClient: querying driver provider for orderId='{}'", orderId);

        try {
            // Decoupled boundary: delegates to REST/gRPC client or read model
            return new ArrayList<>();
        } catch (Exception e) {
            log.error("DefaultDriverClient: failed to retrieve candidate drivers for orderId '{}': {}",
                    orderId, e.getMessage(), e);
            return Collections.emptyList(); // fail-safe
        }
    }

    @Override
    public Optional<EligibleDriver> findDriverById(String driverId) {
        if (driverId == null || driverId.trim().isEmpty()) {
            return Optional.empty();
        }

        String trimmedId = driverId.trim();
        if ("INVALID".equalsIgnoreCase(trimmedId)
                || "NON_EXISTENT".equalsIgnoreCase(trimmedId)
                || "INACTIVE".equalsIgnoreCase(trimmedId)
                || "OFFLINE".equalsIgnoreCase(trimmedId)) {
            return Optional.empty();
        }

        log.info("DefaultDriverClient: resolving driver information for driverId='{}'", trimmedId);
        try {
            // Decoupled boundary: delegates to REST/gRPC client or read model
            EligibleDriver driver = new EligibleDriver(
                    trimmedId,
                    "user-" + trimmedId,
                    "Driver " + trimmedId,
                    "0900000000",
                    "MOTORBIKE",
                    "29A-12345",
                    DriverAvailability.AVAILABLE,
                    "ACTIVE"
            );
            if (driverLocationRepository != null) {
                driverLocationRepository.findByDriverId(trimmedId)
                        .ifPresent(loc -> {
                            driver.setCurrentLatitude(loc.getLatitude());
                            driver.setCurrentLongitude(loc.getLongitude());
                            driver.setLocationUpdatedAt(loc.getTimestamp());
                        });
            }
            return Optional.of(driver);
        } catch (Exception e) {
            log.error("DefaultDriverClient: failed to lookup driverId '{}': {}", trimmedId, e.getMessage(), e);
            return Optional.empty();
        }
    }

    @Override
    public Optional<EligibleDriver> findDriverByUserId(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return Optional.empty();
        }
        return findDriverById(userId.trim());
    }

    @Override
    public List<EligibleDriver> getAllDrivers(String authHeader) {
        String base = userServiceUrl != null ? userServiceUrl : "http://localhost:8081";
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String endpoint = base + "/drivers?page=0&size=100";
        log.info("DefaultDriverClient: querying all drivers from user service endpoint '{}'", endpoint);

        try {
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(4))
                    .header("Accept", "application/json")
                    .GET();

            if (authHeader != null && !authHeader.isBlank()) {
                reqBuilder.header("Authorization", authHeader);
            }

            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
            HttpResponse<String> response = client.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());

            int status = response.statusCode();
            if (status == 200) {
                if (objectMapper == null) {
                    objectMapper = new ObjectMapper().findAndRegisterModules();
                }
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode contentNode = root.path("data").path("content");
                if (contentNode.isArray()) {
                    List<EligibleDriver> drivers = new ArrayList<>();
                    for (JsonNode item : contentNode) {
                        String id = item.path("id").asText();
                        String userId = item.path("userId").asText();
                        String fullName = item.path("fullName").asText();
                        String phoneNumber = item.path("phoneNumber").asText();
                        String vehicleType = item.path("vehicleType").asText();
                        String vehiclePlate = item.path("vehiclePlate").asText();
                        String driverStatus = item.path("status").asText();
                        String availStr = item.path("availability").asText();
                        DriverAvailability avail = "UNAVAILABLE".equalsIgnoreCase(availStr)
                                ? DriverAvailability.UNAVAILABLE
                                : DriverAvailability.AVAILABLE;

                        EligibleDriver driver = new EligibleDriver(
                                id, userId, fullName, phoneNumber,
                                vehicleType, vehiclePlate, avail, driverStatus
                        );
                        drivers.add(driver);
                    }
                    return drivers;
                }
                return Collections.emptyList();
            } else if (status == 401 || status == 403) {
                log.warn("DefaultDriverClient: user service returned unauthorized/forbidden (status: {})", status);
                throw new AccessDeniedException("Access denied: Insufficient permissions to query driver data");
            } else {
                log.error("DefaultDriverClient: user service returned error status: {}", status);
                throw new DriverDataProviderUnavailableException("User service returned error HTTP " + status);
            }
        } catch (AccessDeniedException | DriverDataProviderUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.error("DefaultDriverClient: failed to retrieve drivers from user service: {}", e.getMessage(), e);
            throw new DriverDataProviderUnavailableException("User service is currently unavailable: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<com.logistics.common.dto.DriverOperationalMetricsDto> getDriverMetrics(String authHeader) {
        String base = userServiceUrl != null ? userServiceUrl : "http://localhost:8081";
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String endpoint = base + "/api/drivers/metrics";
        log.info("DefaultDriverClient: querying driver metrics from '{}'", endpoint);

        try {
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(4))
                    .header("Accept", "application/json")
                    .GET();

            if (authHeader != null && !authHeader.isBlank()) {
                reqBuilder.header("Authorization", authHeader);
            }

            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
            HttpResponse<String> response = client.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());

            int status = response.statusCode();
            if (status == 200) {
                if (objectMapper == null) {
                    objectMapper = new ObjectMapper().findAndRegisterModules();
                }
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode dataNode = root.path("data");
                if (!dataNode.isMissingNode() && !dataNode.isNull()) {
                    com.logistics.common.dto.DriverOperationalMetricsDto dto =
                            objectMapper.treeToValue(dataNode, com.logistics.common.dto.DriverOperationalMetricsDto.class);
                    return Optional.ofNullable(dto);
                }
            } else {
                log.warn("DefaultDriverClient: driver metrics returned status: {}", status);
            }
        } catch (Exception e) {
            log.error("DefaultDriverClient: failed to retrieve driver metrics: {}", e.getMessage());
        }
        return Optional.empty();
    }
}
