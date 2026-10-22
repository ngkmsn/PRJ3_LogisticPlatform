package com.logistics.shipment.map.client;

import com.logistics.common.dto.GeocodeResponse;
import com.logistics.common.dto.RouteDirectionRequest;
import com.logistics.common.dto.RouteDirectionResponse;
import com.logistics.shipment.map.exception.MapException;

/**
 * Interface decoupling map providers (Mock, OSRM, Generic REST, Google Maps)
 * from microservice business logic.
 */
public interface MapProviderClient {

    /**
     * Identifies the provider (e.g., "MOCK", "OSRM", "GENERIC_REST").
     */
    String getProviderName();

    /**
     * Calculates route, distance, duration, polyline coordinates, and turn-by-turn navigation steps.
     */
    RouteDirectionResponse getDirections(RouteDirectionRequest request) throws MapException;

    /**
     * Resolves an address string into latitude and longitude coordinates.
     */
    GeocodeResponse geocode(String address) throws MapException;

    /**
     * Resolves latitude and longitude coordinates into a human-readable address.
     */
    GeocodeResponse reverseGeocode(double latitude, double longitude) throws MapException;
}
