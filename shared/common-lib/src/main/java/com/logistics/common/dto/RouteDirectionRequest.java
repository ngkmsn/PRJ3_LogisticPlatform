package com.logistics.common.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Request DTO for requesting routing and directions between two points or addresses.
 */
public class RouteDirectionRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String MODE_MOTORCYCLE = "MOTORCYCLE";
    public static final String MODE_CAR = "CAR";
    public static final String MODE_TRUCK = "TRUCK";
    public static final String MODE_BICYCLE = "BICYCLE";

    private MapCoordinatesDto origin;
    private MapCoordinatesDto destination;
    private String originAddress;
    private String destinationAddress;
    private List<MapCoordinatesDto> waypoints = new ArrayList<>();
    private String vehicleMode = MODE_MOTORCYCLE;
    private String language = "vi";

    public RouteDirectionRequest() {
    }

    public RouteDirectionRequest(MapCoordinatesDto origin, MapCoordinatesDto destination) {
        this.origin = origin;
        this.destination = destination;
    }

    public RouteDirectionRequest(MapCoordinatesDto origin, MapCoordinatesDto destination, String vehicleMode) {
        this.origin = origin;
        this.destination = destination;
        if (vehicleMode != null && !vehicleMode.isBlank()) {
            this.vehicleMode = vehicleMode.toUpperCase();
        }
    }

    public boolean hasValidCoordinates() {
        return origin != null && origin.isValid() && destination != null && destination.isValid();
    }

    public MapCoordinatesDto getOrigin() {
        return origin;
    }

    public void setOrigin(MapCoordinatesDto origin) {
        this.origin = origin;
    }

    public MapCoordinatesDto getDestination() {
        return destination;
    }

    public void setDestination(MapCoordinatesDto destination) {
        this.destination = destination;
    }

    public String getOriginAddress() {
        return originAddress;
    }

    public void setOriginAddress(String originAddress) {
        this.originAddress = originAddress;
    }

    public String getDestinationAddress() {
        return destinationAddress;
    }

    public void setDestinationAddress(String destinationAddress) {
        this.destinationAddress = destinationAddress;
    }

    public List<MapCoordinatesDto> getWaypoints() {
        return waypoints;
    }

    public void setWaypoints(List<MapCoordinatesDto> waypoints) {
        this.waypoints = waypoints != null ? waypoints : new ArrayList<>();
    }

    public String getVehicleMode() {
        return vehicleMode;
    }

    public void setVehicleMode(String vehicleMode) {
        if (vehicleMode != null && !vehicleMode.isBlank()) {
            this.vehicleMode = vehicleMode.toUpperCase();
        }
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    @Override
    public String toString() {
        return "RouteDirectionRequest{" +
                "origin=" + origin +
                ", destination=" + destination +
                ", vehicleMode='" + vehicleMode + '\'' +
                '}';
    }
}
