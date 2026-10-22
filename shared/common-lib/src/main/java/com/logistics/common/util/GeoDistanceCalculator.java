package com.logistics.common.util;

import com.logistics.common.dto.MapCoordinatesDto;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Shared utility for calculating geographic distances using the Haversine formula.
 * Standardizes all proximity measurements across services to kilometers (km).
 */
public final class GeoDistanceCalculator {

    public static final double EARTH_RADIUS_KM = 6371.0088;

    private GeoDistanceCalculator() {
    }

    /**
     * Calculates the great-circle distance between two geographic coordinates using the Haversine formula.
     *
     * @param lat1 latitude of first point
     * @param lon1 longitude of first point
     * @param lat2 latitude of second point
     * @param lon2 longitude of second point
     * @return distance in kilometers, rounded to 2 decimal places, or null if any coordinate is invalid
     */
    public static Double calculateDistanceKm(Double lat1, Double lon1, Double lat2, Double lon2) {
        if (!isValidCoordinate(lat1, lon1) || !isValidCoordinate(lat2, lon2)) {
            return null;
        }

        // Exact match optimization
        if (Double.compare(lat1, lat2) == 0 && Double.compare(lon1, lon2) == 0) {
            return 0.0;
        }

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double radLat1 = Math.toRadians(lat1);
        double radLat2 = Math.toRadians(lat2);

        double a = Math.sin(dLat / 2.0) * Math.sin(dLat / 2.0)
                + Math.cos(radLat1) * Math.cos(radLat2) * Math.sin(dLon / 2.0) * Math.sin(dLon / 2.0);

        a = Math.min(1.0, Math.max(0.0, a));
        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
        double rawDistance = EARTH_RADIUS_KM * c;

        return BigDecimal.valueOf(rawDistance)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /**
     * Calculates distance between two MapCoordinatesDto objects.
     */
    public static Double calculateDistanceKm(MapCoordinatesDto p1, MapCoordinatesDto p2) {
        if (p1 == null || p2 == null || !p1.isValid() || !p2.isValid()) {
            return null;
        }
        return calculateDistanceKm(p1.getLatitude(), p1.getLongitude(), p2.getLatitude(), p2.getLongitude());
    }

    public static boolean isValidCoordinate(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            return false;
        }
        if (Double.isNaN(latitude) || Double.isInfinite(latitude)
                || Double.isNaN(longitude) || Double.isInfinite(longitude)) {
            return false;
        }
        return latitude >= MapCoordinatesDto.MIN_LATITUDE && latitude <= MapCoordinatesDto.MAX_LATITUDE
                && longitude >= MapCoordinatesDto.MIN_LONGITUDE && longitude <= MapCoordinatesDto.MAX_LONGITUDE;
    }
}
