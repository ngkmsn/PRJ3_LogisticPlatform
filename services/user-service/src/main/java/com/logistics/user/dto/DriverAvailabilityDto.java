package com.logistics.user.dto;

import com.logistics.common.model.DriverAvailability;
import com.logistics.user.entity.DriverProfile;

import java.time.Instant;

public class DriverAvailabilityDto {

    private String driverId;
    private String userId;
    private DriverAvailability availability;
    private Instant updatedAt;

    public DriverAvailabilityDto() {
    }

    public DriverAvailabilityDto(String driverId, String userId, DriverAvailability availability, Instant updatedAt) {
        this.driverId = driverId;
        this.userId = userId;
        this.availability = availability;
        this.updatedAt = updatedAt;
    }

    public static DriverAvailabilityDto from(DriverProfile profile) {
        return new DriverAvailabilityDto(
                profile.getId(),
                profile.getUserId(),
                profile.getAvailability(),
                profile.getUpdatedAt()
        );
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

    public DriverAvailability getAvailability() {
        return availability;
    }

    public void setAvailability(DriverAvailability availability) {
        this.availability = availability;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
