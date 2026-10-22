package com.logistics.shipment.map.service;

import com.logistics.common.dto.GeocodeResponse;
import com.logistics.common.dto.MapCoordinatesDto;
import com.logistics.common.dto.RouteDirectionRequest;
import com.logistics.common.dto.RouteDirectionResponse;
import com.logistics.common.dto.RouteStepDto;
import com.logistics.common.util.GeoDistanceCalculator;
import com.logistics.shipment.entity.Assignment;
import com.logistics.shipment.entity.DriverLocation;
import com.logistics.shipment.exception.AssignmentNotFoundException;
import com.logistics.shipment.map.client.GenericRestMapProviderClient;
import com.logistics.shipment.map.client.MapProviderClient;
import com.logistics.shipment.map.client.MockMapProviderClient;
import com.logistics.shipment.map.client.OsrmMapProviderClient;
import com.logistics.shipment.map.config.MapConfiguration;
import com.logistics.shipment.map.exception.MapConfigurationException;
import com.logistics.shipment.map.exception.MapException;
import com.logistics.shipment.map.exception.MapInvalidRequestException;
import com.logistics.shipment.map.exception.MapRateLimitException;
import com.logistics.shipment.map.exception.MapServiceTimeoutException;
import com.logistics.shipment.map.exception.MapServiceUnavailableException;
import com.logistics.shipment.map.limiter.MapRateLimiter;
import com.logistics.shipment.repository.AssignmentRepository;
import com.logistics.shipment.repository.DriverLocationRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Default implementation of {@link MapService} integrating pluggable map providers
 * with resilient fallback handling and rate limiting.
 */
@ApplicationScoped
public class DefaultMapService implements MapService {

    private static final Logger log = LoggerFactory.getLogger(DefaultMapService.class);

    @Inject
    MapConfiguration configuration;

    @Inject
    MapRateLimiter rateLimiter;

    @Inject
    MockMapProviderClient mockProvider;

    @Inject
    OsrmMapProviderClient osrmProvider;

    @Inject
    GenericRestMapProviderClient genericRestProvider;

    @Inject
    AssignmentRepository assignmentRepository;

    @Inject
    DriverLocationRepository driverLocationRepository;

    // Setters for test injection
    public void setConfiguration(MapConfiguration configuration) {
        this.configuration = configuration;
    }

