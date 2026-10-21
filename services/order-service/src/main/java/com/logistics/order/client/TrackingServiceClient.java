package com.logistics.order.client;

import com.logistics.common.dto.OrderTrackingDto;

public interface TrackingServiceClient {

    /**
     * Calls the Tracking Service (shipment-service) to retrieve real-time driver tracking for an order.
     *
     * @param authHeader Bearer token from the incoming client request
     * @param orderId    ID of the order to track
     * @param allowStale whether to accept stale location coordinates
     * @return OrderTrackingDto containing live coordinates and status
     */
    OrderTrackingDto getOrderTracking(String authHeader, String orderId, boolean allowStale);
}
