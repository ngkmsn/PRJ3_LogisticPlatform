package com.logistics.shipment.eligibility.model;

import java.math.BigDecimal;

/**
 * Encapsulates the assignment requirements extracted from an order to evaluate eligible drivers.
 */
public class AssignmentCriteria {

    private String orderId;
    private String orderNumber;
    private String pickupLocation;
    private Double pickupLatitude;
    private Double pickupLongitude;
    private String deliveryAddress;
    private String serviceType;
    private String requiredVehicleType;
    private BigDecimal totalAmount;

    public AssignmentCriteria() {
    }

    public AssignmentCriteria(String orderId, String orderNumber, String pickupLocation,
                              String deliveryAddress, String serviceType,
                              String requiredVehicleType, BigDecimal totalAmount) {
        this(orderId, orderNumber, pickupLocation, null, null, deliveryAddress, serviceType, requiredVehicleType, totalAmount);
    }

    public AssignmentCriteria(String orderId, String orderNumber, String pickupLocation,
                              Double pickupLatitude, Double pickupLongitude,
                              String deliveryAddress, String serviceType,
                              String requiredVehicleType, BigDecimal totalAmount) {
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.pickupLocation = pickupLocation;
        this.pickupLatitude = pickupLatitude;
        this.pickupLongitude = pickupLongitude;
        this.deliveryAddress = deliveryAddress;
        this.serviceType = serviceType;
        this.requiredVehicleType = requiredVehicleType;
        this.totalAmount = totalAmount;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String orderId;
        private String orderNumber;
        private String pickupLocation;
        private Double pickupLatitude;
        private Double pickupLongitude;
        private String deliveryAddress;
        private String serviceType;
        private String requiredVehicleType;
        private BigDecimal totalAmount;

        public Builder orderId(String orderId) {
            this.orderId = orderId;
            return this;
        }

        public Builder orderNumber(String orderNumber) {
            this.orderNumber = orderNumber;
            return this;
        }

        public Builder pickupLocation(String pickupLocation) {
            this.pickupLocation = pickupLocation;
            return this;
        }

        public Builder pickupLatitude(Double pickupLatitude) {
            this.pickupLatitude = pickupLatitude;
            return this;
        }

        public Builder pickupLongitude(Double pickupLongitude) {
            this.pickupLongitude = pickupLongitude;
            return this;
        }

        public Builder pickupCoordinates(Double latitude, Double longitude) {
            this.pickupLatitude = latitude;
            this.pickupLongitude = longitude;
            return this;
        }

        public Builder deliveryAddress(String deliveryAddress) {
            this.deliveryAddress = deliveryAddress;
            return this;
        }

        public Builder serviceType(String serviceType) {
            this.serviceType = serviceType;
            return this;
        }

        public Builder requiredVehicleType(String requiredVehicleType) {
            this.requiredVehicleType = requiredVehicleType;
            return this;
        }

        public Builder totalAmount(BigDecimal totalAmount) {
            this.totalAmount = totalAmount;
            return this;
        }

        public AssignmentCriteria build() {
            return new AssignmentCriteria(
                    orderId, orderNumber, pickupLocation,
                    pickupLatitude, pickupLongitude,
                    deliveryAddress, serviceType, requiredVehicleType, totalAmount
            );
        }
    }

    /**
     * Resolves the pickup geographic location, prioritizing numeric latitude/longitude,
     * or parsing from pickupLocation if formatted as coordinate string.
     */
    public GeoLocation getPickupGeoLocation() {
        if (pickupLatitude != null && pickupLongitude != null) {
            GeoLocation loc = GeoLocation.of(pickupLatitude, pickupLongitude);
            if (loc.isValid()) {
                return loc;
            }
        }
        return GeoLocation.parse(pickupLocation);
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public String getPickupLocation() {
        return pickupLocation;
    }

    public void setPickupLocation(String pickupLocation) {
        this.pickupLocation = pickupLocation;
    }

    public Double getPickupLatitude() {
        return pickupLatitude;
    }

    public void setPickupLatitude(Double pickupLatitude) {
        this.pickupLatitude = pickupLatitude;
    }

    public Double getPickupLongitude() {
        return pickupLongitude;
    }

    public void setPickupLongitude(Double pickupLongitude) {
        this.pickupLongitude = pickupLongitude;
    }

    public String getDeliveryAddress() {
        return deliveryAddress;
    }

    public void setDeliveryAddress(String deliveryAddress) {
        this.deliveryAddress = deliveryAddress;
    }

    public String getServiceType() {
        return serviceType;
    }

    public void setServiceType(String serviceType) {
        this.serviceType = serviceType;
    }

    public String getRequiredVehicleType() {
        return requiredVehicleType;
    }

    public void setRequiredVehicleType(String requiredVehicleType) {
        this.requiredVehicleType = requiredVehicleType;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    @Override
    public String toString() {
        return "AssignmentCriteria{" +
                "orderId='" + orderId + '\'' +
                ", orderNumber='" + orderNumber + '\'' +
                ", pickupLocation='" + pickupLocation + '\'' +
                ", pickupLatitude=" + pickupLatitude +
                ", pickupLongitude=" + pickupLongitude +
                ", deliveryAddress='" + deliveryAddress + '\'' +
                ", serviceType='" + serviceType + '\'' +
                ", requiredVehicleType='" + requiredVehicleType + '\'' +
                '}';
    }
}
