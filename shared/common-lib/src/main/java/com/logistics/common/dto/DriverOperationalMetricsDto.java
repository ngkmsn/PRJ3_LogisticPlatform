package com.logistics.common.dto;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Aggregated operational metrics for Drivers in the Logistics Platform (PB-048).
 */
public class DriverOperationalMetricsDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private long totalDrivers;
    private long availableDrivers;
    private long busyDrivers;
    private long unavailableDrivers;
    private long activeGpsDrivers;
    private long staleGpsDrivers;
    private Map<String, Long> driversByStatus = new HashMap<>();

    public DriverOperationalMetricsDto() {
    }

    public long getTotalDrivers() {
        return totalDrivers;
    }

    public void setTotalDrivers(long totalDrivers) {
        this.totalDrivers = totalDrivers;
    }

    public long getAvailableDrivers() {
        return availableDrivers;
    }

    public void setAvailableDrivers(long availableDrivers) {
        this.availableDrivers = availableDrivers;
    }

    public long getBusyDrivers() {
        return busyDrivers;
    }

    public void setBusyDrivers(long busyDrivers) {
        this.busyDrivers = busyDrivers;
    }

    public long getUnavailableDrivers() {
        return unavailableDrivers;
    }

    public void setUnavailableDrivers(long unavailableDrivers) {
        this.unavailableDrivers = unavailableDrivers;
    }

    public long getActiveGpsDrivers() {
        return activeGpsDrivers;
    }

    public void setActiveGpsDrivers(long activeGpsDrivers) {
        this.activeGpsDrivers = activeGpsDrivers;
    }

    public long getStaleGpsDrivers() {
        return staleGpsDrivers;
    }

    public void setStaleGpsDrivers(long staleGpsDrivers) {
        this.staleGpsDrivers = staleGpsDrivers;
    }

    public Map<String, Long> getDriversByStatus() {
        return driversByStatus;
    }

    public void setDriversByStatus(Map<String, Long> driversByStatus) {
        this.driversByStatus = driversByStatus != null ? driversByStatus : new HashMap<>();
    }
}
