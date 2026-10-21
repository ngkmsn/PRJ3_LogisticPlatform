package com.logistics.shipment.dto;

public class StartTransitRequest {

    private String notes;

    public StartTransitRequest() {
    }

    public StartTransitRequest(String notes) {
        this.notes = notes;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
