package com.logistics.common.dto;

import java.io.Serializable;
import java.time.Instant;

/**
 * Data Transfer Object representing real-time driver GPS tracking information
 * for a specific order, consumable by customers and authorized operators.
 */
public class OrderTrackingDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private String orderId;
    private String orderNumber;
    private String customerId;
    private String orderStatus;
    private String assignmentStatus;
    private String driverId;
    private String driverName;
    private Double latitude;
    private Double longitude;
    private Instant updatedAt;
    private Double accuracy;
    private Double speed;
    private Double bearing;
    private boolean liveTrackingActive;
    private boolean stale;
    private Long locationAgeMinutes;
    private String staleWarning;
    private String message;

    public OrderTrackingDto() {
    }

    public OrderTrackingDto(String orderId, String orderNumber, String customerId,
                            String orderStatus, String assignmentStatus, String driverId,
                            Double latitude, Double longitude, Instant updatedAt,
                            boolean liveTrackingActive, boolean stale) {
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.customerId = customerId;
        this.orderStatus = orderStatus;
        this.assignmentStatus = assignmentStatus;
        this.driverId = driverId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.updatedAt = updatedAt;
        this.liveTrackingActive = liveTrackingActive;
        this.stale = stale;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(String orderStatus) {
        this.orderStatus = orderStatus;
    }

    public String getAssignmentStatus() {
        return assignmentStatus;
    }

    public void setAssignmentStatus(String assignmentStatus) {
        this.assignmentStatus = assignmentStatus;
    }

    public String getDriverId() {
        return driverId;
    }

    public void setDriverId(String driverId) {
        this.driverId = driverId;
    }

    public String getDriverName() {
        return driverName;
    }

    public void setDriverName(String driverName) {
        this.driverName = driverName;
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

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
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

    public boolean isLiveTrackingActive() {
        return liveTrackingActive;
    }

    public void setLiveTrackingActive(boolean liveTrackingActive) {
        this.liveTrackingActive = liveTrackingActive;
    }

    public boolean isStale() {
        return stale;
    }

    public void setStale(boolean stale) {
        this.stale = stale;
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

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return "OrderTrackingDto{" +
                "orderId='" + orderId + '\'' +
                ", orderNumber='" + orderNumber + '\'' +
                ", customerId='" + customerId + '\'' +
                ", orderStatus='" + orderStatus + '\'' +
                ", assignmentStatus='" + assignmentStatus + '\'' +
                ", driverId='" + driverId + '\'' +
                ", latitude=" + latitude +
                ", longitude=" + longitude +
                ", updatedAt=" + updatedAt +
                ", liveTrackingActive=" + liveTrackingActive +
                ", stale=" + stale +
                ", locationAgeMinutes=" + locationAgeMinutes +
                '}';
    }
}
