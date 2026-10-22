package com.logistics.common.dto;

import java.io.Serializable;

/**
 * Request DTO for geocoding an address into latitude and longitude coordinates.
 */
public class GeocodeRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String address;

    public GeocodeRequest() {
    }

    public GeocodeRequest(String address) {
        this.address = address;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    @Override
    public String toString() {
        return "GeocodeRequest{" +
                "address='" + address + '\'' +
                '}';
    }
}
