package com.logistics.shipment.dashboard.client;

import com.logistics.common.dto.OrderMetricsDto;

import java.util.Optional;

/**
 * Interface for retrieving aggregated Order Metrics from the authoritative Order Service (PB-048).
 */
public interface OrderMetricsClient {

    /**
     * Retrieves aggregated order metrics from Order Service.
     *
     * @param authHeader Bearer token
     * @return Optional containing OrderMetricsDto if successful, empty if unavailable or failed
     */
    Optional<OrderMetricsDto> getOrderMetrics(String authHeader);
}
