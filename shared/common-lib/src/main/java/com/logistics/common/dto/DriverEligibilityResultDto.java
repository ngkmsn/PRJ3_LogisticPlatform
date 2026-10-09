package com.logistics.common.dto;

import com.logistics.common.model.DriverIneligibilityReason;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DriverEligibilityResultDto {

    private String driverId;
    private String userId;
    private boolean eligible;
    private List<DriverIneligibilityReason> ineligibilityReasons;
    private List<String> reasonDescriptions;
    private Instant evaluatedAt;

    public DriverEligibilityResultDto() {
        this.ineligibilityReasons = new ArrayList<>();
        this.reasonDescriptions = new ArrayList<>();
        this.evaluatedAt = Instant.now();
    }

    public DriverEligibilityResultDto(String driverId, String userId, boolean eligible,
                                      List<DriverIneligibilityReason> ineligibilityReasons,
                                      Instant evaluatedAt) {
        this.driverId = driverId;
        this.userId = userId;
        this.eligible = eligible;
        this.ineligibilityReasons = ineligibilityReasons != null ? new ArrayList<>(ineligibilityReasons) : new ArrayList<>();
        this.reasonDescriptions = new ArrayList<>();
        if (ineligibilityReasons != null) {
            for (DriverIneligibilityReason reason : ineligibilityReasons) {
                if (reason != null) {
                    this.reasonDescriptions.add(reason.getDefaultDescription());
                }
            }
        }
        this.evaluatedAt = evaluatedAt != null ? evaluatedAt : Instant.now();
    }

    public static DriverEligibilityResultDto eligible(String driverId, String userId) {
        return new DriverEligibilityResultDto(driverId, userId, true, Collections.emptyList(), Instant.now());
    }

    public static DriverEligibilityResultDto notEligible(String driverId, String userId, List<DriverIneligibilityReason> reasons) {
        return new DriverEligibilityResultDto(driverId, userId, false, reasons, Instant.now());
    }

    public static DriverEligibilityResultDto systemError(String driverId, String userId, String errorMessage) {
        DriverEligibilityResultDto dto = new DriverEligibilityResultDto(
                driverId, userId, false, List.of(DriverIneligibilityReason.SYSTEM_ERROR), Instant.now()
        );
        if (errorMessage != null && !errorMessage.isBlank()) {
            dto.getReasonDescriptions().add(errorMessage);
        }
        return dto;
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

    public boolean isEligible() {
        return eligible;
    }

    public void setEligible(boolean eligible) {
        this.eligible = eligible;
    }

    public List<DriverIneligibilityReason> getIneligibilityReasons() {
        return ineligibilityReasons;
    }

    public void setIneligibilityReasons(List<DriverIneligibilityReason> ineligibilityReasons) {
        this.ineligibilityReasons = ineligibilityReasons != null ? ineligibilityReasons : new ArrayList<>();
    }

    public List<String> getReasonDescriptions() {
        return reasonDescriptions;
    }

    public void setReasonDescriptions(List<String> reasonDescriptions) {
        this.reasonDescriptions = reasonDescriptions != null ? reasonDescriptions : new ArrayList<>();
    }

    public Instant getEvaluatedAt() {
        return evaluatedAt;
    }

    public void setEvaluatedAt(Instant evaluatedAt) {
        this.evaluatedAt = evaluatedAt;
    }
}
