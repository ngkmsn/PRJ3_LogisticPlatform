package com.logistics.common.dto;

import com.logistics.common.model.DriverAvailability;

import java.time.Instant;

/**
 * Data transfer object representing driver operational and real-time monitoring information (PB-046).
 * Combines driver profile and availability (from User Service), active order assignment (from Shipment Service),
 * and real-time GPS tracking fixes with staleness indicators (from Tracking Service).
 */
public class DriverMonitoringDto {

    private String driverId;
    private String userId;
    private String fullName;
    private String phoneNumber;
    private String vehicleType;
    private String vehiclePlate;
    private String status;                  // Driver account status (ACTIVE, INACTIVE, etc.)
    private DriverAvailability availability; // AVAILABLE, UNAVAILABLE
    private String operationalStatus;       // AVAILABLE, BUSY, UNAVAILABLE

    // Active order information
    private boolean hasActiveOrder;
    private String activeOrderId;
    private String activeOrderNumber;
    private String activeOrderStatus;       // ASSIGNED, PICKED_UP, IN_TRANSIT

    // Real-time GPS location
    private boolean hasGps;
    private Double latitude;
    private Double longitude;
    private Instant gpsUpdatedAt;
    private Double accuracy;
    private Double speed;
    private Double bearing;

    // Freshness / Staleness
    private boolean locationStale;
    private Long locationAgeMinutes;
    private String staleWarning;

    public DriverMonitoringDto() {
        this.hasActiveOrder = false;
        this.hasGps = false;
        this.locationStale = false;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public DriverAvailability getAvailability() {
        return availability;
    }

    public void setAvailability(DriverAvailability availability) {
        this.availability = availability;
    }

    public String getOperationalStatus() {
        return operationalStatus;
    }

    public void setOperationalStatus(String operationalStatus) {
        this.operationalStatus = operationalStatus;
    }

    public boolean isHasActiveOrder() {
        return hasActiveOrder;
    }

    public void setHasActiveOrder(boolean hasActiveOrder) {
        this.hasActiveOrder = hasActiveOrder;
    }

    public String getActiveOrderId() {
        return activeOrderId;
    }

    public void setActiveOrderId(String activeOrderId) {
        this.activeOrderId = activeOrderId;
    }

    public String getActiveOrderNumber() {
        return activeOrderNumber;
    }

    public void setActiveOrderNumber(String activeOrderNumber) {
        this.activeOrderNumber = activeOrderNumber;
    }

    public String getActiveOrderStatus() {
        return activeOrderStatus;
    }

    public void setActiveOrderStatus(String activeOrderStatus) {
        this.activeOrderStatus = activeOrderStatus;
    }

    public boolean isHasGps() {
        return hasGps;
    }

    public void setHasGps(boolean hasGps) {
        this.hasGps = hasGps;
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

    public Instant getGpsUpdatedAt() {
        return gpsUpdatedAt;
    }

    public void setGpsUpdatedAt(Instant gpsUpdatedAt) {
        this.gpsUpdatedAt = gpsUpdatedAt;
    }

    public Double getAccuracy() {
        return accuracy;
    }

    public void setAccuracy(Double accuracy) {
        this.accuracy = accuracy;
    }

    public Double getSpeed() {
        return speed;
    }

    public void setSpeed(Double speed) {
        this.speed = speed;
    }

    public Double getBearing() {
        return bearing;
    }

    public void setBearing(Double bearing) {
        this.bearing = bearing;
    }

    public boolean isLocationStale() {
        return locationStale;
    }

    public void setLocationStale(boolean locationStale) {
        this.locationStale = locationStale;
    }

    public Long getLocationAgeMinutes() {
        return locationAgeMinutes;
    }

    public void setLocationAgeMinutes(Long locationAgeMinutes) {
        this.locationAgeMinutes = locationAgeMinutes;
    }

    public String getStaleWarning() {
        return staleWarning;
    }

    public void setStaleWarning(String staleWarning) {
        this.staleWarning = staleWarning;
    }
}
