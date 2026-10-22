package com.logistics.shipment.map.service;

import com.logistics.common.dto.GeocodeResponse;
import com.logistics.common.dto.RouteDirectionRequest;
import com.logistics.common.dto.RouteDirectionResponse;

/**
 * High-level business service for map data, routing, and directions (PB-047).
 * Provides resilient routing with automatic fallback so that downstream logistics
 * operations (dispatching, tracking, order delivery) never crash when the external
 * map service is degraded or offline.
 */
public interface MapService {

    /**
     * Calculates route directions, distances, durations, and turn-by-turn navigation steps.
     * Automatically applies fallback calculation if the downstream provider fails and fallback is enabled.
     */
    RouteDirectionResponse calculateDirections(RouteDirectionRequest request);

    /**
     * Geocodes an address string to geographic coordinates.
     */
    GeocodeResponse geocode(String address);

    /**
     * Reverse geocodes latitude/longitude coordinates to a formatted address.
     */
    GeocodeResponse reverseGeocode(double latitude, double longitude);

    /**
     * Calculates real-time directions from an assigned driver's latest GPS position
     * to the delivery destination for a specific order.
     */
    RouteDirectionResponse getOrderDirections(String orderId);
}
