package com.logistics.shipment.eligibility.model;

import com.logistics.common.model.DriverAvailability;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a candidate driver evaluated during the auto-assignment eligibility pipeline.
 */
public class EligibleDriver {

    private String driverId;
    private String userId;
    private String fullName;
    private String phoneNumber;
    private String vehicleType;
    private String vehiclePlate;
    private DriverAvailability availability;
    private String status;
    private double score;
    private Double distanceKm;
    private Integer currentWorkload;
    private Double currentLatitude;
    private Double currentLongitude;
    private Instant locationUpdatedAt;
    private Map<String, Object> metadata = new HashMap<>();

    public EligibleDriver() {
    }

    public EligibleDriver(String driverId, String userId, String fullName, String phoneNumber,
                          String vehicleType, String vehiclePlate, DriverAvailability availability,
                          String status) {
        this.driverId = driverId;
        this.userId = userId;
        this.fullName = fullName;
        this.phoneNumber = phoneNumber;
        this.vehicleType = vehicleType;
        this.vehiclePlate = vehiclePlate;
        this.availability = availability;
        this.status = status;
        this.score = 0.0;
    }

    public EligibleDriver(String driverId, String userId, String fullName, String phoneNumber,
                          String vehicleType, String vehiclePlate, DriverAvailability availability,
                          String status, Double currentLatitude, Double currentLongitude,
                          Instant locationUpdatedAt) {
        this(driverId, userId, fullName, phoneNumber, vehicleType, vehiclePlate, availability, status);
        this.currentLatitude = currentLatitude;
        this.currentLongitude = currentLongitude;
        this.locationUpdatedAt = locationUpdatedAt;
    }

    /**
     * Resolves the current {@link GeoLocation} of this driver,
     * checking numeric latitude/longitude fields first, then inspecting metadata.
     */
    public GeoLocation getCurrentGeoLocation() {
        if (currentLatitude != null && currentLongitude != null) {
            GeoLocation loc = GeoLocation.of(currentLatitude, currentLongitude, locationUpdatedAt);
            if (loc.isValid()) {
                return loc;
            }
        }

        if (metadata != null) {
            Object latObj = metadata.get("latitude");
            if (latObj == null) latObj = metadata.get("lat");

            Object lonObj = metadata.get("longitude");
            if (lonObj == null) lonObj = metadata.get("lon");
            if (lonObj == null) lonObj = metadata.get("lng");

            if (latObj instanceof Number latNum && lonObj instanceof Number lonNum) {
                GeoLocation loc = GeoLocation.of(latNum.doubleValue(), lonNum.doubleValue(), locationUpdatedAt);
                if (loc.isValid()) {
                    return loc;
                }
            }

            Object locStr = metadata.get("location");
            if (locStr instanceof String s) {
                GeoLocation parsed = GeoLocation.parse(s);
                if (parsed != null) {
                    return parsed;
                }
            }
        }

        return null;
    }

    public String getDriverId() {
        return driverId;
    }

    public void setDriverId(String driverId) {
        this.driverId = driverId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getVehicleType() {
        return vehicleType;
    }

    public void setVehicleType(String vehicleType) {
        this.vehicleType = vehicleType;
    }

    public String getVehiclePlate() {
        return vehiclePlate;
    }

    public void setVehiclePlate(String vehiclePlate) {
        this.vehiclePlate = vehiclePlate;
    }

    public DriverAvailability getAvailability() {
        return availability;
    }

    public void setAvailability(DriverAvailability availability) {
        this.availability = availability;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public Double getDistanceKm() {
        return distanceKm;
    }

    public void setDistanceKm(Double distanceKm) {
        this.distanceKm = distanceKm;
    }

    public Integer getCurrentWorkload() {
        return currentWorkload;
    }

    public void setCurrentWorkload(Integer currentWorkload) {
        this.currentWorkload = currentWorkload;
    }

    public Double getCurrentLatitude() {
        return currentLatitude;
    }

    public void setCurrentLatitude(Double currentLatitude) {
        this.currentLatitude = currentLatitude;
    }

    public Double getCurrentLongitude() {
        return currentLongitude;
    }

    public void setCurrentLongitude(Double currentLongitude) {
        this.currentLongitude = currentLongitude;
    }

    public Instant getLocationUpdatedAt() {
        return locationUpdatedAt;
    }

    public void setLocationUpdatedAt(Instant locationUpdatedAt) {
        this.locationUpdatedAt = locationUpdatedAt;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public void addMetadata(String key, Object value) {
        if (this.metadata == null) {
            this.metadata = new HashMap<>();
        }
        this.metadata.put(key, value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EligibleDriver that)) return false;
        return Objects.equals(driverId, that.driverId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(driverId);
    }

    @Override
    public String toString() {
        return "EligibleDriver{" +
                "driverId='" + driverId + '\'' +
                ", fullName='" + fullName + '\'' +
                ", vehicleType='" + vehicleType + '\'' +
                ", availability=" + availability +
                ", status='" + status + '\'' +
                ", score=" + score +
                ", distanceKm=" + distanceKm +
                '}';
    }
}
