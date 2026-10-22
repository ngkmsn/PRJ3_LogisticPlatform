package com.logistics.common.dto;

import java.io.Serializable;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Data Transfer Object representing a 2D geographic coordinate (latitude, longitude).
 */
public class MapCoordinatesDto implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final double MIN_LATITUDE = -90.0;
    public static final double MAX_LATITUDE = 90.0;
    public static final double MIN_LONGITUDE = -180.0;
    public static final double MAX_LONGITUDE = 180.0;

    private static final Pattern COORD_PATTERN = Pattern.compile("^\\s*([+-]?\\d+(?:\\.\\d+)?)\\s*,\\s*([+-]?\\d+(?:\\.\\d+)?)\\s*$");

    private Double latitude;
    private Double longitude;

    public MapCoordinatesDto() {
    }

    public MapCoordinatesDto(Double latitude, Double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public static MapCoordinatesDto of(Double latitude, Double longitude) {
        return new MapCoordinatesDto(latitude, longitude);
    }

    /**
     * Parses "lat, lon" or "lat,lon" into a MapCoordinatesDto.
     * Returns null if input string is null, empty, or invalid.
     */
    public static MapCoordinatesDto parse(String coordStr) {
        if (coordStr == null || coordStr.trim().isEmpty()) {
            return null;
        }
        Matcher matcher = COORD_PATTERN.matcher(coordStr.trim());
        if (matcher.matches()) {
            try {
                double lat = Double.parseDouble(matcher.group(1));
                double lon = Double.parseDouble(matcher.group(2));
                MapCoordinatesDto coords = new MapCoordinatesDto(lat, lon);
                return coords.isValid() ? coords : null;
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

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

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MapCoordinatesDto that)) return false;
        return Objects.equals(latitude, that.latitude) && Objects.equals(longitude, that.longitude);
    }

    @Override
    public int hashCode() {
        return Objects.hash(latitude, longitude);
    }

    @Override
    public String toString() {
        return "MapCoordinatesDto{" +
                "latitude=" + latitude +
                ", longitude=" + longitude +
                '}';
    }
}
