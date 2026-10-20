package com.logistics.shipment.dto;

import jakarta.validation.constraints.NotBlank;

public class ManualAssignDriverRequest {

    @NotBlank(message = "driverId must not be blank")
    private String driverId;

    private String notes;

    public ManualAssignDriverRequest() {
    }

    public ManualAssignDriverRequest(String driverId) {
        this.driverId = driverId;
    }

    public ManualAssignDriverRequest(String driverId, String notes) {
        this.driverId = driverId;
        this.notes = notes;
    }

    public String getDriverId() {
        return driverId;
    }

    public void setDriverId(String driverId) {
        this.driverId = driverId;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
