package com.logistics.shipment.eligibility.util;

import com.logistics.shipment.eligibility.model.GeoLocation;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Utility for calculating geographic distances using the Haversine formula.
 * Standardizes all proximity measurements to kilometers (km).
 */
public final class GeoDistanceCalculator {

    // Mean Earth radius in kilometers (IUGG standard value)
    public static final double EARTH_RADIUS_KM = 6371.0088;

    private GeoDistanceCalculator() {
        // Utility class
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
        return com.logistics.common.util.GeoDistanceCalculator.calculateDistanceKm(lat1, lon1, lat2, lon2);
    }

    /**
     * Calculates the distance between two {@link GeoLocation} objects.
     */
    public static Double calculateDistanceKm(GeoLocation loc1, GeoLocation loc2) {
        if (loc1 == null || loc2 == null || !loc1.isValid() || !loc2.isValid()) {
            return null;
        }
        return calculateDistanceKm(loc1.getLatitude(), loc1.getLongitude(), loc2.getLatitude(), loc2.getLongitude());
    }

    /**
     * Validates if a latitude and longitude pair represents a legitimate geographic location.
     */
    public static boolean isValidCoordinate(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            return false;
        }
        if (Double.isNaN(latitude) || Double.isInfinite(latitude)
                || Double.isNaN(longitude) || Double.isInfinite(longitude)) {
            return false;
        }
        return latitude >= GeoLocation.MIN_LATITUDE && latitude <= GeoLocation.MAX_LATITUDE
                && longitude >= GeoLocation.MIN_LONGITUDE && longitude <= GeoLocation.MAX_LONGITUDE;
    }
}
