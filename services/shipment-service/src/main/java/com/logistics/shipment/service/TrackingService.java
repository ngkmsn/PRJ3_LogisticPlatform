package com.logistics.shipment.service;

import com.logistics.shipment.dto.UpdateDriverLocationRequest;
import com.logistics.shipment.entity.DriverLocation;

import java.util.Optional;

public interface TrackingService {

    /**
     * Records or updates the latest real-time GPS location of an authenticated Driver (PB-044).
     * Validates that the caller has DRIVER role, coordinates are within valid geographic ranges,
     * timestamp is not in the future beyond clock skew, and incoming timestamp is not older
     * than the currently recorded location (preventing stale out-of-order data).
     *
     * @param authHeader Bearer token
     * @param request    GPS location update request containing latitude, longitude, timestamp, etc.
     * @return updated or persisted DriverLocation entity
     */
    DriverLocation recordDriverLocation(String authHeader, UpdateDriverLocationRequest request);

    /**
     * Retrieves the latest recorded GPS location of the currently authenticated Driver.
     *
     * @param authHeader Bearer token
     * @return optional DriverLocation
     */
    Optional<DriverLocation> getCurrentDriverLocation(String authHeader);

    /**
     * Retrieves the latest recorded GPS location of a specific Driver.
     * Requires DRIVER, DISPATCHER, or ADMIN role.
     *
     * @param authHeader Bearer token
     * @param driverId   target driver identifier
     * @return optional DriverLocation
     */
    Optional<DriverLocation> getDriverLocation(String authHeader, String driverId);

    /**
     * Retrieves the latest real-time GPS location of the Driver assigned to a specific Order (PB-045).
     *
     * @param authHeader Bearer token
     * @param orderId    target order identifier
     * @return OrderTrackingDto containing live coordinates and status
     */
    com.logistics.common.dto.OrderTrackingDto getOrderTracking(String authHeader, String orderId);

    /**
     * Retrieves the latest real-time GPS location of the Driver assigned to a specific Order (PB-045).
     *
     * @param authHeader Bearer token
     * @param orderId    target order identifier
     * @param allowStale whether to return stale locations (> 30 minutes) with warning instead of throwing error
     * @return OrderTrackingDto containing live coordinates and status
     */
    com.logistics.common.dto.OrderTrackingDto getOrderTracking(String authHeader, String orderId, boolean allowStale);

    /**
     * Queries driver monitoring data for Operations (PB-046).
     * Combines driver profile and status from User Service, active order assignments from Shipment Service,
     * and real-time GPS locations from Tracking Service with staleness indicators, filters, and pagination.
     * Accessible only by DISPATCHER and ADMIN.
     *
     * @param authHeader     Bearer token of DISPATCHER or ADMIN
     * @param page           page index (0-based)
     * @param size           page size (max 100)
     * @param search         optional search string matching driver name, phone, id, or vehicle plate
     * @param status         optional status filter (AVAILABLE, BUSY, UNAVAILABLE)
     * @param hasActiveOrder optional filter for drivers with/without active order
     * @param hasGps         optional filter for drivers with/without GPS fix
     * @return PagedResponse of DriverMonitoringDto
     */
    com.logistics.shipment.dto.PagedResponse<com.logistics.common.dto.DriverMonitoringDto> getDriversMonitoring(
            String authHeader,
            int page,
            int size,
            String search,
            String status,
            Boolean hasActiveOrder,
            Boolean hasGps);

    /**
     * Retrieves detailed monitoring data for a specific driver for Operations (PB-046).
     * Accessible only by DISPATCHER and ADMIN.
     *
     * @param authHeader Bearer token of DISPATCHER or ADMIN
     * @param driverId   target driver identifier
     * @return DriverMonitoringDto
     */
    com.logistics.common.dto.DriverMonitoringDto getDriverMonitoringDetails(String authHeader, String driverId);
}
