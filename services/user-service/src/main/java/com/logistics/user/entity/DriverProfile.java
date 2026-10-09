package com.logistics.user.entity;

import com.logistics.common.model.DriverAvailability;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "driver_profiles")
public class DriverProfile {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "user_id", length = 36, nullable = false, unique = true)
    private String userId;

    @Column(name = "full_name", length = 100, nullable = false)
    private String fullName;

    @Column(name = "phone_number", length = 20, nullable = false)
    private String phoneNumber;

    @Column(name = "license_number", length = 50, nullable = false, unique = true)
    private String licenseNumber;

    @Column(name = "license_class", length = 20, nullable = false)
    private String licenseClass;

    @Column(name = "vehicle_type", length = 50, nullable = false)
    private String vehicleType;

    @Column(name = "vehicle_plate", length = 30, nullable = false)
    private String vehiclePlate;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "status", length = 30, nullable = false)
    private String status;

    @Enumerated(EnumType.STRING)
    @Column(name = "availability", length = 20, nullable = false)
    private DriverAvailability availability;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public DriverProfile() {
    }

    public DriverProfile(String id, String userId, String fullName, String phoneNumber,
                         String licenseNumber, String licenseClass, String vehicleType,
                         String vehiclePlate, String address, String status) {
        this(id, userId, fullName, phoneNumber, licenseNumber, licenseClass, vehicleType, vehiclePlate, address, status, DriverAvailability.UNAVAILABLE);
    }

    public DriverProfile(String id, String userId, String fullName, String phoneNumber,
                         String licenseNumber, String licenseClass, String vehicleType,
                         String vehiclePlate, String address, String status,
                         DriverAvailability availability) {
        this.id = id;
        this.userId = userId;
        this.fullName = fullName;
        this.phoneNumber = phoneNumber;
        this.licenseNumber = licenseNumber;
        this.licenseClass = licenseClass;
        this.vehicleType = vehicleType;
        this.vehiclePlate = vehiclePlate;
        this.address = address;
        this.status = status;
        this.availability = availability != null ? availability : DriverAvailability.UNAVAILABLE;
    }

    @PrePersist
    protected void onCreate() {
        if (this.id == null || this.id.isBlank()) {
            this.id = UUID.randomUUID().toString();
        }
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.updatedAt == null) {
            this.updatedAt = Instant.now();
        }
        if (this.status == null || this.status.isBlank()) {
            this.status = "ACTIVE";
        }
        if (this.availability == null) {
            this.availability = DriverAvailability.UNAVAILABLE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // --- Getters and Setters ---

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getLicenseNumber() {
        return licenseNumber;
    }

    public void setLicenseNumber(String licenseNumber) {
        this.licenseNumber = licenseNumber;
    }

    public String getLicenseClass() {
        return licenseClass;
    }

    public void setLicenseClass(String licenseClass) {
        this.licenseClass = licenseClass;
    }

    public String getVehicleType() {
        return vehicleType;
    }

    public void setVehicleType(String vehicleType) {
        this.vehicleType = vehicleType;
    }

    public String getVehiclePlate() {
        return vehiclePlate;
    }

    public void setVehiclePlate(String vehiclePlate) {
        this.vehiclePlate = vehiclePlate;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public DriverAvailability getAvailability() {
        return availability;
    }

    public void setAvailability(DriverAvailability availability) {
        this.availability = availability;
    }
}
