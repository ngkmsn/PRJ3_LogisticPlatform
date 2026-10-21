package com.logistics.shipment.dto;

import com.logistics.shipment.entity.DriverLocation;

import java.time.Instant;

public class DriverLocationDto {

    private String id;
    private String driverId;
    private String userId;
    private Double latitude;
    private Double longitude;
    private Double accuracy;
    private Double speed;
    private Double bearing;
    private String orderId;
    private Instant timestamp;
    private Instant createdAt;
    private Instant updatedAt;

    public DriverLocationDto() {
    }

    public DriverLocationDto(String id, String driverId, String userId, Double latitude, Double longitude,
                             Double accuracy, Double speed, Double bearing, String orderId,
                             Instant timestamp, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.driverId = driverId;
        this.userId = userId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracy = accuracy;
        this.speed = speed;
        this.bearing = bearing;
        this.orderId = orderId;
        this.timestamp = timestamp;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static DriverLocationDto from(DriverLocation entity) {
        if (entity == null) {
            return null;
        }
        return new DriverLocationDto(
                entity.getId(),
                entity.getDriverId(),
                entity.getUserId(),
                entity.getLatitude(),
                entity.getLongitude(),
                entity.getAccuracy(),
                entity.getSpeed(),
                entity.getBearing(),
                entity.getOrderId(),
                entity.getTimestamp(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
