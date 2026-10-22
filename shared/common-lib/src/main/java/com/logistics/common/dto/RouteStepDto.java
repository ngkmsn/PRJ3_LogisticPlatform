package com.logistics.common.dto;

import java.io.Serializable;

/**
 * Data Transfer Object representing a turn-by-turn navigation step in a route.
 */
public class RouteStepDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private String instruction;
    private Double distanceMeters;
    private Double durationSeconds;
    private String maneuver;
    private MapCoordinatesDto startLocation;
    private MapCoordinatesDto endLocation;

    public RouteStepDto() {
    }

    public RouteStepDto(String instruction, Double distanceMeters, Double durationSeconds,
                        String maneuver, MapCoordinatesDto startLocation, MapCoordinatesDto endLocation) {
        this.instruction = instruction;
        this.distanceMeters = distanceMeters;
        this.durationSeconds = durationSeconds;
        this.maneuver = maneuver;
        this.startLocation = startLocation;
        this.endLocation = endLocation;
    }

    public String getInstruction() {
        return instruction;
    }

    public void setInstruction(String instruction) {
        this.instruction = instruction;
    }

    public Double getDistanceMeters() {
        return distanceMeters;
    }

    public void setDistanceMeters(Double distanceMeters) {
        this.distanceMeters = distanceMeters;
    }

    public Double getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Double durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public String getManeuver() {
        return maneuver;
    }

    public void setManeuver(String maneuver) {
        this.maneuver = maneuver;
    }

    public MapCoordinatesDto getStartLocation() {
        return startLocation;
    }

    public void setStartLocation(MapCoordinatesDto startLocation) {
        this.startLocation = startLocation;
    }

    public MapCoordinatesDto getEndLocation() {
        return endLocation;
    }

    public void setEndLocation(MapCoordinatesDto endLocation) {
        this.endLocation = endLocation;
    }

    @Override
    public String toString() {
        return "RouteStepDto{" +
                "instruction='" + instruction + '\'' +
                ", distanceMeters=" + distanceMeters +
                ", durationSeconds=" + durationSeconds +
                ", maneuver='" + maneuver + '\'' +
                '}';
    }
}
