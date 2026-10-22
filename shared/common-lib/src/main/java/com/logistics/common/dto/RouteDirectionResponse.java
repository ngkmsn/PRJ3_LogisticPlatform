package com.logistics.common.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Transfer Object representing the calculated route, distance, duration,
 * polyline geometry coordinates, and turn-by-turn navigation instructions.
 */
public class RouteDirectionResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean success;
    private Double distanceMeters;
    private Double distanceKm;
    private Long durationSeconds;
    private Long durationMinutes;
    private String summary;
    private List<MapCoordinatesDto> geometry = new ArrayList<>();
    private List<RouteStepDto> steps = new ArrayList<>();
    private String provider;
    private boolean fallback;
    private String message;

    public RouteDirectionResponse() {
    }

    public static RouteDirectionResponse success(Double distanceMeters, Long durationSeconds,
                                                 List<MapCoordinatesDto> geometry, List<RouteStepDto> steps,
                                                 String summary, String provider) {
        RouteDirectionResponse response = new RouteDirectionResponse();
        response.setSuccess(true);
        response.setDistanceMeters(distanceMeters);
        if (distanceMeters != null) {
            response.setDistanceKm(Math.round((distanceMeters / 1000.0) * 100.0) / 100.0);
        }
        response.setDurationSeconds(durationSeconds);
        if (durationSeconds != null) {
            response.setDurationMinutes(Math.max(1, Math.round(durationSeconds / 60.0)));
        }
        response.setGeometry(geometry != null ? geometry : new ArrayList<>());
        response.setSteps(steps != null ? steps : new ArrayList<>());
        response.setSummary(summary);
        response.setProvider(provider);
        response.setFallback(false);
        return response;
    }

    public static RouteDirectionResponse fallback(Double distanceKm, Long durationMinutes,
                                                  List<MapCoordinatesDto> geometry, List<RouteStepDto> steps,
                                                  String provider, String message) {
        RouteDirectionResponse response = new RouteDirectionResponse();
        response.setSuccess(true);
        response.setFallback(true);
        response.setDistanceKm(distanceKm);
        if (distanceKm != null) {
            response.setDistanceMeters(distanceKm * 1000.0);
        }
        response.setDurationMinutes(durationMinutes);
        if (durationMinutes != null) {
            response.setDurationSeconds(durationMinutes * 60L);
        }
        response.setGeometry(geometry != null ? geometry : new ArrayList<>());
        response.setSteps(steps != null ? steps : new ArrayList<>());
        response.setSummary("Estimated route via " + provider);
        response.setProvider(provider);
        response.setMessage(message);
        return response;
    }

    public static RouteDirectionResponse error(String message, String provider) {
        RouteDirectionResponse response = new RouteDirectionResponse();
        response.setSuccess(false);
        response.setMessage(message);
        response.setProvider(provider);
        return response;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public Double getDistanceMeters() {
        return distanceMeters;
    }

    public void setDistanceMeters(Double distanceMeters) {
        this.distanceMeters = distanceMeters;
    }

    public Double getDistanceKm() {
        return distanceKm;
    }

    public void setDistanceKm(Double distanceKm) {
        this.distanceKm = distanceKm;
    }

    public Long getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Long durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public Long getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Long durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<MapCoordinatesDto> getGeometry() {
        return geometry;
    }

    public void setGeometry(List<MapCoordinatesDto> geometry) {
        this.geometry = geometry != null ? geometry : new ArrayList<>();
    }

    public List<RouteStepDto> getSteps() {
        return steps;
    }

    public void setSteps(List<RouteStepDto> steps) {
        this.steps = steps != null ? steps : new ArrayList<>();
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public boolean isFallback() {
        return fallback;
    }

    public void setFallback(boolean fallback) {
        this.fallback = fallback;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return "RouteDirectionResponse{" +
                "success=" + success +
                ", distanceKm=" + distanceKm +
                ", durationMinutes=" + durationMinutes +
                ", provider='" + provider + '\'' +
                ", fallback=" + fallback +
                '}';
    }
}
