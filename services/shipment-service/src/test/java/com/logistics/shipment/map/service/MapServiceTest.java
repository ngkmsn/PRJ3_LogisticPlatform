package com.logistics.shipment.map.service;

import com.logistics.common.dto.MapCoordinatesDto;
import com.logistics.common.dto.RouteDirectionRequest;
import com.logistics.common.dto.RouteDirectionResponse;
import com.logistics.shipment.entity.Assignment;
import com.logistics.shipment.entity.DriverLocation;
import com.logistics.shipment.exception.AssignmentNotFoundException;
import com.logistics.shipment.map.client.GenericRestMapProviderClient;
import com.logistics.shipment.map.client.MockMapProviderClient;
import com.logistics.shipment.map.client.OsrmMapProviderClient;
import com.logistics.shipment.map.config.MapConfiguration;
import com.logistics.shipment.map.exception.MapConfigurationException;
import com.logistics.shipment.map.exception.MapInvalidRequestException;
import com.logistics.shipment.map.exception.MapRateLimitException;
import com.logistics.shipment.map.exception.MapServiceTimeoutException;
import com.logistics.shipment.map.exception.MapServiceUnavailableException;
import com.logistics.shipment.map.limiter.MapRateLimiter;
import com.logistics.shipment.model.AssignmentStatus;
import com.logistics.shipment.repository.AssignmentRepository;
import com.logistics.shipment.repository.DriverLocationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MapServiceTest {

    private DefaultMapService mapService;
    private MapConfiguration config;
    private MapRateLimiter rateLimiter;
    private MockMapProviderClient mockProvider;
    private OsrmMapProviderClient osrmProvider;
    private GenericRestMapProviderClient genericRestProvider;
    private AssignmentRepository assignmentRepository;
    private DriverLocationRepository driverLocationRepository;

    @BeforeEach
    void setUp() {
        config = new MapConfiguration();
        config.setProviderType("MOCK");
        config.setFallbackEnabled(true);
        config.setRateLimitPermitsPerSecond(100);

        rateLimiter = new MapRateLimiter(config);
        mockProvider = new MockMapProviderClient();
        osrmProvider = mock(OsrmMapProviderClient.class);
        genericRestProvider = mock(GenericRestMapProviderClient.class);
        assignmentRepository = mock(AssignmentRepository.class);
        driverLocationRepository = mock(DriverLocationRepository.class);

        mapService = new DefaultMapService();
        mapService.setConfiguration(config);
        mapService.setRateLimiter(rateLimiter);
        mapService.setMockProvider(mockProvider);
        mapService.setOsrmProvider(osrmProvider);
        mapService.setGenericRestProvider(genericRestProvider);
        mapService.setAssignmentRepository(assignmentRepository);
        mapService.setDriverLocationRepository(driverLocationRepository);
    }

    @Test
    @DisplayName("CalculateDirections: Successful routing using default provider")
    void calculateDirectionsSuccess() {
        RouteDirectionRequest req = new RouteDirectionRequest(
                MapCoordinatesDto.of(10.7769, 106.7009), // HCMC
                MapCoordinatesDto.of(10.7540, 106.6660), // District 5
                "MOTORCYCLE"
        );

        RouteDirectionResponse response = mapService.calculateDirections(req);

        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.isFallback()).isFalse();
        assertThat(response.getDistanceKm()).isGreaterThan(0.0);
        assertThat(response.getDurationMinutes()).isGreaterThan(0L);
        assertThat(response.getGeometry()).isNotEmpty();
        assertThat(response.getSteps()).isNotEmpty();
        assertThat(response.getProvider()).isEqualTo("MOCK");
    }

    @Test
    @DisplayName("CalculateDirections: Invalid coordinate inputs throw MapInvalidRequestException")
    void calculateDirectionsInvalidInput() {
        // Null request
        assertThatThrownBy(() -> mapService.calculateDirections(null))
                .isInstanceOf(MapInvalidRequestException.class)
                .hasMessageContaining("cannot be null");

        // Missing origin
        RouteDirectionRequest missingOrigin = new RouteDirectionRequest(null, MapCoordinatesDto.of(10.75, 106.66));
        assertThatThrownBy(() -> mapService.calculateDirections(missingOrigin))
                .isInstanceOf(MapInvalidRequestException.class)
                .hasMessageContaining("Origin coordinates or origin address must be provided");

        // Out of range latitude (> 90)
        RouteDirectionRequest invalidLat = new RouteDirectionRequest(
                MapCoordinatesDto.of(95.0, 106.70),
                MapCoordinatesDto.of(10.75, 106.66)
        );
        assertThatThrownBy(() -> mapService.calculateDirections(invalidLat))
                .isInstanceOf(MapInvalidRequestException.class)
                .hasMessageContaining("Invalid origin coordinates");

        // Out of range longitude (> 180)
        RouteDirectionRequest invalidLon = new RouteDirectionRequest(
                MapCoordinatesDto.of(10.77, 106.70),
                MapCoordinatesDto.of(10.75, 185.0)
        );
        assertThatThrownBy(() -> mapService.calculateDirections(invalidLon))
                .isInstanceOf(MapInvalidRequestException.class)
                .hasMessageContaining("Invalid destination coordinates");
    }

    @Test
    @DisplayName("CalculateDirections: Downstream timeout activates resilient fallback when enabled")
    void timeoutWithFallbackEnabled() {
        config.setProviderType("OSRM");
        config.setFallbackEnabled(true);
        when(osrmProvider.getDirections(any()))
                .thenThrow(new MapServiceTimeoutException("OSRM server timed out", "OSRM"));

        RouteDirectionRequest req = new RouteDirectionRequest(
                MapCoordinatesDto.of(10.7769, 106.7009),
                MapCoordinatesDto.of(10.7540, 106.6660)
        );

        RouteDirectionResponse response = mapService.calculateDirections(req);

        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.isFallback()).isTrue();
        assertThat(response.getProvider()).isEqualTo("FALLBACK_HAVERSINE");
        assertThat(response.getDistanceKm()).isGreaterThan(0.0);
        assertThat(response.getMessage()).contains("timed out");
    }

    @Test
    @DisplayName("CalculateDirections: Downstream timeout rethrows when fallback is disabled")
    void timeoutWithFallbackDisabled() {
        config.setProviderType("OSRM");
        config.setFallbackEnabled(false);
        when(osrmProvider.getDirections(any()))
                .thenThrow(new MapServiceTimeoutException("OSRM server timed out", "OSRM"));

        RouteDirectionRequest req = new RouteDirectionRequest(
                MapCoordinatesDto.of(10.7769, 106.7009),
                MapCoordinatesDto.of(10.7540, 106.6660)
        );

        assertThatThrownBy(() -> mapService.calculateDirections(req))
                .isInstanceOf(MapServiceTimeoutException.class)
                .hasMessageContaining("timed out");
    }

    @Test
    @DisplayName("CalculateDirections: Downstream 500 activates resilient fallback when enabled")
    void serverErrorWithFallbackEnabled() {
        config.setProviderType("OSRM");
        config.setFallbackEnabled(true);
        when(osrmProvider.getDirections(any()))
                .thenThrow(new MapServiceUnavailableException("OSRM service crashed", "OSRM"));

        RouteDirectionRequest req = new RouteDirectionRequest(
                MapCoordinatesDto.of(10.7769, 106.7009),
                MapCoordinatesDto.of(10.7540, 106.6660)
        );

        RouteDirectionResponse response = mapService.calculateDirections(req);

        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.isFallback()).isTrue();
        assertThat(response.getProvider()).isEqualTo("FALLBACK_HAVERSINE");
        assertThat(response.getDistanceKm()).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("CalculateDirections: Missing API key activates fallback when enabled or throws when disabled")
    void missingApiKeyHandling() {
        config.setProviderType("GENERIC_REST");
        when(genericRestProvider.getDirections(any()))
                .thenThrow(new MapConfigurationException("API key is missing or blank", "GENERIC_REST"));

        RouteDirectionRequest req = new RouteDirectionRequest(
                MapCoordinatesDto.of(10.7769, 106.7009),
                MapCoordinatesDto.of(10.7540, 106.6660)
        );

        // Fallback enabled: safe degradation
        config.setFallbackEnabled(true);
        RouteDirectionResponse response = mapService.calculateDirections(req);
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.isFallback()).isTrue();

        // Fallback disabled: configuration exception is propagated
        config.setFallbackEnabled(false);
        assertThatThrownBy(() -> mapService.calculateDirections(req))
                .isInstanceOf(MapConfigurationException.class)
                .hasMessageContaining("API key is missing");
    }

    @Test
    @DisplayName("GetOrderDirections: Computes directions from assigned driver's GPS to delivery destination")
    void getOrderDirectionsSuccess() {
        String orderId = "ORD-MAP-001";
        Assignment assignment = new Assignment();
        assignment.setOrderId(orderId);
        assignment.setDriverId("drv-01");
        assignment.setDeliveryAddress("123 Nguyễn Trãi, Quận 5, TP.HCM");
        assignment.setStatus(AssignmentStatus.IN_TRANSIT);

        DriverLocation driverLoc = new DriverLocation();
        driverLoc.setDriverId("drv-01");
        driverLoc.setUserId("usr-01");
        driverLoc.setLatitude(10.7769);
        driverLoc.setLongitude(106.7009);
        driverLoc.setTimestamp(Instant.now());

        when(assignmentRepository.findByOrderId(orderId)).thenReturn(Optional.of(assignment));
        when(driverLocationRepository.findByDriverId("drv-01")).thenReturn(Optional.of(driverLoc));

        RouteDirectionResponse directions = mapService.getOrderDirections(orderId);

        assertThat(directions).isNotNull();
        assertThat(directions.isSuccess()).isTrue();
        assertThat(directions.getDistanceKm()).isGreaterThan(0.0);
        assertThat(directions.getSteps()).isNotEmpty();
    }

    @Test
    @DisplayName("GetOrderDirections: Throws AssignmentNotFoundException if order does not exist")
    void getOrderDirectionsNotFound() {
        when(assignmentRepository.findByOrderId("NON_EXISTENT")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mapService.getOrderDirections("NON_EXISTENT"))
                .isInstanceOf(AssignmentNotFoundException.class)
                .hasMessageContaining("No assignment found for order");
    }
}
