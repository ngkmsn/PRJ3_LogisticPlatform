package com.logistics.user.dto;

import com.logistics.common.model.DriverAvailability;
import jakarta.validation.constraints.NotNull;

public class UpdateDriverAvailabilityRequest {

    @NotNull(message = "Availability status is required")
    private DriverAvailability availability;

    public UpdateDriverAvailabilityRequest() {
    }

    public UpdateDriverAvailabilityRequest(DriverAvailability availability) {
        this.availability = availability;
    }

    public DriverAvailability getAvailability() {
        return availability;
    }

    public void setAvailability(DriverAvailability availability) {
        this.availability = availability;
    }
}
