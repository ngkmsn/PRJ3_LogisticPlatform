package com.logistics.shipment.dto;

import jakarta.validation.constraints.NotBlank;

public class ReassignDriverRequest {

    @NotBlank(message = "newDriverId must not be blank")
    private String newDriverId;

    @NotBlank(message = "reason must not be blank")
    private String reason;

    private String notes;

    public ReassignDriverRequest() {
    }

    public ReassignDriverRequest(String newDriverId, String reason) {
        this.newDriverId = newDriverId;
        this.reason = reason;
    }

    public ReassignDriverRequest(String newDriverId, String reason, String notes) {
        this.newDriverId = newDriverId;
        this.reason = reason;
        this.notes = notes;
    }

    public String getNewDriverId() {
        return newDriverId;
    }

    public void setNewDriverId(String newDriverId) {
        this.newDriverId = newDriverId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
