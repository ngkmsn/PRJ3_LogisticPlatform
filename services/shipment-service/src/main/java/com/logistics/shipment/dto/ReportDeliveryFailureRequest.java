package com.logistics.shipment.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Request payload for reporting delivery failure (PB-042).
 */
public class ReportDeliveryFailureRequest {

    private String failureReason;
    private String reason;
    private String notes;

    public ReportDeliveryFailureRequest() {
    }

    public ReportDeliveryFailureRequest(String reason) {
        this.reason = reason;
        this.failureReason = reason;
    }

    public ReportDeliveryFailureRequest(String failureReason, String notes) {
        this.failureReason = failureReason;
        this.notes = notes;
    }

    public ReportDeliveryFailureRequest(String failureReason, String reason, String notes) {
        this.failureReason = failureReason;
        this.reason = reason;
        this.notes = notes;
    }

    @JsonIgnore
    public String getEffectiveReason() {
        if (failureReason != null && !failureReason.trim().isEmpty()) {
            return failureReason.trim();
        }
        if (reason != null && !reason.trim().isEmpty()) {
            return reason.trim();
        }
        return null;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
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
