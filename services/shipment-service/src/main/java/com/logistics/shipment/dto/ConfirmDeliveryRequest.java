package com.logistics.shipment.dto;

public class ConfirmDeliveryRequest {

    private String notes;
    private String recipientSignature;
    private String proofOfDelivery;

    public ConfirmDeliveryRequest() {
    }

    public ConfirmDeliveryRequest(String notes) {
        this.notes = notes;
    }

    public ConfirmDeliveryRequest(String notes, String proofOfDelivery) {
        this.notes = notes;
        this.proofOfDelivery = proofOfDelivery;
    }

    public ConfirmDeliveryRequest(String notes, String recipientSignature, String proofOfDelivery) {
        this.notes = notes;
        this.recipientSignature = recipientSignature;
        this.proofOfDelivery = proofOfDelivery;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getRecipientSignature() {
        return recipientSignature;
    }

    public void setRecipientSignature(String recipientSignature) {
        this.recipientSignature = recipientSignature;
    }

    public String getProofOfDelivery() {
        return proofOfDelivery;
    }

    public void setProofOfDelivery(String proofOfDelivery) {
        this.proofOfDelivery = proofOfDelivery;
    }
}
