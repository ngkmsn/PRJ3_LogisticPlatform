package com.logistics.user.dto;

import com.logistics.common.model.DriverAvailability;
import com.logistics.user.entity.DriverProfile;
import com.logistics.user.entity.User;

import java.time.Instant;

public class DriverProfileDto {

    private String id;
    private String userId;
    private String username;
    private String email;
    private String userRole;
    private String userStatus;
    private String fullName;
    private String phoneNumber;
    private String licenseNumber;
    private String licenseClass;
    private String vehicleType;
    private String vehiclePlate;
    private String address;
    private String status;
    private DriverAvailability availability;
    private Instant createdAt;
    private Instant updatedAt;

    public DriverProfileDto() {
    }

    public static DriverProfileDto from(DriverProfile profile, User user) {
        DriverProfileDto dto = new DriverProfileDto();
        dto.setId(profile.getId());
        dto.setUserId(profile.getUserId());
        dto.setFullName(profile.getFullName());
        dto.setPhoneNumber(profile.getPhoneNumber());
        dto.setLicenseNumber(profile.getLicenseNumber());
        dto.setLicenseClass(profile.getLicenseClass());
        dto.setVehicleType(profile.getVehicleType());
        dto.setVehiclePlate(profile.getVehiclePlate());
        dto.setAddress(profile.getAddress());
        dto.setStatus(profile.getStatus());
        dto.setAvailability(profile.getAvailability());
        dto.setCreatedAt(profile.getCreatedAt());
        dto.setUpdatedAt(profile.getUpdatedAt());

        if (user != null) {
            dto.setUsername(user.getUsername());
            dto.setEmail(user.getEmail());
            dto.setUserRole(user.getRole() != null ? user.getRole().name() : null);
            dto.setUserStatus(user.getStatus() != null ? user.getStatus().name() : null);
        }

        return dto;
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

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUserRole() {
        return userRole;
    }

    public void setUserRole(String userRole) {
        this.userRole = userRole;
    }

    public String getUserStatus() {
        return userStatus;
    }

    public void setUserStatus(String userStatus) {
        this.userStatus = userStatus;
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
