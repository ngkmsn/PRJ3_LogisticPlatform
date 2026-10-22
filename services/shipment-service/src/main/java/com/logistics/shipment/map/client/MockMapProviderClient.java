package com.logistics.shipment.map.client;

import com.logistics.common.dto.GeocodeResponse;
import com.logistics.common.dto.MapCoordinatesDto;
import com.logistics.common.dto.RouteDirectionRequest;
import com.logistics.common.dto.RouteDirectionResponse;
import com.logistics.common.dto.RouteStepDto;
import com.logistics.common.util.GeoDistanceCalculator;
import com.logistics.shipment.map.exception.MapInvalidRequestException;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Default mock map provider client for local development, offline operation,
 * and automated testing without external billing or internet dependencies.
 */
@ApplicationScoped
public class MockMapProviderClient implements MapProviderClient {

    public static final String PROVIDER_NAME = "MOCK";
    private static final double URBAN_ROAD_FACTOR = 1.28;

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public RouteDirectionResponse getDirections(RouteDirectionRequest request) {
        if (request == null) {
            throw new MapInvalidRequestException("Route direction request cannot be null", PROVIDER_NAME);
        }

        MapCoordinatesDto origin = resolveOrigin(request);
        MapCoordinatesDto destination = resolveDestination(request);

        if (origin == null || !origin.isValid()) {
            throw new MapInvalidRequestException("Invalid or missing origin coordinates", PROVIDER_NAME);
        }
        if (destination == null || !destination.isValid()) {
            throw new MapInvalidRequestException("Invalid or missing destination coordinates", PROVIDER_NAME);
        }

        // Calculate straight-line distance and apply urban road curvature factor
        Double straightLineKm = GeoDistanceCalculator.calculateDistanceKm(origin, destination);
        if (straightLineKm == null) {
            straightLineKm = 0.0;
        }

        double roadDistanceKm = BigDecimal.valueOf(Math.max(0.1, straightLineKm * URBAN_ROAD_FACTOR))
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
        double distanceMeters = roadDistanceKm * 1000.0;

        // Speed in km/h based on transport mode
        double speedKmH = resolveSpeed(request.getVehicleMode());
        long durationSeconds = Math.max(60L, Math.round((roadDistanceKm / speedKmH) * 3600.0));

        // Generate geometry coordinates
        List<MapCoordinatesDto> geometry = generateRouteGeometry(origin, destination);

        // Generate turn-by-turn navigation steps
        List<RouteStepDto> steps = generateRouteSteps(origin, destination, distanceMeters, durationSeconds, geometry);

        String summary = String.format(Locale.ROOT, "Route from (%.4f, %.4f) to (%.4f, %.4f) via %s",
                origin.getLatitude(), origin.getLongitude(),
                destination.getLatitude(), destination.getLongitude(),
                request.getVehicleMode() != null ? request.getVehicleMode() : "MOTORCYCLE");

        return RouteDirectionResponse.success(distanceMeters, durationSeconds, geometry, steps, summary, PROVIDER_NAME);
    }

    @Override
    public GeocodeResponse geocode(String address) {
        if (address == null || address.trim().isEmpty()) {
            throw new MapInvalidRequestException("Address to geocode cannot be blank", PROVIDER_NAME);
        }

        String query = address.trim();

        // Check if string is already formatted as "lat, lon"
        MapCoordinatesDto parsed = MapCoordinatesDto.parse(query);
        if (parsed != null) {
            return GeocodeResponse.success(query, "Coordinates (" + parsed.getLatitude() + ", " + parsed.getLongitude() + ")",
                    parsed.getLatitude(), parsed.getLongitude(), PROVIDER_NAME);
        }

        String lower = query.toLowerCase(Locale.ROOT);
        double lat;
        double lon;
        String formatted;

        if (lower.contains("hoàn kiếm") || lower.contains("hoan kiem")) {
            lat = 21.0288;
            lon = 105.8525;
            formatted = "Hoàn Kiếm, Hà Nội, Vietnam";
        } else if (lower.contains("ba đình") || lower.contains("ba dinh")) {
            lat = 21.0341;
            lon = 105.8242;
            formatted = "Ba Đình, Hà Nội, Vietnam";
        } else if (lower.contains("cầu giấy") || lower.contains("cau giay")) {
            lat = 21.0362;
            lon = 105.7905;
            formatted = "Cầu Giấy, Hà Nội, Vietnam";
        } else if (lower.contains("hà nội") || lower.contains("hanoi")) {
            lat = 21.0285;
            lon = 105.8542;
            formatted = "Hà Nội, Vietnam";
        } else if (lower.contains("quận 1") || lower.contains("quan 1") || lower.contains("district 1")) {
            lat = 10.7756;
            lon = 106.7004;
            formatted = "Quận 1, TP. Hồ Chí Minh, Vietnam";
        } else if (lower.contains("quận 5") || lower.contains("quan 5")) {
            lat = 10.7540;
            lon = 106.6660;
            formatted = "Quận 5, TP. Hồ Chí Minh, Vietnam";
        } else if (lower.contains("nguyễn trãi") || lower.contains("nguyen trai")) {
            lat = 10.7558;
            lon = 106.6675;
            formatted = "Đường Nguyễn Trãi, TP. Hồ Chí Minh, Vietnam";
        } else if (lower.contains("hồ chí minh") || lower.contains("ho chi minh") || lower.contains("hcm") || lower.contains("sài gòn") || lower.contains("saigon")) {
            lat = 10.7769;
            lon = 106.7009;
            formatted = "TP. Hồ Chí Minh, Vietnam";
        } else if (lower.contains("đà nẵng") || lower.contains("da nang")) {
            lat = 16.0544;
            lon = 108.2022;
            formatted = "Đà Nẵng, Vietnam";
        } else {
            // Deterministic hash coordinates within Vietnam range (lat ~10.7 to 21.0, lon ~105.8 to 106.7)
            int hash = Math.abs(query.hashCode());
            lat = 10.7000 + (hash % 1000) * 0.001;
            lon = 106.6000 + ((hash / 1000) % 1000) * 0.001;
            formatted = query + ", Vietnam";
        }

        return GeocodeResponse.success(query, formatted, lat, lon, PROVIDER_NAME);
    }

