package com.logistics.shipment.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public class UpdateDriverLocationRequest {

    @NotNull(message = "Latitude is required")
    @DecimalMin(value = "-90.0", message = "Latitude must be between -90.0 and 90.0 degrees")
    @DecimalMax(value = "90.0", message = "Latitude must be between -90.0 and 90.0 degrees")
    private Double latitude;

    @NotNull(message = "Longitude is required")
    @DecimalMin(value = "-180.0", message = "Longitude must be between -180.0 and 180.0 degrees")
    @DecimalMax(value = "180.0", message = "Longitude must be between -180.0 and 180.0 degrees")
    private Double longitude;

    private Instant timestamp;

    @DecimalMin(value = "0.0", message = "Accuracy must be greater than or equal to 0")
    private Double accuracy;

    @DecimalMin(value = "0.0", message = "Speed must be greater than or equal to 0")
    private Double speed;

    @DecimalMin(value = "0.0", message = "Bearing must be between 0.0 and 360.0 degrees")
    @DecimalMax(value = "360.0", message = "Bearing must be between 0.0 and 360.0 degrees")
    private Double bearing;

    private String orderId;

    public UpdateDriverLocationRequest() {
    }

    public UpdateDriverLocationRequest(Double latitude, Double longitude) {
        this(latitude, longitude, null, null, null, null, null);
    }

    public UpdateDriverLocationRequest(Double latitude, Double longitude, Instant timestamp) {
        this(latitude, longitude, timestamp, null, null, null, null);
    }

    public UpdateDriverLocationRequest(Double latitude, Double longitude, Instant timestamp,
                                       Double accuracy, Double speed, Double bearing, String orderId) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.timestamp = timestamp;
        this.accuracy = accuracy;
        this.speed = speed;
        this.bearing = bearing;
        this.orderId = orderId;
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

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
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
}
