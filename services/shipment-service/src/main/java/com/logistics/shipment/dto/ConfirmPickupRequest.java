package com.logistics.shipment.dto;

public class ConfirmPickupRequest {

    private String notes;

    public ConfirmPickupRequest() {
    }

    public ConfirmPickupRequest(String notes) {
        this.notes = notes;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
