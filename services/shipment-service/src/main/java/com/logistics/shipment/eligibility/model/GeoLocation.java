package com.logistics.shipment.eligibility.model;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Value object representing a geographic location with latitude, longitude, and optional timestamp.
 */
public class GeoLocation {

    public static final double MIN_LATITUDE = -90.0;
    public static final double MAX_LATITUDE = 90.0;
    public static final double MIN_LONGITUDE = -180.0;
    public static final double MAX_LONGITUDE = 180.0;

    // Pattern to match coordinate strings like "10.776889, 106.700806" or "10.776889,106.700806"
    private static final Pattern COORD_PATTERN = Pattern.compile("^\\s*([+-]?\\d+(?:\\.\\d+)?)\\s*,\\s*([+-]?\\d+(?:\\.\\d+)?)\\s*$");

    private final Double latitude;
    private final Double longitude;
    private final Instant timestamp;

    public GeoLocation(Double latitude, Double longitude) {
        this(latitude, longitude, null);
    }

    public GeoLocation(Double latitude, Double longitude, Instant timestamp) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.timestamp = timestamp;
    }

    public static GeoLocation of(Double latitude, Double longitude) {
        return new GeoLocation(latitude, longitude, null);
    }

    public static GeoLocation of(Double latitude, Double longitude, Instant timestamp) {
        return new GeoLocation(latitude, longitude, timestamp);
    }

    /**
     * Attempts to parse coordinates from a string formatted as "lat, lon" or "lat,lon".
     * Returns null if string is null, empty, or does not match coordinate pattern.
     */
    public static GeoLocation parse(String locationStr) {
        if (locationStr == null || locationStr.trim().isEmpty()) {
            return null;
        }
        Matcher matcher = COORD_PATTERN.matcher(locationStr.trim());
        if (matcher.matches()) {
            try {
                double lat = Double.parseDouble(matcher.group(1));
                double lon = Double.parseDouble(matcher.group(2));
                GeoLocation loc = new GeoLocation(lat, lon);
                return loc.isValid() ? loc : null;
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    /**
     * Checks whether both latitude and longitude are non-null, finite numbers
     * and within their valid geographic boundaries.
     */
    public boolean isValid() {
        if (latitude == null || longitude == null) {
            return false;
        }
        if (Double.isNaN(latitude) || Double.isInfinite(latitude)
                || Double.isNaN(longitude) || Double.isInfinite(longitude)) {
            return false;
        }
        return latitude >= MIN_LATITUDE && latitude <= MAX_LATITUDE
                && longitude >= MIN_LONGITUDE && longitude <= MAX_LONGITUDE;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GeoLocation that)) return false;
        return Objects.equals(latitude, that.latitude) &&
                Objects.equals(longitude, that.longitude);
    }

    @Override
    public int hashCode() {
        return Objects.hash(latitude, longitude);
    }

    @Override
    public String toString() {
        return "GeoLocation{" +
                "latitude=" + latitude +
                ", longitude=" + longitude +
                (timestamp != null ? ", timestamp=" + timestamp : "") +
                '}';
    }
}
