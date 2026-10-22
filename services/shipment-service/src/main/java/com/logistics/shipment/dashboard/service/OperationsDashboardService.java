package com.logistics.shipment.dashboard.service;

import com.logistics.common.dto.OperationsDashboardDto;

/**
 * Service for aggregating operations dashboard metrics across services (PB-048).
 */
public interface OperationsDashboardService {

    /**
     * Retrieves aggregated operations dashboard data.
     *
     * @param authHeader Bearer token
     * @param forceRefresh whether to bypass cache
     * @return OperationsDashboardDto containing order, driver, and attention metrics
     */
    OperationsDashboardDto getDashboard(String authHeader, boolean forceRefresh);
}