    @Override
    public GeocodeResponse reverseGeocode(double latitude, double longitude) {
        if (!GeoDistanceCalculator.isValidCoordinate(latitude, longitude)) {
            throw new MapInvalidRequestException("Invalid coordinates for reverse geocoding: " + latitude + ", " + longitude, PROVIDER_NAME);
        }
        String formatted = String.format(Locale.ROOT, "Location near %.4f, %.4f, Vietnam", latitude, longitude);
        return GeocodeResponse.success(latitude + "," + longitude, formatted, latitude, longitude, PROVIDER_NAME);
    }

    private MapCoordinatesDto resolveOrigin(RouteDirectionRequest request) {
        if (request.getOrigin() != null && request.getOrigin().isValid()) {
            return request.getOrigin();
        }
        if (request.getOriginAddress() != null && !request.getOriginAddress().isBlank()) {
            GeocodeResponse geo = geocode(request.getOriginAddress());
            return MapCoordinatesDto.of(geo.getLatitude(), geo.getLongitude());
        }
        return null;
    }

    private MapCoordinatesDto resolveDestination(RouteDirectionRequest request) {
        if (request.getDestination() != null && request.getDestination().isValid()) {
            return request.getDestination();
        }
        if (request.getDestinationAddress() != null && !request.getDestinationAddress().isBlank()) {
            GeocodeResponse geo = geocode(request.getDestinationAddress());
            return MapCoordinatesDto.of(geo.getLatitude(), geo.getLongitude());
        }
        return null;
    }

    private double resolveSpeed(String vehicleMode) {
        if (vehicleMode == null) return 30.0;
        return switch (vehicleMode.toUpperCase()) {
            case "CAR" -> 38.0;
            case "TRUCK" -> 26.0;
            case "BICYCLE" -> 16.0;
            default -> 32.0; // MOTORCYCLE
        };
    }

    private List<MapCoordinatesDto> generateRouteGeometry(MapCoordinatesDto origin, MapCoordinatesDto dest) {
        List<MapCoordinatesDto> points = new ArrayList<>();
        points.add(origin);
        int segments = 5;
        double dLat = (dest.getLatitude() - origin.getLatitude()) / segments;
        double dLon = (dest.getLongitude() - origin.getLongitude()) / segments;

        for (int i = 1; i < segments; i++) {
            // add slight lateral wiggle to simulate realistic roads
            double offset = (i % 2 == 1 ? 0.0005 : -0.0005);
            points.add(MapCoordinatesDto.of(
                    origin.getLatitude() + dLat * i + offset,
                    origin.getLongitude() + dLon * i + offset
            ));
        }
        points.add(dest);
        return points;
    }

    private List<RouteStepDto> generateRouteSteps(MapCoordinatesDto origin, MapCoordinatesDto dest,
                                                  double totalDistMeters, long totalDurationSec,
                                                  List<MapCoordinatesDto> geometry) {
        List<RouteStepDto> steps = new ArrayList<>();

        MapCoordinatesDto mid1 = geometry.size() > 2 ? geometry.get(1) : origin;
        MapCoordinatesDto mid2 = geometry.size() > 3 ? geometry.get(3) : dest;

        double d1 = totalDistMeters * 0.2;
        double d2 = totalDistMeters * 0.5;
        double d3 = totalDistMeters * 0.3;

        double t1 = totalDurationSec * 0.2;
        double t2 = totalDurationSec * 0.5;
        double t3 = totalDurationSec * 0.3;

        steps.add(new RouteStepDto(
                "Depart from origin heading towards main arterial route",
                d1, t1, "depart", origin, mid1
        ));
        steps.add(new RouteStepDto(
                String.format(Locale.ROOT, "Continue straight along primary road for %.1f km", (d2 / 1000.0)),
                d2, t2, "straight", mid1, mid2
        ));
        steps.add(new RouteStepDto(
                "Turn right towards destination address",
                d3 * 0.7, t3 * 0.7, "turn-right", mid2, dest
        ));
        steps.add(new RouteStepDto(
                "Arrive at destination on the right",
                d3 * 0.3, t3 * 0.3, "arrive", mid2, dest
        ));

        return steps;
    }
}