    public void setRateLimiter(MapRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    public void setMockProvider(MockMapProviderClient mockProvider) {
        this.mockProvider = mockProvider;
    }

    public void setOsrmProvider(OsrmMapProviderClient osrmProvider) {
        this.osrmProvider = osrmProvider;
    }

    public void setGenericRestProvider(GenericRestMapProviderClient genericRestProvider) {
        this.genericRestProvider = genericRestProvider;
    }

    public void setAssignmentRepository(AssignmentRepository assignmentRepository) {
        this.assignmentRepository = assignmentRepository;
    }

    public void setDriverLocationRepository(DriverLocationRepository driverLocationRepository) {
        this.driverLocationRepository = driverLocationRepository;
    }

    @Override
    public RouteDirectionResponse calculateDirections(RouteDirectionRequest request) {
        if (request == null) {
            throw new MapInvalidRequestException("Route direction request cannot be null");
        }

        // Validate coordinates / addresses
        validateDirectionRequest(request);

        // Apply rate limiter check to protect downstream provider
        if (rateLimiter != null && !rateLimiter.tryAcquire()) {
            if (configuration.isFallbackEnabled()) {
                log.warn("Map rate limit exceeded; falling back to local geometric route estimation");
                return buildFallbackRoute(request, "Rate limit exceeded (HTTP 429)");
            }
            throw new MapRateLimitException("Rate limit exceeded for map provider requests", configuration.getProviderType());
        }

        MapProviderClient client = resolveProvider(configuration.getProviderType());

        try {
            return client.getDirections(request);
        } catch (MapInvalidRequestException e) {
            // User input errors should not trigger fallback
            throw e;
        } catch (MapConfigurationException e) {
            if (configuration.isFallbackEnabled()) {
                log.warn("Missing or invalid map provider configuration, activating resilient fallback: {}", e.getMessage());
                return buildFallbackRoute(request, e.getMessage());
            }
            throw e;
        } catch (MapServiceTimeoutException e) {
            if (configuration.isFallbackEnabled()) {
                log.warn("Map provider timed out, activating resilient fallback: {}", e.getMessage());
                return buildFallbackRoute(request, "Provider request timed out: " + e.getMessage());
            }
            throw e;
        } catch (MapRateLimitException e) {
            if (configuration.isFallbackEnabled()) {
                log.warn("Map provider rate limited, activating resilient fallback: {}", e.getMessage());
                return buildFallbackRoute(request, "Provider rate limit reached: " + e.getMessage());
            }
            throw e;
        } catch (MapServiceUnavailableException e) {
            if (configuration.isFallbackEnabled()) {
                log.warn("Map provider unavailable, activating resilient fallback: {}", e.getMessage());
                return buildFallbackRoute(request, "Provider unavailable: " + e.getMessage());
            }
            throw e;
        } catch (Exception e) {
            if (configuration.isFallbackEnabled()) {
                log.warn("Unexpected failure from map provider, activating resilient fallback: {}", e.getMessage());
                return buildFallbackRoute(request, "Unexpected error: " + e.getMessage());
            }
            throw new MapServiceUnavailableException("Map provider failure: " + e.getMessage(), e, client.getProviderName());
        }
    }

    @Override
    public GeocodeResponse geocode(String address) {
        if (address == null || address.trim().isEmpty()) {
            throw new MapInvalidRequestException("Address for geocoding cannot be blank");
        }

        MapProviderClient client = resolveProvider(configuration.getProviderType());
        try {
            return client.geocode(address);
        } catch (MapInvalidRequestException e) {
            throw e;
        } catch (MapConfigurationException e) {
            if (configuration.isFallbackEnabled()) {
                log.warn("Provider configuration error during geocoding, falling back to mock dictionary: {}", e.getMessage());
                return mockProvider.geocode(address);
            }
            throw e;
        } catch (Exception e) {
            if (configuration.isFallbackEnabled()) {
                log.warn("Geocoding failed on provider {}, falling back to mock dictionary: {}", client.getProviderName(), e.getMessage());
                return mockProvider.geocode(address);
            }
            throw new MapServiceUnavailableException("Geocoding failed: " + e.getMessage(), e, client.getProviderName());
        }
    }

    @Override
    public GeocodeResponse reverseGeocode(double latitude, double longitude) {
        if (!GeoDistanceCalculator.isValidCoordinate(latitude, longitude)) {
            throw new MapInvalidRequestException("Invalid coordinates for reverse geocoding: lat=" + latitude + ", lon=" + longitude);
        }

        MapProviderClient client = resolveProvider(configuration.getProviderType());
        try {
            return client.reverseGeocode(latitude, longitude);
        } catch (MapInvalidRequestException e) {
            throw e;
        } catch (MapConfigurationException e) {
            if (configuration.isFallbackEnabled()) {
                return mockProvider.reverseGeocode(latitude, longitude);
            }
            throw e;
        } catch (Exception e) {
            if (configuration.isFallbackEnabled()) {
                return mockProvider.reverseGeocode(latitude, longitude);
            }
            throw new MapServiceUnavailableException("Reverse geocoding failed: " + e.getMessage(), e, client.getProviderName());
        }
    }

    @Override
    public RouteDirectionResponse getOrderDirections(String orderId) {
        if (orderId == null || orderId.trim().isEmpty()) {
            throw new MapInvalidRequestException("Order ID cannot be blank");
        }

        Assignment assignment = assignmentRepository.findByOrderId(orderId.trim())
                .orElseThrow(() -> new AssignmentNotFoundException("No assignment found for order: " + orderId));

        // Destination: resolved from deliveryAddress
        MapCoordinatesDto destination = null;
        if (assignment.getDeliveryAddress() != null && !assignment.getDeliveryAddress().isBlank()) {
            GeocodeResponse geo = geocode(assignment.getDeliveryAddress());
            if (geo.isSuccess()) {
                destination = MapCoordinatesDto.of(geo.getLatitude(), geo.getLongitude());
            }
        }
        if (destination == null) {
            destination = MapCoordinatesDto.of(10.7769, 106.7009); // Default city center destination
        }

        // Origin: assigned driver's latest GPS position
        MapCoordinatesDto origin = null;
        if (assignment.getDriverId() != null && !assignment.getDriverId().isBlank()) {
            DriverLocation loc = driverLocationRepository.findByDriverId(assignment.getDriverId().trim()).orElse(null);
            if (loc != null && GeoDistanceCalculator.isValidCoordinate(loc.getLatitude(), loc.getLongitude())) {
                origin = MapCoordinatesDto.of(loc.getLatitude(), loc.getLongitude());
            }
        }

        // If driver hasn't reported GPS yet, use pickup location or nearby hub
        if (origin == null) {
            // Default hub nearby destination
            origin = MapCoordinatesDto.of(destination.getLatitude() + 0.02, destination.getLongitude() - 0.02);
        }

        RouteDirectionRequest request = new RouteDirectionRequest(origin, destination, "MOTORCYCLE");
        request.setDestinationAddress(assignment.getDeliveryAddress());
        return calculateDirections(request);
    }

    private void validateDirectionRequest(RouteDirectionRequest request) {
        boolean hasOrigin = (request.getOrigin() != null) || (request.getOriginAddress() != null && !request.getOriginAddress().isBlank());
        boolean hasDest = (request.getDestination() != null) || (request.getDestinationAddress() != null && !request.getDestinationAddress().isBlank());

        if (!hasOrigin) {
            throw new MapInvalidRequestException("Origin coordinates or origin address must be provided");
        }
        if (!hasDest) {
            throw new MapInvalidRequestException("Destination coordinates or destination address must be provided");
        }

        if (request.getOrigin() != null) {
            if (!request.getOrigin().isValid()) {
                throw new MapInvalidRequestException("Invalid origin coordinates: latitude must be in [-90, 90] and longitude in [-180, 180]");
            }
        }
        if (request.getDestination() != null) {
            if (!request.getDestination().isValid()) {
                throw new MapInvalidRequestException("Invalid destination coordinates: latitude must be in [-90, 90] and longitude in [-180, 180]");
            }
        }
    }

    private MapProviderClient resolveProvider(String providerType) {
        if ("OSRM".equalsIgnoreCase(providerType)) {
            return osrmProvider;
        }
        if ("GENERIC_REST".equalsIgnoreCase(providerType) || "REST".equalsIgnoreCase(providerType)) {
            return genericRestProvider;
        }
        return mockProvider;
    }

    private RouteDirectionResponse buildFallbackRoute(RouteDirectionRequest request, String errorReason) {
        MapCoordinatesDto origin = request.getOrigin();
        if (origin == null && request.getOriginAddress() != null) {
            GeocodeResponse geo = mockProvider.geocode(request.getOriginAddress());
            origin = MapCoordinatesDto.of(geo.getLatitude(), geo.getLongitude());
        }

        MapCoordinatesDto dest = request.getDestination();
        if (dest == null && request.getDestinationAddress() != null) {
            GeocodeResponse geo = mockProvider.geocode(request.getDestinationAddress());
            dest = MapCoordinatesDto.of(geo.getLatitude(), geo.getLongitude());
        }

        if (origin == null || dest == null) {
            origin = MapCoordinatesDto.of(10.7769, 106.7009);
            dest = MapCoordinatesDto.of(10.7800, 106.7050);
        }

        Double straightLineKm = GeoDistanceCalculator.calculateDistanceKm(origin, dest);
        if (straightLineKm == null) straightLineKm = 1.0;

        double roadDistanceKm = BigDecimal.valueOf(Math.max(0.1, straightLineKm * 1.30))
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
        long durationMinutes = Math.max(1, Math.round((roadDistanceKm / 30.0) * 60.0));

        List<MapCoordinatesDto> geom = List.of(
                origin,
                MapCoordinatesDto.of((origin.getLatitude() + dest.getLatitude()) / 2, (origin.getLongitude() + dest.getLongitude()) / 2),
                dest
        );

        List<RouteStepDto> steps = List.of(
                new RouteStepDto("Depart from origin", roadDistanceKm * 500, (double) (durationMinutes * 30), "depart", origin, null),
                new RouteStepDto("Arrive at destination", roadDistanceKm * 500, (double) (durationMinutes * 30), "arrive", null, dest)
        );

        return RouteDirectionResponse.fallback(roadDistanceKm, durationMinutes, geom, steps,
                "FALLBACK_HAVERSINE", "Map provider degraded; calculated via straight-line Haversine fallback (" + errorReason + ")");
    }
}
