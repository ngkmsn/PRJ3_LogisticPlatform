package com.logistics.common.dto;

import java.io.Serializable;

/**
 * Response DTO for geocoding / reverse-geocoding operations.
 */
public class GeocodeResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean success;
    private String queryAddress;
    private String formattedAddress;
    private Double latitude;
    private Double longitude;
    private String provider;
    private String message;

    public GeocodeResponse() {
    }

    public static GeocodeResponse success(String queryAddress, String formattedAddress,
                                          Double latitude, Double longitude, String provider) {
        GeocodeResponse response = new GeocodeResponse();
        response.setSuccess(true);
        response.setQueryAddress(queryAddress);
        response.setFormattedAddress(formattedAddress);
        response.setLatitude(latitude);
        response.setLongitude(longitude);
        response.setProvider(provider);
        return response;
    }

    public static GeocodeResponse error(String queryAddress, String message, String provider) {
        GeocodeResponse response = new GeocodeResponse();
        response.setSuccess(false);
        response.setQueryAddress(queryAddress);
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

    public String getQueryAddress() {
        return queryAddress;
    }

    public void setQueryAddress(String queryAddress) {
        this.queryAddress = queryAddress;
    }

    public String getFormattedAddress() {
        return formattedAddress;
    }

    public void setFormattedAddress(String formattedAddress) {
        this.formattedAddress = formattedAddress;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return "GeocodeResponse{" +
                "success=" + success +
                ", formattedAddress='" + formattedAddress + '\'' +
                ", latitude=" + latitude +
                ", longitude=" + longitude +
                ", provider='" + provider + '\'' +
                '}';
    }
}
